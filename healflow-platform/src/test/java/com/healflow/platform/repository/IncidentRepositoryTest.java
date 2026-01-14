package com.healflow.platform.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.healflow.common.enums.IncidentStatus;
import com.healflow.platform.entity.IncidentEntity;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class IncidentRepositoryTest {
  @Autowired private IncidentRepository incidentRepository;

  @Test
  void savesAndLoadsIncident() {
    IncidentEntity incident = new IncidentEntity("inc-1", "app-1", IncidentStatus.OPEN);
    incident.setRepoUrl("https://example.invalid/repo.git");
    incident.setBranch("main");

    incidentRepository.saveAndFlush(incident);

    IncidentEntity loaded = incidentRepository.findById("inc-1").orElseThrow();
    assertEquals("app-1", loaded.getAppId());
    assertEquals("https://example.invalid/repo.git", loaded.getRepoUrl());
    assertEquals("main", loaded.getBranch());
    assertEquals(IncidentStatus.OPEN, loaded.getStatus());
    assertNotNull(loaded.getCreatedAt());
    assertNotNull(loaded.getUpdatedAt());
  }

  @Test
  void supportsQueriesByAppIdAndStatus() {
    incidentRepository.saveAll(
        List.of(
            new IncidentEntity("inc-a", "app-x", IncidentStatus.OPEN),
            new IncidentEntity("inc-b", "app-x", IncidentStatus.PENDING_REVIEW),
            new IncidentEntity("inc-c", "app-y", IncidentStatus.PENDING_REVIEW)));
    incidentRepository.flush();

    assertEquals(2, incidentRepository.findByAppId("app-x").size());
    assertEquals(2, incidentRepository.findByStatus(IncidentStatus.PENDING_REVIEW).size());
  }

  @Test
  void updatesIncrementVersion() {
    IncidentEntity incident = new IncidentEntity("inc-version", "app-1", IncidentStatus.OPEN);
    incidentRepository.saveAndFlush(incident);

    long versionBefore = incidentRepository.findById("inc-version").orElseThrow().getVersion();
    IncidentEntity loaded = incidentRepository.findById("inc-version").orElseThrow();
    loaded.setSessionId("sess-1");
    incidentRepository.saveAndFlush(loaded);

    long versionAfter = incidentRepository.findById("inc-version").orElseThrow().getVersion();
    assertTrue(versionAfter > versionBefore);
  }

  @Test
  void statusChangedAtUpdatesWhenStatusChanges() throws Exception {
    IncidentEntity incident = new IncidentEntity("inc-status", "app-1", IncidentStatus.OPEN);
    incidentRepository.saveAndFlush(incident);

    Instant before = incidentRepository.findById("inc-status").orElseThrow().getStatusChangedAt();
    TimeUnit.MILLISECONDS.sleep(5);

    IncidentEntity loaded = incidentRepository.findById("inc-status").orElseThrow();
    loaded.setStatus(IncidentStatus.ANALYZING);
    incidentRepository.saveAndFlush(loaded);

    Instant after = incidentRepository.findById("inc-status").orElseThrow().getStatusChangedAt();
    assertTrue(after.isAfter(before));
  }

  @Test
  void incidentEntityValidatesRequiredFields() {
    assertThrows(IllegalArgumentException.class, () -> new IncidentEntity(" ", "app-1", IncidentStatus.OPEN));
    assertThrows(IllegalArgumentException.class, () -> new IncidentEntity("inc-1", " ", IncidentStatus.OPEN));

    IncidentEntity entity = new IncidentEntity("inc-1", "app-1", IncidentStatus.OPEN);
    assertThrows(IllegalArgumentException.class, () -> entity.setAppId(" "));
    assertThrows(IllegalArgumentException.class, () -> entity.setStatus(null));
  }
}
