package com.nexus.controller;

import com.nexus.entity.Worker;
import com.nexus.enums.Enums.WorkerFailureMode;
import com.nexus.repository.WorkerRepository;
import com.nexus.service.WorkerManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workers")
public class WorkerController {
    private final WorkerRepository workerRepository;
    private final WorkerManager workerManager;

    public WorkerController(WorkerRepository workerRepository, WorkerManager workerManager) {
        this.workerRepository = workerRepository;
        this.workerManager = workerManager;
    }

    @GetMapping
    public ResponseEntity<List<Worker>> getAllWorkers() {
        return ResponseEntity.ok(workerRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Worker> getWorkerById(@PathVariable String id) {
        Worker worker = workerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Worker not found with ID: " + id));
        return ResponseEntity.ok(worker);
    }

    @PostMapping("/{id}/recover")
    public ResponseEntity<Map<String, String>> recoverWorker(@PathVariable String id) {
        workerManager.recoverWorker(id);
        Map<String, String> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Worker recovery sequence initiated");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/failure-mode")
    public ResponseEntity<Map<String, String>> setFailureMode(@PathVariable String id, @RequestBody Map<String, String> body) {
        String modeStr = body.get("mode");
        if (modeStr == null) {
            modeStr = body.get("failureMode"); // Fallback check
        }
        if (modeStr == null) {
            throw new IllegalArgumentException("Failure mode parameter 'mode' is required in request body.");
        }

        WorkerFailureMode mode = WorkerFailureMode.valueOf(modeStr.toUpperCase());
        workerManager.setFailureMode(id, mode);

        Map<String, String> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Worker failure mode updated to " + mode);
        return ResponseEntity.ok(response);
    }
}
