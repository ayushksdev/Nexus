package com.nexus.entity;

import com.nexus.enums.Enums.ReleaseStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "release_history")
public class Release {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String serviceName;
    private String version;
    private String previousVersion;

    @Enumerated(EnumType.STRING)
    private ReleaseStatus status;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private boolean rollbackAvailable;

    @Column(columnDefinition = "TEXT")
    private String reason;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getPreviousVersion() { return previousVersion; }
    public void setPreviousVersion(String previousVersion) { this.previousVersion = previousVersion; }

    public ReleaseStatus getStatus() { return status; }
    public void setStatus(ReleaseStatus status) { this.status = status; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public boolean isRollbackAvailable() { return rollbackAvailable; }
    public void setRollbackAvailable(boolean rollbackAvailable) { this.rollbackAvailable = rollbackAvailable; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
