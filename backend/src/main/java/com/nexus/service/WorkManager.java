package com.nexus.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.dto.WorkRequest;
import com.nexus.dto.WorkResponse;
import com.nexus.entity.Work;
import com.nexus.entity.WorkAttempt;
import com.nexus.entity.Worker;
import com.nexus.enums.Enums.WorkAttemptStatus;
import com.nexus.enums.Enums.WorkStatus;
import com.nexus.enums.Enums.WorkerStatus;
import com.nexus.repository.WorkAttemptRepository;
import com.nexus.repository.WorkRepository;
import com.nexus.repository.WorkerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class WorkManager {
    private static final Logger log = LoggerFactory.getLogger(WorkManager.class);

    private final WorkRepository workRepository;
    private final WorkAttemptRepository workAttemptRepository;
    private final WorkerRepository workerRepository;
    private final WorkerManager workerManager;
    private final EventManager eventManager;
    private final ThreadPoolTaskScheduler taskScheduler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AtomicInteger workerIndex = new AtomicInteger(0);

    @Value("${nexus.retry.max-attempts}")
    private int defaultMaxAttempts;

    @Value("${nexus.retry.base-delay-ms}")
    private long baseDelayMs;

    public WorkManager(WorkRepository workRepository,
                       WorkAttemptRepository workAttemptRepository,
                       WorkerRepository workerRepository,
                       WorkerManager workerManager,
                       EventManager eventManager,
                       ThreadPoolTaskScheduler taskScheduler) {
        this.workRepository = workRepository;
        this.workAttemptRepository = workAttemptRepository;
        this.workerRepository = workerRepository;
        this.workerManager = workerManager;
        this.eventManager = eventManager;
        this.taskScheduler = taskScheduler;
    }

    @Transactional
    public WorkResponse acceptWork(WorkRequest request) {
        Optional<Work> existing = workRepository.findById(request.getId());
        if (existing.isPresent()) {
            Work existingWork = existing.get();
            log.info("Duplicate work request received for job ID={}. Status: {}", request.getId(), existingWork.getStatus());
            return new WorkResponse(existingWork.getId(), "ACCEPTED");
        }

        Work work = new Work();
        work.setId(request.getId());
        work.setType(request.getType());
        try {
            work.setPayload(objectMapper.writeValueAsString(request.getPayload()));
        } catch (Exception e) {
            work.setPayload("{}");
        }
        work.setStatus(WorkStatus.PENDING);
        work.setAttempts(0);
        work.setMaxAttempts(request.getMaxAttempts() != null ? request.getMaxAttempts() : defaultMaxAttempts);
        work.setNextAttemptAt(LocalDateTime.now());
        workRepository.save(work);

        Map<String, Object> meta = new HashMap<>();
        meta.put("type", request.getType());
        meta.put("maxAttempts", work.getMaxAttempts());

        eventManager.logEvent(
                "WORK_ACCEPTED",
                "WORK",
                work.getId(),
                "Job accepted and persisted",
                "Initial state set to PENDING. Persisted before API acknowledgment.",
                meta
        );

        return new WorkResponse(work.getId(), "ACCEPTED");
    }

    @Scheduled(fixedDelay = 1000)
    public void schedulePendingWork() {
        LocalDateTime now = LocalDateTime.now();
        List<Work> pendingJobs = workRepository.findPendingJobsToProcess(now);
        if (pendingJobs.isEmpty()) {
            return;
        }

        List<Worker> healthyWorkers = workerRepository.findByStatus(WorkerStatus.RUNNING);
        if (healthyWorkers.isEmpty()) {
            log.warn("No healthy worker nodes (RUNNING) available to process {} pending jobs.", pendingJobs.size());
            return;
        }

        for (Work job : pendingJobs) {
            // Select worker in round-robin fashion
            int index = workerIndex.getAndIncrement() % healthyWorkers.size();
            Worker selectedWorker = healthyWorkers.get(index);

            // Submit execution asynchronously to the task scheduler pool to avoid blocking the main scheduler thread
            taskScheduler.submit(() -> executeWorkItem(job.getId(), selectedWorker.getId()));
        }
    }

    private void executeWorkItem(String workId, String workerId) {
        // Step 1: Claim job (Atomic transition PENDING -> PROCESSING)
        WorkAttempt attempt = claimWork(workId, workerId);
        if (attempt == null) {
            // Already claimed or not in PENDING state
            return;
        }

        long startTime = System.currentTimeMillis();
        boolean success = false;
        String lastError = null;

        try {
            // Fetch the job object for processing payload
            Work work = workRepository.findById(workId).orElseThrow();
            success = workerManager.sendWorkToWorker(workerId, work);
            if (!success) {
                lastError = "Worker failed to execute task or returned an error";
            }
        } catch (Exception e) {
            lastError = "Execution exception: " + e.getMessage();
        }

        long duration = System.currentTimeMillis() - startTime;

        // Step 2: Finalize attempt and work state transitions
        finalizeWorkAttempt(workId, attempt.getId(), success, lastError, duration);
    }

    @Transactional
    public WorkAttempt claimWork(String workId, String workerId) {
        Work work = workRepository.findById(workId).orElse(null);
        if (work == null || work.getStatus() != WorkStatus.PENDING) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();

        // Lock/Claim job
        work.setStatus(WorkStatus.PROCESSING);
        work.setAssignedWorkerId(workerId);
        work.setAttempts(work.getAttempts() + 1);
        workRepository.save(work);

        // Record Attempt (Requirement 7)
        WorkAttempt attempt = new WorkAttempt();
        attempt.setWorkId(workId);
        attempt.setAttemptNumber(work.getAttempts());
        attempt.setWorkerId(workerId);
        attempt.setStartedAt(now);
        attempt.setStatus(WorkAttemptStatus.STARTED);
        WorkAttempt savedAttempt = workAttemptRepository.save(attempt);

        Map<String, Object> meta = new HashMap<>();
        meta.put("workerId", workerId);
        meta.put("attemptNumber", work.getAttempts());

        eventManager.logEvent(
                "WORK_ASSIGNED",
                "WORK",
                workId,
                "Job assigned to worker node",
                "Assigned to " + workerId + " (Attempt " + work.getAttempts() + "/" + work.getMaxAttempts() + ")",
                meta
        );

        return savedAttempt;
    }

    @Transactional
    public void finalizeWorkAttempt(String workId, Long attemptId, boolean success, String errorReason, long durationMs) {
        Work work = workRepository.findById(workId).orElse(null);
        WorkAttempt attempt = workAttemptRepository.findById(attemptId).orElse(null);

        if (work == null || attempt == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        attempt.setCompletedAt(now);
        attempt.setDurationMs(durationMs);

        Map<String, Object> meta = new HashMap<>();
        meta.put("workerId", attempt.getWorkerId());
        meta.put("attemptNumber", work.getAttempts());
        meta.put("durationMs", durationMs);

        if (success) {
            attempt.setStatus(WorkAttemptStatus.SUCCESS);
            workAttemptRepository.save(attempt);

            work.setStatus(WorkStatus.SUCCESS);
            work.setCompletedAt(now);
            work.setLastError(null);
            workRepository.save(work);

            eventManager.logEvent(
                    "WORK_SUCCESS",
                    "WORK",
                    workId,
                    "Job execution succeeded",
                    "Completed by " + attempt.getWorkerId() + " in " + durationMs + "ms",
                    meta
            );
        } else {
            attempt.setStatus(WorkAttemptStatus.FAILED);
            attempt.setError(errorReason);
            workAttemptRepository.save(attempt);

            work.setLastError(errorReason);
            meta.put("error", errorReason);

            eventManager.logEvent(
                    "WORK_FAILED",
                    "WORK",
                    workId,
                    "Job attempt failed",
                    errorReason,
                    meta
            );

            if (work.getAttempts() < work.getMaxAttempts()) {
                // Schedule retry with exponential backoff (Requirement 13)
                long delay = baseDelayMs * (long) Math.pow(2, work.getAttempts() - 1);
                work.setStatus(WorkStatus.PENDING);
                work.setNextAttemptAt(now.plusNanos(delay * 1000000L));
                workRepository.save(work);

                Map<String, Object> retryMeta = new HashMap<>();
                retryMeta.put("attemptNumber", work.getAttempts());
                retryMeta.put("nextAttemptDelayMs", delay);

                eventManager.logEvent(
                        "WORK_RETRY_SCHEDULED",
                        "WORK",
                        workId,
                        "Job retry scheduled",
                        "Rescheduled in " + delay + "ms (Backoff)",
                        retryMeta
                );
            } else {
                // Retries exhausted
                work.setStatus(WorkStatus.FAILED);
                work.setCompletedAt(now);
                workRepository.save(work);

                eventManager.logEvent(
                        "WORK_FAILED_PERMANENTLY",
                        "WORK",
                        workId,
                        "Job failed permanently",
                        "Max retry attempts exhausted (" + work.getMaxAttempts() + "/" + work.getMaxAttempts() + ")",
                        meta
                );
            }
        }
    }

    @Transactional
    public void manualRetry(String workId) {
        Work work = workRepository.findById(workId).orElse(null);
        if (work == null) {
            throw new IllegalArgumentException("Work not found: " + workId);
        }
        if (work.getStatus() != WorkStatus.FAILED) {
            throw new IllegalStateException("Only failed jobs can be retried manually.");
        }

        log.info("Operator triggered manual retry for work job: {}", workId);
        work.setStatus(WorkStatus.PENDING);
        work.setAttempts(0); // Reset attempt count
        work.setNextAttemptAt(LocalDateTime.now());
        workRepository.save(work);

        eventManager.logEvent(
                "WORK_RETRY_EXECUTED",
                "WORK",
                workId,
                "Manual job retry triggered by operator",
                "Status reset to PENDING, attempt count reset to 0",
                null
        );
    }
}
