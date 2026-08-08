package com.nexus.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public class WorkRequest {
    @NotBlank(message = "Job ID is required")
    private String id;

    @NotBlank(message = "Job type is required")
    private String type;

    private Map<String, Object> payload;

    @NotNull(message = "maxAttempts is required")
    @Min(value = 1, message = "maxAttempts must be at least 1")
    private Integer maxAttempts;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }

    public Integer getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(Integer maxAttempts) { this.maxAttempts = maxAttempts; }
}
