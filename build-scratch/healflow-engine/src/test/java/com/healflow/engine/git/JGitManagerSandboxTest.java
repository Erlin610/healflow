package com.healflow.engine.git;

import static org.junit.jupiter.api.Assertions.*;

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

class JGitManagerSandboxTest {

  @Test
  void createsBranchesCommitsAndPushes(@TempDir Path tempDir) throws Exception {
    PersonIdent author = new PersonIdent("Test User", "test@example.com");
    Path originBare = createOriginWithMain(tempDir, author);

    JGitManager manager = new JGitManager();
    Path cloneDir = tempDir.resolve("clone");
    manager.cloneRepository(originBare.toUri().toString(), cloneDir);
    assertTrue(Files.exists(cloneDir.resolve(".git")));

    manager.fetch(cloneDir);
    manager.createOrResetBranch(cloneDir, "feature", "origin/main");
    assertEquals("feature", manager.currentBranch(cloneDir));

    Files.writeString(cloneDir.resolve("version.txt"), "v2");
    Files.writeString(cloneDir.resolve("new.txt"), "new");
    String commitId = manager.commitAll(cloneDir, "update");
    assertEquals(commitId, manager.headCommit(cloneDir));

    manager.push(cloneDir);

    try (Repository repo =
        new FileRepositoryBuilder().setGitDir(originBare.toFile()).setBare().build()) {
      assertEquals(commitId, repo.resolve(Constants.R_HEADS + "feature").name());
    }
  }

  @Test
  void rejectsDetachedHeadPush(@TempDir Path tempDir) throws Exception {
    PersonIdent author = new PersonIdent("Test User", "test@example.com");
    Path originBare = createOriginWithMain(tempDir, author);

    JGitManager manager = new JGitManager();
    Path cloneDir = tempDir.resolve("clone");
    manager.cloneRepository(originBare.toUri().toString(), cloneDir);
    manager.createOrResetBranch(cloneDir, "feature", "origin/main");

    Files.writeString(cloneDir.resolve("version.txt"), "v2");
    String commitId = manager.commitAll(cloneDir, "update");
    manager.checkout(cloneDir, commitId);

    GitException ex = assertThrows(GitException.class, () -> manager.push(cloneDir));
    assertTrue(ex.getMessage().contains("detached"));
  }

  @Test
  void wrapsWhenRepositoryCannotBeOpened(@TempDir Path tempDir) {
    JGitManager manager = new JGitManager();
    GitException ex = assertThrows(GitException.class, () -> manager.fetch(tempDir.resolve("missing")));
    assertTrue(ex.getMessage().contains("open"));
  }

  @Test
  void rejectsCloneWhenTargetAlreadyExists(@TempDir Path tempDir) throws Exception {
    Path existing = tempDir.resolve("existing");
    Files.createDirectories(existing);

    JGitManager manager = new JGitManager();
    assertThrows(
        IllegalArgumentException.class, () -> manager.cloneRepository("file:///does-not-matter", existing));
  }

  private static Path createOriginWithMain(Path tempDir, PersonIdent author) throws Exception {
    Path originBare = tempDir.resolve("origin.git");
    Files.createDirectories(originBare);
    try (Git ignored = Git.init().setBare(true).setDirectory(originBare.toFile()).call()) {}

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

    try (Repository repo =
        new FileRepositoryBuilder().setGitDir(originBare.toFile()).setBare().build()) {
      repo.updateRef(Constants.HEAD).link(Constants.R_HEADS + "main");
    }

    return originBare;
  }
}

