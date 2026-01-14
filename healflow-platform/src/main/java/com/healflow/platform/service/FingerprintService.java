package com.healflow.platform.service;

import com.healflow.platform.entity.ErrorFingerprintEntity;
import com.healflow.platform.repository.ErrorFingerprintRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FingerprintService {

  private static final int MAX_FRAMES = 3;
  private static final List<String> IGNORED_PREFIXES =
      List.of("java.", "javax.", "sun.", "jdk.", "com.sun.");

  private final ErrorFingerprintRepository repository;
  private final Clock clock;

  @Autowired
  public FingerprintService(ErrorFingerprintRepository repository) {
    this(repository, Clock.systemUTC());
  }

  FingerprintService(ErrorFingerprintRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  public ErrorFingerprintEntity recordOccurrence(String errorType, String stackTrace) {
    String fingerprint = generateFingerprint(errorType, stackTrace);
    Instant now = Instant.now(clock);
    return repository
        .findById(fingerprint)
        .map(
            existing -> {
              existing.markSeen(now);
              return repository.save(existing);
            })
        .orElseGet(() -> repository.save(new ErrorFingerprintEntity(fingerprint, now)));
  }

  public Optional<ErrorFingerprintEntity> findByFingerprint(String fingerprint) {
    return repository.findById(fingerprint);
  }

  public String generateFingerprint(String errorType, String stackTrace) {
    String type = requireNonBlank(errorType, "errorType");
    String normalizedStack = normalizeRootStack(stackTrace);
    String payload = type + "\n" + normalizedStack;
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
      return toHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  private static String normalizeRootStack(String stackTrace) {
    if (stackTrace == null || stackTrace.isBlank()) {
      return "";
    }
    String rootSegment = extractRootSegment(stackTrace);
    String[] lines = rootSegment.split("\\R");
    List<String> frames = new ArrayList<>(MAX_FRAMES);
    List<String> fallbackFrames = new ArrayList<>(MAX_FRAMES);
    for (String line : lines) {
      String trimmed = line.trim();
      if (!trimmed.startsWith("at ")) {
        continue;
      }
      String frame = trimmed.substring(3).trim();
      frame = stripLineInfo(frame);
      if (fallbackFrames.size() < MAX_FRAMES) {
        fallbackFrames.add(frame);
      }
      if (isIgnoredFrame(frame)) {
        continue;
      }
      frames.add(frame);
      if (frames.size() == MAX_FRAMES) {
        break;
      }
    }
    if (frames.isEmpty()) {
      if (!fallbackFrames.isEmpty()) {
        return String.join("\n", fallbackFrames);
      }
      return rootSegment.trim();
    }
    return String.join("\n", frames);
  }

  private static String extractRootSegment(String stackTrace) {
    int index = stackTrace.lastIndexOf("Caused by:");
    if (index < 0) {
      return stackTrace;
    }
    return stackTrace.substring(index);
  }

  private static String stripLineInfo(String frame) {
    int paren = frame.indexOf('(');
    if (paren < 0) {
      return frame;
    }
    return frame.substring(0, paren);
  }

  private static boolean isIgnoredFrame(String frame) {
    for (String prefix : IGNORED_PREFIXES) {
      if (frame.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  private static String toHex(byte[] bytes) {
    StringBuilder builder = new StringBuilder(bytes.length * 2);
    for (byte value : bytes) {
      builder.append(String.format("%02x", value));
    }
    return builder.toString();
  }

  private static String requireNonBlank(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }
}
