package com.nexus.repository;

import com.nexus.entity.Release;
import com.nexus.enums.Enums.ReleaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReleaseRepository extends JpaRepository<Release, Long> {
    List<Release> findByStatus(ReleaseStatus status);

    @Query("SELECT r FROM Release r ORDER BY r.startedAt DESC LIMIT 1")
    Optional<Release> findLatestRelease();

    @Query("SELECT r FROM Release r WHERE r.status IN ('PREPARING', 'DEPLOYING', 'WATCHING') ORDER BY r.startedAt DESC LIMIT 1")
    Optional<Release> findActiveRelease();
}
