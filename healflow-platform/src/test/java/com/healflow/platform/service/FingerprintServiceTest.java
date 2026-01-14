package com.healflow.platform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.healflow.platform.entity.ErrorFingerprintEntity;
import com.healflow.platform.repository.ErrorFingerprintRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class FingerprintServiceTest {

  @Autowired private ErrorFingerprintRepository repository;

  @Test
  void sameStackGeneratesSameFingerprint() {
    FingerprintService service = new FingerprintService(repository, Clock.systemUTC());
    String stackTrace =
        String.join(
            "\n",
            "java.lang.RuntimeException: top",
            "at java.lang.Thread.run(Thread.java:833)",
            "Caused by: java.lang.IllegalStateException: root",
            "at com.example.service.UserService.loadUser(UserService.java:45)",
            "at com.example.service.UserService.validate(UserService.java:54)",
            "at com.example.web.UserController.handle(UserController.java:27)");

    String first = service.generateFingerprint("IllegalStateException", stackTrace);
    String second = service.generateFingerprint("IllegalStateException", stackTrace);

    assertEquals(first, second);
  }

  @Test
  void differentStackGeneratesDifferentFingerprint() {
    FingerprintService service = new FingerprintService(repository, Clock.systemUTC());
    String stackTraceA =
        String.join(
            "\n",
            "java.lang.RuntimeException: top",
            "Caused by: java.lang.IllegalStateException: root",
            "at com.example.service.UserService.loadUser(UserService.java:45)",
            "at com.example.web.UserController.handle(UserController.java:27)");
    String stackTraceB =
        String.join(
            "\n",
            "java.lang.RuntimeException: top",
            "Caused by: java.lang.IllegalStateException: root",
            "at com.example.service.UserService.saveUser(UserService.java:45)",
            "at com.example.web.UserController.handle(UserController.java:27)");

    String first = service.generateFingerprint("IllegalStateException", stackTraceA);
    String second = service.generateFingerprint("IllegalStateException", stackTraceB);

    assertNotEquals(first, second);
  }

  @Test
  void dynamicValuesDoNotAffectFingerprint() {
    FingerprintService service = new FingerprintService(repository, Clock.systemUTC());
    String stackTraceA =
        String.join(
            "\n",
            "Caused by: java.lang.IllegalStateException: userId=123",
            "at com.example.service.UserService.loadUser(UserService.java:45)",
            "at com.example.web.UserController.handle(UserController.java:27)");
    String stackTraceB =
        String.join(
            "\n",
            "Caused by: java.lang.IllegalStateException: userId=987",
            "at com.example.service.UserService.loadUser(UserService.java:99)",
            "at com.example.web.UserController.handle(UserController.java:27)");

    String first = service.generateFingerprint("IllegalStateException", stackTraceA);
    String second = service.generateFingerprint("IllegalStateException", stackTraceB);

    assertEquals(first, second);
  }

  @Test
  void recordOccurrenceCreatesAndUpdatesCount() {
    Instant firstSeen = Instant.parse("2026-01-05T00:00:00Z");
    Instant secondSeen = firstSeen.plusSeconds(60);
    String stackTrace =
        String.join(
            "\n",
            "Caused by: java.lang.IllegalStateException: root",
            "at com.example.service.UserService.loadUser(UserService.java:45)",
            "at com.example.web.UserController.handle(UserController.java:27)");

    FingerprintService firstService =
        new FingerprintService(repository, Clock.fixed(firstSeen, ZoneOffset.UTC));
    ErrorFingerprintEntity first = firstService.recordOccurrence("IllegalStateException", stackTrace);

    assertEquals(1L, first.getOccurrenceCount());
    assertEquals(firstSeen, first.getLastSeenTime());
    assertEquals(1L, repository.count());

    FingerprintService secondService =
        new FingerprintService(repository, Clock.fixed(secondSeen, ZoneOffset.UTC));
    ErrorFingerprintEntity second = secondService.recordOccurrence("IllegalStateException", stackTrace);

    assertEquals(2L, second.getOccurrenceCount());
    assertEquals(secondSeen, second.getLastSeenTime());
    assertEquals(first.getFingerprint(), second.getFingerprint());
    assertEquals(1L, repository.count());
  }

  @Test
  void blankErrorTypeIsRejected() {
    FingerprintService service = new FingerprintService(repository, Clock.systemUTC());

    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> service.generateFingerprint(" ", "stack"));

    assertNotNull(error.getMessage());
  }

  @Test
  void jdkOnlyFramesStillProduceStableFingerprint() {
    FingerprintService service = new FingerprintService(repository, Clock.systemUTC());
    String stackTraceA =
        String.join(
            "\n",
            "Caused by: java.lang.IllegalArgumentException: userId=123",
            "at java.lang.Thread.run(Thread.java:833)",
            "at java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:511)");
    String stackTraceB =
        String.join(
            "\n",
            "Caused by: java.lang.IllegalArgumentException: userId=987",
            "at java.lang.Thread.run(Thread.java:999)",
            "at java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:600)");

    String first = service.generateFingerprint("IllegalArgumentException", stackTraceA);
    String second = service.generateFingerprint("IllegalArgumentException", stackTraceB);

    assertEquals(first, second);
  }
}
