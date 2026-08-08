package com.nexus.service;

import com.nexus.entity.Worker;
import com.nexus.enums.Enums.WorkerFailureMode;
import com.nexus.enums.Enums.WorkerStatus;
import com.nexus.entity.Work;
import com.nexus.repository.WorkerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WorkerManager {
    private static final Logger log = LoggerFactory.getLogger(WorkerManager.class);

    private final WorkerRepository workerRepository;
    private final EventManager eventManager;
    private final ThreadPoolTaskScheduler taskScheduler;
    private final HttpClient httpClient;

    private final Map<String, Process> processes = new ConcurrentHashMap<>();
    private volatile boolean workersInitialized = false;

    @Value("${nexus.worker.max-restarts}")
    private int maxRestarts;

    @Value("${nexus.worker.restart-base-delay-ms}")
    private long restartBaseDelayMs;

    @Value("${nexus.worker.healthy-settling-period-ms}")
    private long healthySettlingPeriodMs;

    public WorkerManager(WorkerRepository workerRepository, EventManager eventManager, ThreadPoolTaskScheduler taskScheduler) {
        this.workerRepository = workerRepository;
        this.eventManager = eventManager;
        this.taskScheduler = taskScheduler;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(1000))
                .build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationStart() {
        initWorkers();
    }

    @Transactional
    public void initWorkers() {
        log.info("Initializing NEXUS worker nodes...");
        createAndStartWorker("worker-1", "Worker Node 1", 8081);
        createAndStartWorker("worker-2", "Worker Node 2", 8082);
        createAndStartWorker("worker-3", "Worker Node 3", 8083);
        workersInitialized = true;
    }

    private void createAndStartWorker(String id, String name, int port) {
        Worker worker = workerRepository.findById(id).orElse(null);
        if (worker == null) {
            worker = new Worker();
            worker.setId(id);
            worker.setName(name);
            worker.setStatus(WorkerStatus.STOPPED);
            worker.setVersion("v1");
            worker.setRestartCount(0);
            worker.setMaxRestartCount(maxRestarts);
            worker.setFailureMode(WorkerFailureMode.NORMAL);
            worker.setPort(port);
            workerRepository.save(worker);
        }

        // Clean up any stale process if already running (e.g. on hot reload)
        stopWorkerProcess(id);

        // Start worker
        startWorkerProcess(worker);
    }

    public synchronized void startWorkerProcess(Worker worker) {
        String id = worker.getId();
        log.info("Spawning worker subprocess for ID={} Port={} Version={} Mode={}", 
                id, worker.getPort(), worker.getVersion(), worker.getFailureMode());

        try {
            // Find worker code file path
            String workerFilePath = "worker/Worker.java";
            File checkFile = new File(workerFilePath);
            if (!checkFile.exists()) {
                // Try parent path if running from backend module
                workerFilePath = "../worker/Worker.java";
                checkFile = new File(workerFilePath);
            }

            if (!checkFile.exists()) {
                log.error("Could not find Worker.java source file at path 'worker/Worker.java' or '../worker/Worker.java'");
                throw new RuntimeException("Worker.java file missing");
            }

            // Command: java [file] --id=X --port=Y --version=Z --mode=M
            ProcessBuilder pb = new ProcessBuilder(
                    "java",
                    workerFilePath,
                    "--id=" + id,
                    "--port=" + worker.getPort(),
                    "--version=" + worker.getVersion(),
                    "--mode=" + worker.getFailureMode().toString()
            );

            // Redirect logs to separate files for readability
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.to(new File("worker-" + id + ".log")));

            Process process = pb.start();
            processes.put(id, process);

            worker.setLastStartedAt(LocalDateTime.now());
            // If the status is not RESTARTING or FAILED, it is STARTING
            if (worker.getStatus() != WorkerStatus.RESTARTING) {
                worker.setStatus(WorkerStatus.STARTING);
            }
            workerRepository.save(worker);

            Map<String, Object> meta = new HashMap<>();
            meta.put("port", worker.getPort());
            meta.put("version", worker.getVersion());
            meta.put("failureMode", worker.getFailureMode().toString());
            eventManager.logEvent(
                    "WORKER_STARTED",
                    "WORKER",
                    id,
                    "Worker process spawned",
                    "Startup initialized on port " + worker.getPort(),
                    meta
            );

        } catch (Exception e) {
            log.error("Failed to start worker subprocess for ID=" + id, e);
            worker.setStatus(WorkerStatus.FAILED);
            worker.setLastError("Subprocess spawn error: " + e.getMessage());
            worker.setLastFailureAt(LocalDateTime.now());
            workerRepository.save(worker);

            eventManager.logEvent(
                    "WORKER_CRASHED",
                    "WORKER",
                    id,
                    "Failed to spawn worker process",
                    e.getMessage(),
                    null
            );
        }
    }

    public synchronized void stopWorkerProcess(String id) {
        Process p = processes.remove(id);
        if (p != null && p.isAlive()) {
            log.info("Stopping worker subprocess for ID={}", id);
            p.destroy();
            try {
                // Wait briefly for process to exit
                p.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (p.isAlive()) {
                    p.destroyForcibly();
                }
            } catch (Exception e) {
                log.error("Error waiting for worker process to terminate: " + id, e);
            }
        }
    }

    @Scheduled(fixedDelay = 2000)
    public void checkAllWorkersHealth() {
        if (!workersInitialized) {
            return;
        }
        List<Worker> workers = workerRepository.findAll();
        for (Worker worker : workers) {
            if (worker.getStatus() == WorkerStatus.OUT_OF_SERVICE || worker.getStatus() == WorkerStatus.STOPPED) {
                // Do not health check or restart out-of-service/stopped workers
                continue;
            }

            String id = worker.getId();
            Process process = processes.get(id);

            boolean processAlive = (process != null && process.isAlive());
            boolean healthCheckOk = false;
            String errorMsg = null;
            String remoteVersion = null;

            if (processAlive) {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + worker.getPort() + "/health"))
                            .GET()
                            .timeout(Duration.ofMillis(800))
                            .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        healthCheckOk = true;
                        // Extract version from response body e.g. {"status":"UP", ... "version":"v1"}
                        String body = response.body();
                        int verIndex = body.indexOf("\"version\":\"");
                        if (verIndex != -1) {
                            int start = verIndex + 11;
                            int end = body.indexOf("\"", start);
                            if (end != -1) {
                                remoteVersion = body.substring(start, end);
                            }
                        }
                    } else {
                        errorMsg = "HTTP Status Code " + response.statusCode();
                    }
                } catch (Exception e) {
                    errorMsg = "HTTP ping failure: " + e.getMessage();
                }
            } else {
                errorMsg = "Process died (exit code = " + (process != null ? process.exitValue() : "unknown") + ")";
                processes.remove(id); // Clean from active map
            }

            if (healthCheckOk) {
                // If it is now healthy
                LocalDateTime now = LocalDateTime.now();
                worker.setLastHealthyAt(now);

                if (worker.getStatus() != WorkerStatus.RUNNING) {
                    worker.setStatus(WorkerStatus.RUNNING);
                    if (remoteVersion != null) {
                        worker.setVersion(remoteVersion);
                    }
                    workerRepository.save(worker);

                    eventManager.logEvent(
                            "WORKER_RECOVERED",
                            "WORKER",
                            id,
                            "Worker health check succeeded",
                            "Worker is healthy and accepting jobs",
                            null
                    );
                }

                // Handle Settling Period (Requirement 16)
                // If the worker has been healthy for healthySettlingPeriodMs since started, reset its restart budget
                if (worker.getRestartCount() > 0 && worker.getLastStartedAt() != null) {
                    Duration duration = Duration.between(worker.getLastStartedAt(), now);
                    if (duration.toMillis() >= healthySettlingPeriodMs) {
                        int prevCount = worker.getRestartCount();
                        worker.setRestartCount(0);
                        workerRepository.save(worker);

                        Map<String, Object> meta = new HashMap<>();
                        meta.put("previousRestartCount", prevCount);
                        eventManager.logEvent(
                                "WORKER_RECOVERED",
                                "WORKER",
                                id,
                                "Worker restart budget reset",
                                "Worker remained healthy for settling period of " + (healthySettlingPeriodMs / 1000) + "s",
                                meta
                        );
                    }
                }
            } else {
                // Health check failed!
                handleWorkerFailure(worker, errorMsg);
            }
        }
    }

    private void handleWorkerFailure(Worker worker, String errorMsg) {
        String id = worker.getId();
        if (worker.getStatus() == WorkerStatus.FAILED || worker.getStatus() == WorkerStatus.RESTARTING) {
            // Already processing failure, check if scheduled restart is pending or just wait
            return;
        }

        log.warn("Worker failure detected for ID={}. Reason: {}", id, errorMsg);

        // Ensure process is dead
        stopWorkerProcess(id);

        LocalDateTime now = LocalDateTime.now();
        worker.setLastFailureAt(now);
        worker.setLastError(errorMsg);
        worker.setStatus(WorkerStatus.FAILED);
        worker.setRestartCount(worker.getRestartCount() + 1);
        workerRepository.save(worker);

        Map<String, Object> meta = new HashMap<>();
        meta.put("error", errorMsg);
        meta.put("restartCount", worker.getRestartCount());
        meta.put("maxRestartCount", worker.getMaxRestartCount());

        eventManager.logEvent(
                "WORKER_CRASHED",
                "WORKER",
                id,
                "Worker node crashed or became unreachable",
                errorMsg,
                meta
        );

        if (worker.getRestartCount() > worker.getMaxRestartCount()) {
            // Bounded recovery limit reached (Requirement 17)
            worker.setStatus(WorkerStatus.OUT_OF_SERVICE);
            workerRepository.save(worker);

            eventManager.logEvent(
                    "WORKER_OUT_OF_SERVICE",
                    "WORKER",
                    id,
                    "Worker marked OUT_OF_SERVICE",
                    "Restart budget exhausted (tried " + worker.getMaxRestartCount() + " times)",
                    meta
            );
        } else {
            // Schedule restart with exponential backoff (Requirement 17)
            long backoffDelay = restartBaseDelayMs * (long) Math.pow(2, worker.getRestartCount() - 1);
            worker.setStatus(WorkerStatus.RESTARTING);
            workerRepository.save(worker);

            eventManager.logEvent(
                    "WORKER_RESTART_REQUESTED",
                    "WORKER",
                    id,
                    "Scheduling worker recovery restart",
                    "Attempt " + worker.getRestartCount() + "/" + worker.getMaxRestartCount() + " in " + backoffDelay + "ms",
                    meta
            );

            taskScheduler.schedule(() -> {
                // Reload worker from database to verify status has not been overridden by manual operations
                Worker current = workerRepository.findById(id).orElse(null);
                if (current != null && current.getStatus() == WorkerStatus.RESTARTING) {
                    startWorkerProcess(current);
                }
            }, Instant.now().plusMillis(backoffDelay));
        }
    }

    @Transactional
    public void recoverWorker(String id) {
        Worker worker = workerRepository.findById(id).orElse(null);
        if (worker == null) {
            throw new IllegalArgumentException("Worker not found: " + id);
        }

        log.info("Operator triggered manual recovery for worker: {}", id);

        // Reset budget
        worker.setRestartCount(0);
        worker.setStatus(WorkerStatus.STARTING);
        workerRepository.save(worker);

        eventManager.logEvent(
                "WORKER_RESTARTED",
                "WORKER",
                id,
                "Manual worker recovery triggered by operator",
                "Restart budget reset to 0",
                null
        );

        stopWorkerProcess(id);
        startWorkerProcess(worker);
    }

    @Transactional
    public void setFailureMode(String id, WorkerFailureMode mode) {
        Worker worker = workerRepository.findById(id).orElse(null);
        if (worker == null) {
            throw new IllegalArgumentException("Worker not found: " + id);
        }

        log.info("Setting failure mode for worker {} to {}", id, mode);
        worker.setFailureMode(mode);
        workerRepository.save(worker);

        // Try to propagate Failure Mode to running worker HTTP process
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + worker.getPort() + "/failure-mode"))
                    .POST(HttpRequest.BodyPublishers.ofString("{\"mode\":\"" + mode.toString() + "\"}"))
                    .timeout(Duration.ofMillis(800))
                    .build();

            // Fire and forget or simple timeout-bounded wait
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            log.warn("Could not propagate failure mode to worker HTTP endpoint (worker might be down): " + e.getMessage());
        }

        Map<String, Object> meta = new HashMap<>();
        meta.put("newMode", mode.toString());
        eventManager.logEvent(
                "WORKER_FAILURE_MODE_CHANGED",
                "WORKER",
                id,
                "Worker failure mode updated by operator",
                "Failure mode changed to " + mode,
                meta
        );
    }

    public boolean sendWorkToWorker(String workerId, Work work) {
        Worker worker = workerRepository.findById(workerId).orElse(null);
        if (worker == null || worker.getStatus() != WorkerStatus.RUNNING) {
            return false;
        }

        try {
            String jsonPayload = String.format("{\"id\":\"%s\",\"type\":\"%s\",\"payload\":%s}",
                    work.getId(), work.getType(), work.getPayload() != null ? work.getPayload() : "{}");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + worker.getPort() + "/work"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .timeout(Duration.ofMillis(12000)) // give enough headroom for SLOW mode (10s)
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String body = response.body();
                return body.contains("SUCCESS") || body.contains("ALREADY_PROCESSED");
            }
        } catch (Exception e) {
            log.error("HTTP error sending work to worker " + workerId + ": " + e.getMessage());
        }
        return false;
    }
}
