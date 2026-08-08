package com.nexus.entity;

import com.nexus.enums.Enums.WorkerFailureMode;
import com.nexus.enums.Enums.WorkerStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "worker")
public class Worker {
    @Id
    private String id;

    private String name;

    @Enumerated(EnumType.STRING)
    private WorkerStatus status;

    private String version;
    private int restartCount;
    private int maxRestartCount;
    private LocalDateTime lastStartedAt;
    private LocalDateTime lastHealthyAt;
    private LocalDateTime lastFailureAt;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    @Enumerated(EnumType.STRING)
    private WorkerFailureMode failureMode;

    private int port;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public WorkerStatus getStatus() { return status; }
    public void setStatus(WorkerStatus status) { this.status = status; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public int getRestartCount() { return restartCount; }
    public void setRestartCount(int restartCount) { this.restartCount = restartCount; }

    public int getMaxRestartCount() { return maxRestartCount; }
    public void setMaxRestartCount(int maxRestartCount) { this.maxRestartCount = maxRestartCount; }

    public LocalDateTime getLastStartedAt() { return lastStartedAt; }
    public void setLastStartedAt(LocalDateTime lastStartedAt) { this.lastStartedAt = lastStartedAt; }

    public LocalDateTime getLastHealthyAt() { return lastHealthyAt; }
    public void setLastHealthyAt(LocalDateTime lastHealthyAt) { this.lastHealthyAt = lastHealthyAt; }

    public LocalDateTime getLastFailureAt() { return lastFailureAt; }
    public void setLastFailureAt(LocalDateTime lastFailureAt) { this.lastFailureAt = lastFailureAt; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public WorkerFailureMode getFailureMode() { return failureMode; }
    public void setFailureMode(WorkerFailureMode failureMode) { this.failureMode = failureMode; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
}
