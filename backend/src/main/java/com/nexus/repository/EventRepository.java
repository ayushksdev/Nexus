package com.nexus.repository;

import com.nexus.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findBySubjectTypeAndSubjectIdOrderByTimestampDesc(String subjectType, String subjectId);

    List<Event> findAllByOrderByTimestampDesc();

    List<Event> findByReleaseIdOrderByTimestampDesc(Long releaseId);
}
