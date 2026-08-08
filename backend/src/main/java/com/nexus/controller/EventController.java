package com.nexus.controller;

import com.nexus.entity.Event;
import com.nexus.repository.EventRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {
    private final EventRepository eventRepository;

    public EventController(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @GetMapping
    public ResponseEntity<List<Event>> getAllEvents() {
        return ResponseEntity.ok(eventRepository.findAllByOrderByTimestampDesc());
    }

    @GetMapping("/{subjectType}/{subjectId}")
    public ResponseEntity<List<Event>> getEventsBySubject(@PathVariable String subjectType, @PathVariable String subjectId) {
        return ResponseEntity.ok(eventRepository.findBySubjectTypeAndSubjectIdOrderByTimestampDesc(
                subjectType.toUpperCase(), subjectId));
    }
}
