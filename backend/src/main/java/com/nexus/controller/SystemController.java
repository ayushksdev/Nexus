package com.nexus.controller;

import com.nexus.entity.Worker;
import com.nexus.enums.Enums.WorkerStatus;
import com.nexus.repository.WorkerRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class SystemController {
    private final WorkerRepository workerRepository;

    public SystemController(WorkerRepository workerRepository) {
        this.workerRepository = workerRepository;
    }

    @GetMapping("/api/system/status")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        List<Worker> workers = workerRepository.findAll();
        boolean degraded = false;
        String reason = "";

        // Check if any worker is out of service or failed
        for (Worker w : workers) {
            if (w.getStatus() == WorkerStatus.OUT_OF_SERVICE) {
                degraded = true;
                reason = "Worker " + w.getId() + " is out of service (restart budget exhausted)";
                break;
            } else if (w.getStatus() == WorkerStatus.FAILED) {
                degraded = true;
                reason = "Worker " + w.getId() + " is currently in FAILED state";
                break;
            }
        }

        Map<String, Object> response = new HashMap<>();
        if (degraded) {
            response.put("status", "DEGRADED");
            response.put("reason", reason);
        } else {
            response.put("status", "HEALTHY");
            response.put("reason", "All services operating normally");
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/actuator/health")
    public ResponseEntity<Map<String, String>> getActuatorHealth() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        return ResponseEntity.ok(response);
    }
}
