package com.nexus.repository;

import com.nexus.entity.Work;
import com.nexus.enums.Enums.WorkStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface WorkRepository extends JpaRepository<Work, String> {
    List<Work> findByStatus(WorkStatus status);

    @Query("SELECT w FROM Work w WHERE w.status = 'PENDING' AND (w.nextAttemptAt IS NULL OR w.nextAttemptAt <= :now) ORDER BY w.createdAt ASC")
    List<Work> findPendingJobsToProcess(LocalDateTime now);

    @Query("SELECT w FROM Work w WHERE w.status = 'PROCESSING' AND w.updatedAt <= :timeoutTime")
    List<Work> findTimedOutJobs(LocalDateTime timeoutTime);

    @Query("SELECT MIN(w.createdAt) FROM Work w WHERE w.status = 'PENDING'")
    LocalDateTime getOldestPendingCreatedAt();
}
