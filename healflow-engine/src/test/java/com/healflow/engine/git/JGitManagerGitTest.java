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

class JGitManagerGitTest {

  @Test
  void clonesCommitsResetsAndPushes(@TempDir Path tempDir) throws Exception {
    Path originBare = tempDir.resolve("origin.git");
    Files.createDirectories(originBare);
    try (Git ignored = Git.init().setBare(true).setDirectory(originBare.toFile()).call()) {}

    PersonIdent author = new PersonIdent("Test User", "test@example.com");
    try (Git seed = Git.init().setInitialBranch("main").setDirectory(tempDir.resolve("seed").toFile()).call()) {
      Files.writeString(seed.getRepository().getWorkTree().toPath().resolve("a.txt"), "one");
      seed.add().addFilepattern(".").call();
      seed.commit().setMessage("init").setAuthor(author).setCommitter(author).call();
      seed.remoteAdd().setName("origin").setUri(new URIish(originBare.toUri().toString())).call();
      seed.push()
          .setRemote("origin")
          .setRefSpecs(new RefSpec(Constants.R_HEADS + "main:" + Constants.R_HEADS + "main"))
          .call();
    }
    pointHeadToMain(originBare);

    JGitManager manager = new JGitManager();

    Path clone = tempDir.resolve("clone");
    manager.cloneRepository(originBare.toUri().toString(), clone);

    String baseCommit = manager.headCommit(clone);
    manager.createOrResetBranch(clone, "feature", Constants.R_REMOTES + "origin/main");
    manager.createOrResetBranch(clone, "feature", Constants.R_REMOTES + "origin/main");
    Files.writeString(clone.resolve("b.txt"), "two");
    String featureCommit = manager.commitAll(clone, "add b");
    assertNotEquals(baseCommit, featureCommit);

    Path untracked = clone.resolve("untracked.txt");
    Files.writeString(untracked, "tmp");
    assertTrue(Files.exists(untracked));
    manager.clean(clone);
    assertFalse(Files.exists(untracked));

    manager.hardReset(clone, baseCommit);
    assertEquals(baseCommit, manager.headCommit(clone));
    manager.checkout(clone, "feature");
    assertEquals("feature", manager.currentBranch(clone));
    manager.push(clone);

    try (Repository repo =
        new FileRepositoryBuilder().setGitDir(originBare.toFile()).setBare().build()) {
      assertNotNull(repo.exactRef(Constants.R_HEADS + "feature"));
    }
  }

  @Test
  void refusesToPushDetachedHead(@TempDir Path tempDir) throws Exception {
    Path originBare = tempDir.resolve("origin.git");
    Files.createDirectories(originBare);
    try (Git ignored = Git.init().setBare(true).setDirectory(originBare.toFile()).call()) {}

    PersonIdent author = new PersonIdent("Test User", "test@example.com");
    try (Git seed = Git.init().setInitialBranch("main").setDirectory(tempDir.resolve("seed").toFile()).call()) {
      Files.writeString(seed.getRepository().getWorkTree().toPath().resolve("a.txt"), "one");
      seed.add().addFilepattern(".").call();
      seed.commit().setMessage("init").setAuthor(author).setCommitter(author).call();
      seed.remoteAdd().setName("origin").setUri(new URIish(originBare.toUri().toString())).call();
      seed.push()
          .setRemote("origin")
          .setRefSpecs(new RefSpec(Constants.R_HEADS + "main:" + Constants.R_HEADS + "main"))
          .call();
    }
    pointHeadToMain(originBare);

    JGitManager manager = new JGitManager();
    Path clone = tempDir.resolve("clone");
    manager.cloneRepository(originBare.toUri().toString(), clone);

    String head = manager.headCommit(clone);
    manager.checkout(clone, head);

    GitException ex = assertThrows(GitException.class, () -> manager.push(clone));
    assertTrue(ex.getMessage().toLowerCase().contains("detached"));
  }

  @Test
  void failsWhenRepositoryCannotBeOpened(@TempDir Path tempDir) {
    JGitManager manager = new JGitManager();
    GitException ex = assertThrows(GitException.class, () -> manager.fetch(tempDir.resolve("missing")));
    assertNotNull(ex.getCause());
  }

  @Test
  void failsWhenCloneUrlIsInvalid(@TempDir Path tempDir) {
    JGitManager manager = new JGitManager();
    GitException ex =
        assertThrows(
            GitException.class,
            () -> manager.cloneRepository("file:///this/does/not/exist", tempDir.resolve("clone")));
    assertNotNull(ex.getCause());
  }

  private static void pointHeadToMain(Path originBare) throws Exception {
    try (Repository repo =
        new FileRepositoryBuilder().setGitDir(originBare.toFile()).setBare().build()) {
      repo.updateRef(Constants.HEAD).link(Constants.R_HEADS + "main");
    }
  }
}
