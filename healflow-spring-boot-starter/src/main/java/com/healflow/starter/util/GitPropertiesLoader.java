package com.healflow.starter.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

public class GitPropertiesLoader {

  public static final String DEFAULT_LOCATION = "classpath:git.properties";

  static final String KEY_COMMIT_ID_FULL = "git.commit.id.full";
  static final String KEY_COMMIT_ID = "git.commit.id";
  static final String KEY_BRANCH = "git.branch";
  static final String KEY_BUILD_TIME = "git.build.time";

  static final String DEFAULT_COMMIT_ID = "HEAD";
  static final String DEFAULT_VALUE = "unknown";

  private final ResourceLoader resourceLoader;
  private final String gitPropertiesLocation;

  public GitPropertiesLoader(ResourceLoader resourceLoader) {
    this(resourceLoader, DEFAULT_LOCATION);
  }

  public GitPropertiesLoader(ResourceLoader resourceLoader, String gitPropertiesLocation) {
    this.resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader");
    this.gitPropertiesLocation = Objects.requireNonNull(gitPropertiesLocation, "gitPropertiesLocation");
  }

  public GitMetadata load() {
    Resource resource = resourceLoader.getResource(gitPropertiesLocation);
    if (!resource.exists()) {
      return new GitMetadata(DEFAULT_COMMIT_ID, DEFAULT_VALUE, DEFAULT_VALUE);
    }

    Properties properties = new Properties();
    try (InputStream in = resource.getInputStream()) {
      properties.load(in);
    } catch (IOException ignored) {
      return new GitMetadata(DEFAULT_COMMIT_ID, DEFAULT_VALUE, DEFAULT_VALUE);
    }

    String commitIdFull = properties.getProperty(KEY_COMMIT_ID_FULL);
    String commitId = properties.getProperty(KEY_COMMIT_ID);
    String resolvedCommitId =
        (commitIdFull != null && !commitIdFull.isBlank())
            ? commitIdFull
            : (commitId != null && !commitId.isBlank()) ? commitId : DEFAULT_COMMIT_ID;

    String branch = properties.getProperty(KEY_BRANCH, DEFAULT_VALUE);
    String buildTime = properties.getProperty(KEY_BUILD_TIME, DEFAULT_VALUE);

    return new GitMetadata(resolvedCommitId, branch, buildTime);
  }

  public record GitMetadata(String commitId, String branch, String buildTime) {
    public GitMetadata {
      commitId = Objects.requireNonNull(commitId, "commitId");
      branch = Objects.requireNonNull(branch, "branch");
      buildTime = Objects.requireNonNull(buildTime, "buildTime");
    }
  }
}

