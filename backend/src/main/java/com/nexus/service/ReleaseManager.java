package com.nexus.service;

import com.nexus.entity.Event;
import com.nexus.entity.Release;
import com.nexus.entity.Worker;
import com.nexus.enums.Enums.ReleaseStatus;
import com.nexus.enums.Enums.WorkerStatus;
import com.nexus.repository.EventRepository;
import com.nexus.repository.ReleaseRepository;
import com.nexus.repository.WorkerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ReleaseManager {
    private static final Logger log = LoggerFactory.getLogger(ReleaseManager.class);

    private final ReleaseRepository releaseRepository;
    private final WorkerRepository workerRepository;
    private final WorkerManager workerManager;
    private final EventManager eventManager;
    private final EventRepository eventRepository;
    private final ThreadPoolTaskScheduler taskScheduler;

    @Value("${nexus.release.watch-period-ms}")
    private long watchPeriodMs;

    public ReleaseManager(ReleaseRepository releaseRepository,
                          WorkerRepository workerRepository,
                          WorkerManager workerManager,
                          EventManager eventManager,
                          EventRepository eventRepository,
                          ThreadPoolTaskScheduler taskScheduler) {
        this.releaseRepository = releaseRepository;
        this.workerRepository = workerRepository;
        this.workerManager = workerManager;
        this.eventManager = eventManager;
        this.eventRepository = eventRepository;
        this.taskScheduler = taskScheduler;
    }

    @Transactional
    public Release deployRelease(String targetVersion) {
        Optional<Release> activeReleaseOpt = releaseRepository.findActiveRelease();
        if (activeReleaseOpt.isPresent()) {
            throw new IllegalStateException("Another release (version " + activeReleaseOpt.get().getVersion() + ") is already active.");
        }

        // Determine current/previous version
        String previousVersion = "v1";
        Optional<Release> latestRelease = releaseRepository.findLatestRelease();
        if (latestRelease.isPresent()) {
            previousVersion = latestRelease.get().getVersion();
        } else {
            // Get version of first worker in DB
            List<Worker> workers = workerRepository.findAll();
            if (!workers.isEmpty()) {
                previousVersion = workers.get(0).getVersion();
            }
        }

        if (previousVersion.equals(targetVersion)) {
            throw new IllegalArgumentException("Version " + targetVersion + " is already deployed.");
        }

        log.info("Starting deployment of release version {} (Previous: {})", targetVersion, previousVersion);

        // 1. Create Release Record (PREPARING)
        Release release = new Release();
        release.setServiceName("worker");
        release.setVersion(targetVersion);
        release.setPreviousVersion(previousVersion);
        release.setStatus(ReleaseStatus.PREPARING);
        release.setStartedAt(LocalDateTime.now());
        release.setRollbackAvailable(false);
        Release savedRelease = releaseRepository.save(release);

        Map<String, Object> meta = new HashMap<>();
        meta.put("previousVersion", previousVersion);
        meta.put("targetVersion", targetVersion);
        eventManager.logEvent(
                "RELEASE_STARTED",
                "RELEASE",
                savedRelease.getId().toString(),
                "Release deployment started for version " + targetVersion,
                "Preparing rollout of worker nodes",
                meta
        );

        // 2. Deploy to Workers (DEPLOYING)
        savedRelease.setStatus(ReleaseStatus.DEPLOYING);
        releaseRepository.save(savedRelease);

        List<Worker> workers = workerRepository.findAll();
        for (Worker worker : workers) {
            // Stop old process
            workerManager.stopWorkerProcess(worker.getId());

            // Upgrade version and reset health metrics
            worker.setVersion(targetVersion);
            worker.setStatus(WorkerStatus.STARTING);
            worker.setRestartCount(0); // Reset restart budget on new release
            workerRepository.save(worker);

            // Spawn upgraded process
            workerManager.startWorkerProcess(worker);
        }

        eventManager.logEvent(
                "RELEASE_DEPLOYED",
                "RELEASE",
                savedRelease.getId().toString(),
                "All workers updated to version " + targetVersion,
                "Transitioning release to WATCHING period",
                meta
        );

        // 3. Start Watch Period (WATCHING)
        savedRelease.setStatus(ReleaseStatus.WATCHING);
        releaseRepository.save(savedRelease);

        // Schedule release evaluation
        taskScheduler.schedule(() -> evaluateRelease(savedRelease.getId()), Instant.now().plusMillis(watchPeriodMs));

        return savedRelease;
    }

    public void evaluateRelease(Long releaseId) {
        log.info("Evaluating release ID={} after watch period...", releaseId);
        // Find release in DB
        Release release = releaseRepository.findById(releaseId).orElse(null);
        if (release == null || release.getStatus() != ReleaseStatus.WATCHING) {
            return;
        }

        // Check for any WORKER_CRASHED or WORK_FAILED events during the watch period
        List<Event> relatedEvents = eventRepository.findByReleaseIdOrderByTimestampDesc(releaseId);
        boolean healthy = true;
        int failureCount = 0;
        StringBuilder reason = new StringBuilder();

        for (Event event : relatedEvents) {
            if ("WORKER_CRASHED".equals(event.getEventType()) || "WORK_FAILED".equals(event.getEventType())) {
                healthy = false;
                failureCount++;
                if (failureCount <= 3) {
                    if (reason.length() > 0) reason.append("; ");
                    reason.append(event.getMessage());
                }
            }
        }

        release.setCompletedAt(LocalDateTime.now());
        Map<String, Object> meta = new HashMap<>();
        meta.put("failuresObserved", failureCount);
        meta.put("watchPeriodMs", watchPeriodMs);

        if (healthy) {
            release.setStatus(ReleaseStatus.SUCCESS);
            release.setRollbackAvailable(true); // Can rollback any deployed release later
            releaseRepository.save(release);

            eventManager.logEvent(
                    "RELEASE_SUCCESS",
                    "RELEASE",
                    releaseId.toString(),
                    "Release v" + release.getVersion() + " verified successfully",
                    "No worker failures or job crashes observed during watch period (" + (watchPeriodMs / 1000) + "s)",
                    meta
            );
        } else {
            release.setStatus(ReleaseStatus.FAILED);
            release.setRollbackAvailable(true); // Rollback is definitely available now!
            release.setReason("Failing health check rules: " + failureCount + " failures detected: " + reason.toString());
            releaseRepository.save(release);

            eventManager.logEvent(
                    "RELEASE_FAILED",
                    "RELEASE",
                    releaseId.toString(),
                    "Release v" + release.getVersion() + " marked UNHEALTHY",
                    "Failed due to worker crash or job failures during watch period. Rollback recommended.",
                    meta
            );
        }
    }

    @Transactional
    public Release rollbackRelease(Long releaseId) {
        Release release = releaseRepository.findById(releaseId).orElse(null);
        if (release == null) {
            throw new IllegalArgumentException("Release not found: " + releaseId);
        }

        if (!release.isRollbackAvailable()) {
            throw new IllegalStateException("Rollback not available for this release.");
        }

        String targetVersion = release.getPreviousVersion();
        String currentVersion = release.getVersion();
        log.info("Operator triggered rollback from version {} to previous version {}", currentVersion, targetVersion);

        Map<String, Object> meta = new HashMap<>();
        meta.put("fromVersion", currentVersion);
        meta.put("toVersion", targetVersion);
        meta.put("releaseId", releaseId);

        eventManager.logEvent(
                "ROLLBACK_STARTED",
                "RELEASE",
                releaseId.toString(),
                "Rollback initiated from " + currentVersion + " to " + targetVersion,
                "Restoring worker processes to previous known-good version",
                meta
        );

        try {
            List<Worker> workers = workerRepository.findAll();
            for (Worker worker : workers) {
                // Stop current process
                workerManager.stopWorkerProcess(worker.getId());

                // Revert version and reset metrics
                worker.setVersion(targetVersion);
                worker.setStatus(WorkerStatus.STARTING);
                worker.setRestartCount(0);
                workerRepository.save(worker);

                // Start reverted process
                workerManager.startWorkerProcess(worker);
            }

            release.setStatus(ReleaseStatus.ROLLED_BACK);
            release.setRollbackAvailable(false);
            release.setCompletedAt(LocalDateTime.now());
            releaseRepository.save(release);

            eventManager.logEvent(
                    "ROLLBACK_SUCCESS",
                    "RELEASE",
                    releaseId.toString(),
                    "Rollback completed successfully. Restored version " + targetVersion,
                    "All worker nodes reverted and healthy checks initialized",
                    meta
            );

        } catch (Exception e) {
            log.error("Failed to perform rollback for release ID=" + releaseId, e);
            eventManager.logEvent(
                    "ROLLBACK_FAILED",
                    "RELEASE",
                    releaseId.toString(),
                    "Rollback operation failed",
                    e.getMessage(),
                    meta
            );
            throw new RuntimeException("Rollback failed: " + e.getMessage());
        }

        return release;
    }
}
