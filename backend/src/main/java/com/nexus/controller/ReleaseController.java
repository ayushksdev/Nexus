package com.nexus.controller;

import com.nexus.entity.Release;
import com.nexus.repository.ReleaseRepository;
import com.nexus.service.ReleaseManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/releases")
public class ReleaseController {
    private final ReleaseRepository releaseRepository;
    private final ReleaseManager releaseManager;

    public ReleaseController(ReleaseRepository releaseRepository, ReleaseManager releaseManager) {
        this.releaseRepository = releaseRepository;
        this.releaseManager = releaseManager;
    }

    @GetMapping
    public ResponseEntity<List<Release>> getAllReleases() {
        return ResponseEntity.ok(releaseRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Release> getReleaseById(@PathVariable Long id) {
        Release release = releaseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Release not found with ID: " + id));
        return ResponseEntity.ok(release);
    }

    @PostMapping
    public ResponseEntity<Release> deployRelease(@RequestBody Map<String, String> body) {
        String version = body.get("version");
        if (version == null || version.trim().isEmpty()) {
            throw new IllegalArgumentException("Parameter 'version' is required in request body.");
        }
        Release release = releaseManager.deployRelease(version);
        return ResponseEntity.ok(release);
    }

    @PostMapping("/{id}/rollback")
    public ResponseEntity<Map<String, Object>> rollbackRelease(@PathVariable Long id) {
        Release release = releaseManager.rollbackRelease(id);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Rollback sequence executed successfully");
        response.put("release", release);
        return ResponseEntity.ok(response);
    }
}
