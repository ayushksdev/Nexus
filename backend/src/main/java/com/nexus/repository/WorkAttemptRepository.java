package com.nexus.repository;

import com.nexus.entity.WorkAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WorkAttemptRepository extends JpaRepository<WorkAttempt, Long> {
    List<WorkAttempt> findByWorkIdOrderByAttemptNumberAsc(String workId);
}
