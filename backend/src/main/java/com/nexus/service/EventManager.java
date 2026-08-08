package com.nexus.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.entity.Event;
import com.nexus.entity.Release;
import com.nexus.repository.EventRepository;
import com.nexus.repository.ReleaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class EventManager {
    private static final Logger log = LoggerFactory.getLogger(EventManager.class);
    private final EventRepository eventRepository;
    private final ReleaseRepository releaseRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EventManager(EventRepository eventRepository, ReleaseRepository releaseRepository) {
        this.eventRepository = eventRepository;
        this.releaseRepository = releaseRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Event logEvent(String eventType, String subjectType, String subjectId, String message, String reason, Map<String, Object> metadata) {
        Event event = new Event();
        event.setTimestamp(LocalDateTime.now());
        event.setEventType(eventType);
        event.setSubjectType(subjectType);
        event.setSubjectId(subjectId);
        event.setMessage(message);
        event.setReason(reason);

        // Fetch active release to correlate failures
        releaseRepository.findActiveRelease().ifPresent(activeRelease -> {
            event.setReleaseId(activeRelease.getId());
        });

        if (metadata != null) {
            try {
                event.setMetadata(objectMapper.writeValueAsString(metadata));
            } catch (Exception e) {
                log.error("Failed to serialize event metadata", e);
                event.setMetadata("{}");
            }
        } else {
            event.setMetadata("{}");
        }

        Event saved = eventRepository.save(event);

        // Structured logging output to complement DB
        log.info("[{}] subject={}/{} message=\"{}\" reason=\"{}\" metadata={}",
                eventType, subjectType, subjectId, message, reason, event.getMetadata());

        return saved;
    }
}
