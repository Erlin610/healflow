package com.healflow.starter.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.healflow.starter.util.GitPropertiesLoader.GitMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class GitPropertiesLoaderTest {

  @Test
  void loadsGitMetadataFromDefaultLocation() {
    GitPropertiesLoader loader = new GitPropertiesLoader(new DefaultResourceLoader());
    GitMetadata gitMetadata = loader.load();

    assertThat(gitMetadata.commitId()).isEqualTo("abcdef123456");
    assertThat(gitMetadata.branch()).isEqualTo("main");
    assertThat(gitMetadata.buildTime()).isEqualTo("2026-01-04T00:00:00Z");
  }

  @Test
  void fallsBackToShortCommitIdAndUnknownValues() {
    GitPropertiesLoader loader = new GitPropertiesLoader(new DefaultResourceLoader(), "classpath:git-short.properties");
    GitMetadata gitMetadata = loader.load();

    assertThat(gitMetadata.commitId()).isEqualTo("abc1234");
    assertThat(gitMetadata.branch()).isEqualTo("unknown");
    assertThat(gitMetadata.buildTime()).isEqualTo("unknown");
  }

  @Test
  void fallsBackToShortCommitIdWhenFullIdBlank() {
    GitPropertiesLoader loader =
        new GitPropertiesLoader(new DefaultResourceLoader(), "classpath:git-blank-full.properties");
    GitMetadata gitMetadata = loader.load();

    assertThat(gitMetadata.commitId()).isEqualTo("deadbeef");
  }

  @Test
  void usesDefaultsWhenResourceMissing() {
    GitPropertiesLoader loader =
        new GitPropertiesLoader(new DefaultResourceLoader(), "classpath:does-not-exist.properties");
    GitMetadata gitMetadata = loader.load();

    assertThat(gitMetadata.commitId()).isEqualTo("HEAD");
    assertThat(gitMetadata.branch()).isEqualTo("unknown");
    assertThat(gitMetadata.buildTime()).isEqualTo("unknown");
  }
}

