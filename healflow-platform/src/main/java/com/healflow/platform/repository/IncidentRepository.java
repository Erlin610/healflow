package com.healflow.platform.repository;

import com.healflow.common.enums.IncidentStatus;
import com.healflow.platform.entity.IncidentEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentRepository extends JpaRepository<IncidentEntity, String> {
  List<IncidentEntity> findByAppId(String appId);

  List<IncidentEntity> findByStatus(IncidentStatus status);

  List<IncidentEntity> findByStatusOrderByCreatedAtDesc(IncidentStatus status);

  List<IncidentEntity> findAllByOrderByCreatedAtDesc();

  List<IncidentEntity> findByFingerprintId(String fingerprintId);

  boolean existsByFingerprintIdAndStatusInAndCreatedAtGreaterThanEqual(
      String fingerprintId, List<IncidentStatus> status, Instant createdAt);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT i FROM IncidentEntity i WHERE i.fingerprintId = :fingerprintId AND i.status = :status ORDER BY i.createdAt ASC")
  Optional<IncidentEntity> findFirstByFingerprintIdAndStatusWithLock(
      @Param("fingerprintId") String fingerprintId, @Param("status") IncidentStatus status);
}
