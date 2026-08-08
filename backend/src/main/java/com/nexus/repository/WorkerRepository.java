package com.nexus.repository;

import com.nexus.entity.Worker;
import com.nexus.enums.Enums.WorkerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WorkerRepository extends JpaRepository<Worker, String> {
    List<Worker> findByStatus(WorkerStatus status);
}
