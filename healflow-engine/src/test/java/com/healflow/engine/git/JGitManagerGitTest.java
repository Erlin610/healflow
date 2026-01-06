package com.healflow.engine.git;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitWorkspaceManagerSandboxTest {

  @Test
  void preparesAndRefreshesWorkspace(@TempDir Path tempDir) throws Exception {
    Path originBare = tempDir.resolve("origin.git");
    Files.createDirectories(originBare);
    try (Git ignored = Git.init().setBare(true).setDirectory(originBare.toFile()).call()) {}

    PersonIdent author = new PersonIdent("Test User", "test@example.com");

    try (Git seed =
        Git.init().setInitialBranch("main").setDirectory(tempDir.resolve("seed").toFile()).call()) {
      Path workTree = seed.getRepository().getWorkTree().toPath();

      Files.writeString(workTree.resolve("version.txt"), "v1");
      seed.add().addFilepattern(".").call();
      seed.commit().setMessage("v1").setAuthor(author).setCommitter(author).call();

      seed.remoteAdd().setName("origin").setUri(new URIish(originBare.toUri().toString())).call();
      seed.push()
          .setRemote("origin")
          .setRefSpecs(new RefSpec(Constants.R_HEADS + "main:" + Constants.R_HEADS + "main"))
          .call();
      pointHeadToMain(originBare);

      GitWorkspaceManager manager = newManager(tempDir.resolve("workspaces"));
      String originUrl = originBare.toUri().toString();
      Path ws = manager.prepareWorkspace("app-1", originUrl, "main");
      assertEquals("v1", Files.readString(ws.resolve("version.txt")));

      Files.writeString(ws.resolve("tmp.txt"), "untracked");

      Files.writeString(workTree.resolve("version.txt"), "v2");
      seed.add().addFilepattern(".").call();
      seed.commit().setMessage("v2").setAuthor(author).setCommitter(author).call();
      seed.push()
          .setRemote("origin")
          .setRefSpecs(new RefSpec(Constants.R_HEADS + "main:" + Constants.R_HEADS + "main"))
          .call();

      Path ws2 = manager.prepareWorkspace("app-1", originUrl, "main");
      assertEquals(ws, ws2);
      assertEquals("v2", Files.readString(ws.resolve("version.txt")));
      assertTrue(Files.exists(ws.resolve("tmp.txt")));
    }
  }

  @Test
  void defaultsToMainWhenBranchBlank(@TempDir Path tempDir) throws Exception {
    Path originBare = tempDir.resolve("origin.git");
    Files.createDirectories(originBare);
    try (Git ignored = Git.init().setBare(true).setDirectory(originBare.toFile()).call()) {}

    PersonIdent author = new PersonIdent("Test User", "test@example.com");

    try (Git seed =
        Git.init().setInitialBranch("main").setDirectory(tempDir.resolve("seed").toFile()).call()) {
      Path workTree = seed.getRepository().getWorkTree().toPath();
      Files.writeString(workTree.resolve("version.txt"), "v1");
      seed.add().addFilepattern(".").call();
      seed.commit().setMessage("v1").setAuthor(author).setCommitter(author).call();

      seed.remoteAdd().setName("origin").setUri(new URIish(originBare.toUri().toString())).call();
      seed.push()
          .setRemote("origin")
          .setRefSpecs(new RefSpec(Constants.R_HEADS + "main:" + Constants.R_HEADS + "main"))
          .call();
    }
    pointHeadToMain(originBare);

    GitWorkspaceManager manager = newManager(tempDir.resolve("workspaces"));
    Path ws = manager.prepareWorkspace("app-1", originBare.toUri().toString(), null);
    assertEquals("v1", Files.readString(ws.resolve("version.txt")));
  }

  @Test
  void wrapsFailures(@TempDir Path tempDir) throws Exception {
    GitWorkspaceManager manager = newManager(tempDir.resolve("workspaces"));
    RuntimeException ex =
        assertThrows(
            RuntimeException.class,
            () -> manager.prepareWorkspace("app-1", "file:///this/does/not/exist", null));
    assertNotNull(ex.getCause());
  }

  private static void pointHeadToMain(Path originBare) throws Exception {
    try (Repository repo =
        new FileRepositoryBuilder().setGitDir(originBare.toFile()).setBare().build()) {
      repo.updateRef(Constants.HEAD).link(Constants.R_HEADS + "main");
    }
  }

  private static GitWorkspaceManager newManager(Path workspaceRoot) throws Exception {
    GitWorkspaceManager manager = new GitWorkspaceManager();
    Field workspaceRootField = GitWorkspaceManager.class.getDeclaredField("workspaceRoot");
    workspaceRootField.setAccessible(true);
    workspaceRootField.set(manager, workspaceRoot.toString());
    return manager;
  }
}
