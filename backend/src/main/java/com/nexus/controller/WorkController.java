package com.nexus.controller;

import com.nexus.dto.WorkRequest;
import com.nexus.dto.WorkResponse;
import com.nexus.entity.Work;
import com.nexus.entity.WorkAttempt;
import com.nexus.repository.WorkAttemptRepository;
import com.nexus.repository.WorkRepository;
import com.nexus.service.WorkManager;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/work")
public class WorkController {
    private final WorkManager workManager;
    private final WorkRepository workRepository;
    private final WorkAttemptRepository workAttemptRepository;

    public WorkController(WorkManager workManager, WorkRepository workRepository, WorkAttemptRepository workAttemptRepository) {
        this.workManager = workManager;
        this.workRepository = workRepository;
        this.workAttemptRepository = workAttemptRepository;
    }

    @PostMapping
    public ResponseEntity<WorkResponse> submitWork(@Valid @RequestBody WorkRequest request) {
        WorkResponse response = workManager.acceptWork(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<Work>> getAllWork() {
        return ResponseEntity.ok(workRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getWorkById(@PathVariable String id) {
        Work work = workRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Work not found with ID: " + id));

        List<WorkAttempt> attempts = workAttemptRepository.findByWorkIdOrderByAttemptNumberAsc(id);

        Map<String, Object> response = new HashMap<>();
        response.put("work", work);
        response.put("attempts", attempts);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<Map<String, String>> retryWork(@PathVariable String id) {
        workManager.manualRetry(id);
        Map<String, String> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Manual retry scheduled successfully");
        return ResponseEntity.ok(response);
    }
}
