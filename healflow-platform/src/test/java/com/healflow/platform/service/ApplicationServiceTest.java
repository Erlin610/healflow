package com.healflow.platform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.healflow.platform.entity.ApplicationEntity;
import com.healflow.platform.repository.ApplicationRepository;
import com.healflow.platform.service.ApplicationService.ApplicationRequest;
import com.healflow.platform.service.ApplicationService.ApplicationResponse;
import com.healflow.platform.service.ApplicationService.ApplicationSecrets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ApplicationServiceTest {

  @Autowired private ApplicationService applicationService;
  @Autowired private ApplicationRepository applicationRepository;

  @BeforeEach
  void setUp() {
    applicationRepository.deleteAll();
  }

  @Test
  void createStoresEncryptedSecretsAndDecrypts() {
    ApplicationRequest request =
        new ApplicationRequest(
            "app-one",
            "https://example.invalid/repo.git",
            "main",
            "git-token",
            "ai-key",
            true,
            false,
            true,
            "https://example.invalid/webhook");

    ApplicationResponse response = applicationService.create(request);

    assertEquals("app-one", response.appName());
    assertEquals("****", response.gitToken());
    assertEquals("****", response.aiApiKey());

    ApplicationEntity stored = applicationRepository.findById("app-one").orElseThrow();
    assertNotEquals("git-token", stored.getGitToken());
    assertNotEquals("ai-key", stored.getAiApiKey());
    assertNotNull(stored.getGitToken());
    assertNotNull(stored.getAiApiKey());

    ApplicationSecrets secrets = applicationService.getApplicationSecrets("app-one");
    assertEquals("git-token", secrets.gitToken());
    assertEquals("ai-key", secrets.aiApiKey());
  }

  @Test
  void updatePreservesSecretsWhenOmittedAndListsMasked() {
    ApplicationRequest request =
        new ApplicationRequest(
            "app-two",
            "https://example.invalid/repo.git",
            "main",
            "git-token",
            "ai-key",
            false,
            false,
            false,
            null);
    applicationService.create(request);

    String encryptedGitToken = applicationRepository.findById("app-two").orElseThrow().getGitToken();
    String encryptedAiKey = applicationRepository.findById("app-two").orElseThrow().getAiApiKey();

    ApplicationRequest update =
        new ApplicationRequest("app-two", null, "dev", null, null, true, null, null, null);
    ApplicationResponse updated = applicationService.update("app-two", update);

    assertEquals("dev", updated.gitBranch());
    assertTrue(updated.autoAnalyze());

    ApplicationEntity stored = applicationRepository.findById("app-two").orElseThrow();
    assertEquals(encryptedGitToken, stored.getGitToken());
    assertEquals(encryptedAiKey, stored.getAiApiKey());

    List<ApplicationResponse> responses = applicationService.listApplications();
    assertEquals(1, responses.size());
    assertEquals("****", responses.get(0).gitToken());
    assertEquals("****", responses.get(0).aiApiKey());
  }

  @Test
  void updateClearsSecretsAndDeletes() {
    ApplicationRequest request =
        new ApplicationRequest(
            "app-three",
            "https://example.invalid/repo.git",
            "main",
            "git-token",
            "ai-key",
            false,
            false,
            false,
            null);
    applicationService.create(request);

    ApplicationRequest clearSecrets =
        new ApplicationRequest("app-three", null, null, " ", "", null, null, null, null);
    applicationService.update("app-three", clearSecrets);

    ApplicationEntity stored = applicationRepository.findById("app-three").orElseThrow();
    assertNull(stored.getGitToken());
    assertNull(stored.getAiApiKey());

    assertTrue(applicationService.delete("app-three"));
    assertFalse(applicationService.delete("app-three"));
  }
}
