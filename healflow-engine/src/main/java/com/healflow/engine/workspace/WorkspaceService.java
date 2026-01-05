package com.healflow.engine.workspace;

import com.healflow.common.validation.Arguments;
import com.healflow.engine.git.JGitManager;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.regex.Pattern;

public final class WorkspaceService {

  private static final Pattern SAFE_NAME = Pattern.compile("[a-zA-Z0-9._-]+");

  private final Path baseDirectory;
  private final JGitManager gitManager;

  public WorkspaceService(Path baseDirectory, JGitManager gitManager) {
    this.baseDirectory = Arguments.requireNonNull(baseDirectory, "baseDirectory");
    this.gitManager = Arguments.requireNonNull(gitManager, "gitManager");
  }

  public Path prepareWorkspace(String workspaceName, String gitUrl, String ref) {
    String safeName = requireSafeName(workspaceName);
    Arguments.requireNonBlank(gitUrl, "gitUrl");
    Arguments.requireNonBlank(ref, "ref");

    Path workspaceDirectory = baseDirectory.resolve(safeName);
    ensureBaseDirectoryExists();

    if (Files.notExists(workspaceDirectory)) {
      gitManager.cloneRepository(gitUrl, workspaceDirectory);
    } else if (Files.notExists(workspaceDirectory.resolve(".git"))) {
      throw new IllegalStateException("Workspace exists but is not a git repository: " + workspaceDirectory);
    }

    gitManager.fetch(workspaceDirectory);
    gitManager.hardReset(workspaceDirectory, ref);
    gitManager.clean(workspaceDirectory);
    return workspaceDirectory;
  }

  public void deleteWorkspace(String workspaceName) {
    String safeName = requireSafeName(workspaceName);
    Path workspaceDirectory = baseDirectory.resolve(safeName);
    if (Files.notExists(workspaceDirectory)) {
      return;
    }
    deleteRecursively(workspaceDirectory);
  }

  private void ensureBaseDirectoryExists() {
    try {
      Files.createDirectories(baseDirectory);
    } catch (IOException e) {
      throw new WorkspaceException("Failed to create workspace base directory", baseDirectory.toString(), e);
    }
  }

  private static String requireSafeName(String workspaceName) {
    String name = Arguments.requireNonBlank(workspaceName, "workspaceName");
    if (!SAFE_NAME.matcher(name).matches()) {
      throw new IllegalArgumentException("workspaceName contains illegal characters: " + workspaceName);
    }
    return name;
  }

  private static void deleteRecursively(Path root) {
    try {
      Files.walkFileTree(
          root,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
              deleteIfExistsLenient(file);
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
              deleteIfExistsLenient(dir);
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      throw new WorkspaceException("Failed to delete workspace directory", root.toString(), e);
    }
  }

  private static void deleteIfExistsLenient(Path path) throws IOException {
    try {
      Files.deleteIfExists(path);
    } catch (java.nio.file.AccessDeniedException e) {
      try {
        Files.setAttribute(path, "dos:readonly", false);
      } catch (Exception ignored) {
        // ignore
      }
      Files.deleteIfExists(path);
    }
  }
}
