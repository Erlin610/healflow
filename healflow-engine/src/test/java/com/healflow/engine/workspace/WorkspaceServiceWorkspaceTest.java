package com.healflow.engine.workspace;

import static org.junit.jupiter.api.Assertions.*;

import com.healflow.engine.git.JGitManager;
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

class WorkspaceServiceWorkspaceTest {

  @Test
  void preparesAndRefreshesWorkspace(@TempDir Path tempDir) throws Exception {
    Path originBare = tempDir.resolve("origin.git");
    Files.createDirectories(originBare);
    try (Git ignored = Git.init().setBare(true).setDirectory(originBare.toFile()).call()) {}

    PersonIdent author = new PersonIdent("Test User", "test@example.com");

    String commit1;
    String commit2;
    try (Git seed = Git.init().setInitialBranch("main").setDirectory(tempDir.resolve("seed").toFile()).call()) {
      Path workTree = seed.getRepository().getWorkTree().toPath();

      Files.writeString(workTree.resolve("version.txt"), "v1");
      seed.add().addFilepattern(".").call();
      seed.commit().setMessage("v1").setAuthor(author).setCommitter(author).call();
      commit1 = seed.getRepository().resolve(Constants.HEAD).name();

      seed.remoteAdd().setName("origin").setUri(new URIish(originBare.toUri().toString())).call();
      seed.push()
          .setRemote("origin")
          .setRefSpecs(new RefSpec(Constants.R_HEADS + "main:" + Constants.R_HEADS + "main"))
          .call();
      pointHeadToMain(originBare);

      Files.writeString(workTree.resolve("version.txt"), "v2");
      seed.add().addFilepattern(".").call();
      seed.commit().setMessage("v2").setAuthor(author).setCommitter(author).call();
      commit2 = seed.getRepository().resolve(Constants.HEAD).name();
      seed.push()
          .setRemote("origin")
          .setRefSpecs(new RefSpec(Constants.R_HEADS + "main:" + Constants.R_HEADS + "main"))
          .call();
    }

    WorkspaceService service = new WorkspaceService(tempDir.resolve("workspaces"), new JGitManager());

    String originUrl = originBare.toUri().toString();

    Path ws = service.prepareWorkspace("app-1", originUrl, commit1);
    assertEquals("v1", Files.readString(ws.resolve("version.txt")));

    Files.writeString(ws.resolve("version.txt"), "dirty");
    Files.writeString(ws.resolve("tmp.txt"), "untracked");

    Path ws2 = service.prepareWorkspace("app-1", originUrl, commit2);
    assertEquals(ws, ws2);
    assertEquals("v2", Files.readString(ws.resolve("version.txt")));
    assertFalse(Files.exists(ws.resolve("tmp.txt")));

    service.deleteWorkspace("app-1");
    assertFalse(Files.exists(ws));
  }

  @Test
  void rejectsUnsafeWorkspaceNames(@TempDir Path tempDir) {
    WorkspaceService service = new WorkspaceService(tempDir.resolve("workspaces"), new JGitManager());
    assertThrows(IllegalArgumentException.class, () -> service.prepareWorkspace("../x", "x", "y"));
  }

  @Test
  void rejectsExistingNonGitDirectory(@TempDir Path tempDir) throws Exception {
    Path base = tempDir.resolve("workspaces");
    Path ws = base.resolve("app-1");
    Files.createDirectories(ws);

    WorkspaceService service = new WorkspaceService(base, new JGitManager());
    assertThrows(IllegalStateException.class, () -> service.prepareWorkspace("app-1", "file:///x", "HEAD"));
  }

  @Test
  void deleteWorkspaceIsNoopWhenMissing(@TempDir Path tempDir) {
    WorkspaceService service = new WorkspaceService(tempDir.resolve("workspaces"), new JGitManager());
    assertDoesNotThrow(() -> service.deleteWorkspace("missing"));
  }

  @Test
  void deletesReadOnlyFilesByClearingDosAttribute(@TempDir Path tempDir) throws Exception {
    Path base = tempDir.resolve("workspaces");
    Path ws = base.resolve("app-1");
    Files.createDirectories(ws);
    Path readOnlyFile = ws.resolve("read-only.txt");
    Files.writeString(readOnlyFile, "x");
    Files.setAttribute(readOnlyFile, "dos:readonly", true);

    WorkspaceService service = new WorkspaceService(base, new JGitManager());
    assertDoesNotThrow(() -> service.deleteWorkspace("app-1"));
  }

  private static void pointHeadToMain(Path originBare) throws Exception {
    try (Repository repo =
        new FileRepositoryBuilder().setGitDir(originBare.toFile()).setBare().build()) {
      repo.updateRef(Constants.HEAD).link(Constants.R_HEADS + "main");
    }
  }
}
