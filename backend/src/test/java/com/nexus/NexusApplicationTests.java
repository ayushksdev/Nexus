package com.nexus;

import com.nexus.dto.WorkRequest;
import com.nexus.dto.WorkResponse;
import com.nexus.entity.Work;
import com.nexus.entity.Worker;
import com.nexus.enums.Enums.WorkStatus;
import com.nexus.enums.Enums.WorkerFailureMode;
import com.nexus.enums.Enums.WorkerStatus;
import com.nexus.repository.WorkAttemptRepository;
import com.nexus.repository.WorkRepository;
import com.nexus.repository.WorkerRepository;
import com.nexus.service.ReleaseManager;
import com.nexus.service.WorkManager;
import com.nexus.service.WorkerManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class NexusApplicationTests {

    @Autowired
    private WorkRepository workRepository;

    @Autowired
    private WorkAttemptRepository workAttemptRepository;

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private WorkManager workManager;

    @Autowired
    private WorkerManager workerManager;

    @Autowired
    private ReleaseManager releaseManager;

    @BeforeEach
    public void setup() {
        // Clear databases before each test to maintain determinism
        workAttemptRepository.deleteAll();
        workRepository.deleteAll();
        workerRepository.deleteAll();

        // Initialize workers for tests
        workerManager.initWorkers();
    }

    @Test
    @Transactional
    public void testAcceptingWorkPersistsIt() {
        WorkRequest req = new WorkRequest();
        req.setId("test-job-1");
        req.setType("SEND_EMAIL");
        req.setPayload(new HashMap<>());
        req.setMaxAttempts(3);

        WorkResponse resp = workManager.acceptWork(req);
        assertEquals("ACCEPTED", resp.getStatus());
        assertEquals("test-job-1", resp.getId());

        // Verify it is saved in SQLite database
        Optional<Work> saved = workRepository.findById("test-job-1");
        assertTrue(saved.isPresent());
        assertEquals(WorkStatus.PENDING, saved.get().getStatus());
        assertEquals(3, saved.get().getMaxAttempts());
        assertEquals(0, saved.get().getAttempts());
    }

    @Test
    @Transactional
    public void testDuplicateWorkIsHarmless() {
        WorkRequest req1 = new WorkRequest();
        req1.setId("test-dup-job");
        req1.setType("SEND_EMAIL");
        req1.setPayload(new HashMap<>());
        req1.setMaxAttempts(3);

        WorkResponse resp1 = workManager.acceptWork(req1);
        assertEquals("ACCEPTED", resp1.getStatus());

        // Attempting to submit again should return ACCEPTED without throwing error
        WorkResponse resp2 = workManager.acceptWork(req1);
        assertEquals("ACCEPTED", resp2.getStatus());

        // Ensure database only contains 1 instance of the work item
        long count = workRepository.count();
        assertEquals(1, count);
    }

    @Test
    @Transactional
    public void testFailedWorkRetriesAndFailsPermanently() {
        WorkRequest req = new WorkRequest();
        req.setId("test-fail-job");
        req.setType("SEND_EMAIL");
        req.setPayload(new HashMap<>());
        req.setMaxAttempts(2);
        workManager.acceptWork(req);

        // Simulate 1st failure
        // Claim work assigns worker
        workManager.claimWork("test-fail-job", "worker-1");
        // Finalize with failure
        workManager.finalizeWorkAttempt("test-fail-job", 
                workAttemptRepository.findByWorkIdOrderByAttemptNumberAsc("test-fail-job").get(0).getId(), 
                false, "Connection Timeout", 120);

        Work workAfterFirstFail = workRepository.findById("test-fail-job").orElseThrow();
        assertEquals(WorkStatus.PENDING, workAfterFirstFail.getStatus());
        assertEquals(1, workAfterFirstFail.getAttempts());
        assertNotNull(workAfterFirstFail.getNextAttemptAt());

        // Simulate 2nd failure (max limit reached)
        workManager.claimWork("test-fail-job", "worker-1");
        workManager.finalizeWorkAttempt("test-fail-job", 
                workAttemptRepository.findByWorkIdOrderByAttemptNumberAsc("test-fail-job").get(1).getId(), 
                false, "Critical Failure", 80);

        Work workAfterSecondFail = workRepository.findById("test-fail-job").orElseThrow();
        assertEquals(WorkStatus.FAILED, workAfterSecondFail.getStatus());
        assertEquals(2, workAfterSecondFail.getAttempts());
    }

    @Test
    public void testWorkerRestartLimit() {
        // Find worker node
        Worker worker = workerRepository.findById("worker-1").orElseThrow();
        worker.setStatus(WorkerStatus.RUNNING);
        worker.setRestartCount(5); // At budget limit
        worker.setMaxRestartCount(5);
        worker.setPort(9999); // Closed port to force health check failure
        workerRepository.save(worker);

        // Poll health - should transition to OUT_OF_SERVICE because restart budget is exhausted
        workerManager.checkAllWorkersHealth();

        Worker updated = workerRepository.findById("worker-1").orElseThrow();
        assertEquals(WorkerStatus.OUT_OF_SERVICE, updated.getStatus());
    }

    @Test
    @Transactional
    public void testStateTransitions() {
        Work work = new Work();
        work.setId("transition-job");
        work.setType("TEST");
        work.setStatus(WorkStatus.PENDING);
        work.setAttempts(0);
        work.setMaxAttempts(5);
        workRepository.save(work);

        // Claim
        workManager.claimWork("transition-job", "worker-1");
        Work claimed = workRepository.findById("transition-job").orElseThrow();
        assertEquals(WorkStatus.PROCESSING, claimed.getStatus());
        assertEquals("worker-1", claimed.getAssignedWorkerId());

        // Finalize
        long attemptId = workAttemptRepository.findByWorkIdOrderByAttemptNumberAsc("transition-job").get(0).getId();
        workManager.finalizeWorkAttempt("transition-job", attemptId, true, null, 150);
        Work completed = workRepository.findById("transition-job").orElseThrow();
        assertEquals(WorkStatus.SUCCESS, completed.getStatus());
        assertNull(completed.getLastError());
    }
}
