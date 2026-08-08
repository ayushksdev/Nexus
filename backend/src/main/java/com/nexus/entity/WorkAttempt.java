package com.nexus.entity;

import com.nexus.enums.Enums.WorkAttemptStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "work_attempt")
public class WorkAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String workId;
    private int attemptNumber;
    private String workerId;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @Enumerated(EnumType.STRING)
    private WorkAttemptStatus status;

    @Column(columnDefinition = "TEXT")
    private String error;

    private Long durationMs;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getWorkId() { return workId; }
    public void setWorkId(String workId) { this.workId = workId; }

    public int getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(int attemptNumber) { this.attemptNumber = attemptNumber; }

    public String getWorkerId() { return workerId; }
    public void setWorkerId(String workerId) { this.workerId = workerId; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public WorkAttemptStatus getStatus() { return status; }
    public void setStatus(WorkAttemptStatus status) { this.status = status; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
}
