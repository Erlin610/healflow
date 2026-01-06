package com.healflow.engine.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Service
public class GitWorkspaceManager {

  private static final Logger log = LoggerFactory.getLogger(GitWorkspaceManager.class);

  // 工作区根目录，例如 /data/healflow-workspace
  @Value("${healflow.workspace.root:/tmp/healflow-workspace}")
  private String workspaceRoot;

  // 每个 appId 的锁对象，防止并发冲突
  private final ConcurrentHashMap<String, Object> appLocks = new ConcurrentHashMap<>();

  /**
   * 准备代码环境
   *
   * @param appId 应用ID (用于区分目录)
   * @param repoUrl Git仓库地址
   * @param commitId 目标 Commit ID
   * @return 准备好的本地源码路径
   */
  public Path prepareWorkspace(String appId, String repoUrl, String branch) {
    Object lock = appLocks.computeIfAbsent(appId, k -> new Object());

    synchronized (lock) {
      Path appDir = Path.of(workspaceRoot, appId);

      try {
        Files.createDirectories(appDir.getParent());

        if (Files.exists(appDir) && isNotEmptyDirectory(appDir)) {
          // 目录存在且非空 -> 执行更新 (Fetch & Reset)
          updateRepository(appDir.toFile(), branch);
        } else {
          // 目录不存在 -> 执行克隆 (Clone)
          cloneRepository(appDir.toFile(), repoUrl, branch);
        }
        return appDir.toAbsolutePath();
      } catch (Exception e) {
        log.error("Failed to prepare workspace for app: {}", appId, e);
        throw new RuntimeException("Git workspace preparation failed", e);
      }
    }
  }

  private void cloneRepository(File dir, String repoUrl, String branch) throws GitAPIException {
    log.info("⚡️ Cloning repository: {} -> {}", repoUrl, dir.getAbsolutePath());

    // 1. Clone
    try (Git git =
        Git.cloneRepository()
            .setURI(repoUrl)
            .setDirectory(dir)
            // .setCredentialsProvider(...) // 如果是私有仓库，需要在这里设置账号密码或Token
            .call()) {

      // 2. Checkout 到指定Commit
      checkoutBranch(git, branch);
    }
  }

  private void updateRepository(File dir, String branch) throws IOException, GitAPIException {
    log.info("🔄 Updating repository in: {}", dir.getAbsolutePath());

    try (Git git = Git.open(dir)) {
      // 1. Fetch 获取最新远程变更
      git.fetch().call();

      // 2. Hard Reset 到指定Commit (丢弃本地可能的脏数据，保证纯净)
      checkoutBranch(git, branch);
    }
  }

  private void checkoutBranch(Git git, String branch) throws GitAPIException {
    String resolvedBranch = defaultBranch(branch);
    log.info("🎯 Checking out branch: {}", resolvedBranch);

    git.checkout().setName(resolvedBranch).call();
    git.reset().setMode(ResetType.HARD).setRef("origin/" + resolvedBranch).call();
  }

  private static String defaultBranch(String branch) {
    return (branch == null || branch.isBlank()) ? "main" : branch;
  }

  private static boolean isNotEmptyDirectory(Path dir) throws IOException {
    try (Stream<Path> stream = Files.list(dir)) {
      return stream.findAny().isPresent();
    }
  }
}
