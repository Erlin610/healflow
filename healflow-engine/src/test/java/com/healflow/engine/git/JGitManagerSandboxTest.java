package com.healflow.engine.git;

import static org.junit.jupiter.api.Assertions.*;

import com.healflow.engine.dto.CommitInfo;
import com.healflow.engine.dto.FileDiffStat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JGitManagerTest {

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

    Files.writeString(cloneDir.resolve("debug.log"), "should-not-commit");
    Files.writeString(cloneDir.resolve("scratch.tmp"), "should-not-commit");
    Files.writeString(cloneDir.resolve("version.txt"), "v2");
    Files.writeString(cloneDir.resolve("new.txt"), "new");
    Optional<CommitInfo> commitInfo = manager.commitFix(cloneDir, "INC-1", "NullPointerException");
    assertTrue(commitInfo.isPresent());
    assertFalse(Files.exists(cloneDir.resolve("debug.log")));
    assertFalse(Files.exists(cloneDir.resolve("scratch.tmp")));
    CommitInfo info = commitInfo.orElseThrow();
    assertEquals(info.commitId(), manager.headCommit(cloneDir));
    assertEquals("fix: INC-1 NullPointerException", lastCommitMessage(cloneDir));
    assertEquals("fix: INC-1 NullPointerException", info.commitMessage());
    assertEquals(List.of("new.txt", "version.txt"), info.changedFiles().stream().sorted().toList());
    assertFileDiffStat(info.gitDiff(), "new.txt", 1, 0);
    assertFileDiffStat(info.gitDiff(), "version.txt", 1, 1);

    manager.push(cloneDir, "feature");

    try (Repository repo =
        new FileRepositoryBuilder().setGitDir(originBare.toFile()).setBare().build()) {
      assertEquals(info.commitId(), repo.resolve(Constants.R_HEADS + "feature").name());
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

    GitException ex = assertThrows(GitException.class, () -> manager.push(cloneDir, "feature"));
    assertTrue(ex.getMessage().contains("detached"));
  }

  @Test
  void skipsCommitWhenNothingChanged(@TempDir Path tempDir) throws Exception {
    PersonIdent author = new PersonIdent("Test User", "test@example.com");
    Path originBare = createOriginWithMain(tempDir, author);

    JGitManager manager = new JGitManager();
    Path cloneDir = tempDir.resolve("clone");
    manager.cloneRepository(originBare.toUri().toString(), cloneDir);
    manager.createOrResetBranch(cloneDir, "feature", "origin/main");

    String before = manager.headCommit(cloneDir);
    assertTrue(manager.commitFix(cloneDir, "INC-1", "NullPointerException").isEmpty());
    assertEquals(before, manager.headCommit(cloneDir));
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

  private static String lastCommitMessage(Path repositoryDirectory) throws Exception {
    try (Git git = Git.open(repositoryDirectory.toFile())) {
      RevCommit commit = git.log().setMaxCount(1).call().iterator().next();
      return commit.getFullMessage();
    }
  }

  private static void assertFileDiffStat(
      List<FileDiffStat> stats, String fileName, int addedLines, int deletedLines) {
    FileDiffStat stat =
        stats.stream().filter(entry -> entry.fileName().equals(fileName)).findFirst().orElseThrow();
    assertEquals(addedLines, stat.addedLines());
    assertEquals(deletedLines, stat.deletedLines());
  }
}
