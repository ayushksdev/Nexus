package com.nexus.controller;

import com.nexus.entity.Event;
import com.nexus.entity.Release;
import com.nexus.entity.Work;
import com.nexus.entity.Worker;
import com.nexus.enums.Enums.ReleaseStatus;
import com.nexus.enums.Enums.WorkStatus;
import com.nexus.enums.Enums.WorkerStatus;
import com.nexus.repository.EventRepository;
import com.nexus.repository.ReleaseRepository;
import com.nexus.repository.WorkRepository;
import com.nexus.repository.WorkerRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final WorkRepository workRepository;
    private final WorkerRepository workerRepository;
    private final ReleaseRepository releaseRepository;
    private final EventRepository eventRepository;

    public DashboardController(WorkRepository workRepository,
                               WorkerRepository workerRepository,
                               ReleaseRepository releaseRepository,
                               EventRepository eventRepository) {
        this.workRepository = workRepository;
        this.workerRepository = workerRepository;
        this.releaseRepository = releaseRepository;
        this.eventRepository = eventRepository;
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        List<Work> allWork = workRepository.findAll();
        List<Worker> allWorkers = workerRepository.findAll();
        Optional<Release> latestRelease = releaseRepository.findLatestRelease();

        long pending = allWork.stream().filter(w -> w.getStatus() == WorkStatus.PENDING).count();
        long processing = allWork.stream().filter(w -> w.getStatus() == WorkStatus.PROCESSING).count();
        long success = allWork.stream().filter(w -> w.getStatus() == WorkStatus.SUCCESS).count();
        long failed = allWork.stream().filter(w -> w.getStatus() == WorkStatus.FAILED).count();

        long healthyWorkers = allWorkers.stream().filter(w -> w.getStatus() == WorkerStatus.RUNNING).count();
        long failedWorkers = allWorkers.stream().filter(w -> 
                w.getStatus() == WorkerStatus.FAILED || 
                w.getStatus() == WorkerStatus.RESTARTING || 
                w.getStatus() == WorkerStatus.OUT_OF_SERVICE).count();

        // Calculate oldest pending work age
        LocalDateTime oldestPendingTime = workRepository.getOldestPendingCreatedAt();
        long oldestPendingAgeSeconds = 0;
        if (oldestPendingTime != null) {
            oldestPendingAgeSeconds = Duration.between(oldestPendingTime, LocalDateTime.now()).toSeconds();
        }

        // Active incidents count
        List<Map<String, Object>> incidents = computeActiveIncidents(allWorkers, latestRelease, allWork);
        long activeIncidents = incidents.size();

        // Determine global system status
        String systemStatus = "HEALTHY";
        if (allWorkers.stream().anyMatch(w -> w.getStatus() == WorkerStatus.OUT_OF_SERVICE)) {
            systemStatus = "FAILED"; // Red alert: worker fully disabled
        } else if (failedWorkers > 0 || latestRelease.map(r -> r.getStatus() == ReleaseStatus.FAILED).orElse(false) || failed > 0) {
            systemStatus = "DEGRADED"; // Orange alert: some degradation
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("systemStatus", systemStatus);
        summary.put("pendingWork", pending);
        summary.put("processingWork", processing);
        summary.put("successfulWork", success);
        summary.put("failedWork", failed);
        summary.put("activeIncidents", activeIncidents);
        summary.put("oldestPendingWorkAgeSeconds", oldestPendingAgeSeconds);

        Map<String, Object> workersMap = new HashMap<>();
        workersMap.put("total", allWorkers.size());
        workersMap.put("healthy", healthyWorkers);
        workersMap.put("failed", failedWorkers);
        summary.put("workers", workersMap);

        summary.put("currentRelease", latestRelease.map(Release::getVersion).orElse("v1"));
        summary.put("previousRelease", latestRelease.map(Release::getPreviousVersion).orElse("v0"));
        summary.put("activeReleaseStatus", latestRelease.map(r -> r.getStatus().toString()).orElse("NONE"));

        return ResponseEntity.ok(summary);
    }

    @GetMapping("/timeline")
    public ResponseEntity<List<Event>> getTimeline() {
        // Limit to latest 50 events for performance
        List<Event> events = eventRepository.findAllByOrderByTimestampDesc();
        if (events.size() > 50) {
            return ResponseEntity.ok(events.subList(0, 50));
        }
        return ResponseEntity.ok(events);
    }

    @GetMapping("/incidents")
    public ResponseEntity<List<Map<String, Object>>> getIncidents() {
        List<Worker> allWorkers = workerRepository.findAll();
        Optional<Release> latestRelease = releaseRepository.findLatestRelease();
        List<Work> allWork = workRepository.findAll();

        return ResponseEntity.ok(computeActiveIncidents(allWorkers, latestRelease, allWork));
    }

    private List<Map<String, Object>> computeActiveIncidents(List<Worker> workers, Optional<Release> latestRelease, List<Work> allWork) {
        List<Map<String, Object>> incidents = new ArrayList<>();

        // 1. Worker OUT_OF_SERVICE (High Severity)
        for (Worker w : workers) {
            if (w.getStatus() == WorkerStatus.OUT_OF_SERVICE) {
                Map<String, Object> incident = new HashMap<>();
                incident.put("type", "WORKER_OUT_OF_SERVICE");
                incident.put("subjectId", w.getId());
                incident.put("message", "Worker " + w.getName() + " is disabled (OUT_OF_SERVICE) after repeated crashes.");
                incident.put("reason", "Restart budget exhausted (" + w.getRestartCount() + "/" + w.getMaxRestartCount() + ")");
                incident.put("severity", "HIGH");
                incident.put("timestamp", w.getLastFailureAt());
                incidents.add(incident);
            } else if (w.getStatus() == WorkerStatus.FAILED || w.getStatus() == WorkerStatus.RESTARTING) {
                Map<String, Object> incident = new HashMap<>();
                incident.put("type", "WORKER_DEGRADED");
                incident.put("subjectId", w.getId());
                incident.put("message", "Worker " + w.getName() + " is currently crashing and restarting.");
                incident.put("reason", w.getLastError());
                incident.put("severity", "MEDIUM");
                incident.put("timestamp", w.getLastFailureAt());
                incidents.add(incident);
            }
        }

        // 2. Release Failure (High Severity)
        latestRelease.ifPresent(r -> {
            if (r.getStatus() == ReleaseStatus.FAILED) {
                Map<String, Object> incident = new HashMap<>();
                incident.put("type", "RELEASE_FAILED");
                incident.put("subjectId", r.getId().toString());
                incident.put("message", "Active release version v" + r.getVersion() + " failed monitoring validation.");
                incident.put("reason", r.getReason());
                incident.put("severity", "HIGH");
                incident.put("timestamp", r.getCompletedAt() != null ? r.getCompletedAt() : r.getStartedAt());
                incidents.add(incident);
            }
        });

        // 3. Jobs failed permanently (Low Severity)
        for (Work w : allWork) {
            if (w.getStatus() == WorkStatus.FAILED) {
                Map<String, Object> incident = new HashMap<>();
                incident.put("type", "WORK_FAILED");
                incident.put("subjectId", w.getId());
                incident.put("message", "Job " + w.getId() + " (" + w.getType() + ") failed permanently.");
                incident.put("reason", w.getLastError() != null ? w.getLastError() : "Max retries exceeded");
                incident.put("severity", "LOW");
                incident.put("timestamp", w.getCompletedAt() != null ? w.getCompletedAt() : w.getUpdatedAt());
                incidents.add(incident);
            }
        }

        // Sort incidents by timestamp desc
        incidents.sort((a, b) -> {
            LocalDateTime ta = (LocalDateTime) a.get("timestamp");
            LocalDateTime tb = (LocalDateTime) b.get("timestamp");
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.compareTo(ta);
        });

        return incidents;
    }
}
