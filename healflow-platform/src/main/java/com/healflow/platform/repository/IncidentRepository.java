package com.healflow.platform.repository;

import com.healflow.platform.entity.IncidentEntity;
import com.healflow.platform.entity.IncidentStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<IncidentEntity, String> {
  List<IncidentEntity> findByAppId(String appId);

  List<IncidentEntity> findByStatus(IncidentStatus status);
}

