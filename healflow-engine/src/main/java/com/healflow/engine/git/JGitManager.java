package com.healflow.engine.git;

import com.healflow.common.validation.Arguments;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import org.eclipse.jgit.api.CleanCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;

public final class JGitManager {

  private static final PersonIdent DEFAULT_AUTHOR = new PersonIdent("healflow", "healflow@local");

  public void cloneRepository(String gitUrl, Path targetDirectory) {
    Arguments.requireNonBlank(gitUrl, "gitUrl");
    Arguments.requireNonNull(targetDirectory, "targetDirectory");
    if (Files.exists(targetDirectory)) {
      throw new IllegalArgumentException("targetDirectory already exists: " + targetDirectory);
    }
    try (Git git =
        Git.cloneRepository().setURI(gitUrl).setDirectory(targetDirectory.toFile()).call()) {
      // no-op
    } catch (GitAPIException e) {
      throw new GitException("Failed to clone repository", gitUrl, e);
    }
  }

  public void fetch(Path repositoryDirectory) {
    withGit(
        repositoryDirectory,
        git -> {
          git.fetch().setRemote("origin").setRemoveDeletedRefs(true).call();
          return null;
        });
  }

  public void hardReset(Path repositoryDirectory, String ref) {
    Arguments.requireNonBlank(ref, "ref");
    withGit(
        repositoryDirectory,
        git -> {
          git.reset().setMode(ResetType.HARD).setRef(ref).call();
          return null;
        });
  }

  public void clean(Path repositoryDirectory) {
    withGit(
        repositoryDirectory,
        git -> {
          CleanCommand clean = git.clean().setCleanDirectories(true).setForce(true);
          clean.setIgnore(false);
          clean.call();
          return null;
        });
  }

  public void checkout(Path repositoryDirectory, String ref) {
    Arguments.requireNonBlank(ref, "ref");
    withGit(
        repositoryDirectory,
        git -> {
          git.checkout().setName(ref).setForced(true).call();
          return null;
        });
  }

  public void createOrResetBranch(Path repositoryDirectory, String branch, String startPoint) {
    Arguments.requireNonBlank(branch, "branch");
    Arguments.requireNonBlank(startPoint, "startPoint");
    withGit(
        repositoryDirectory,
        git -> {
          Repository repository = git.getRepository();
          Ref existing;
          try {
            existing = repository.exactRef(Constants.R_HEADS + branch);
          } catch (IOException e) {
            throw new GitException("Failed to resolve branch ref", branch, e);
          }
          git.checkout().setName(branch).setCreateBranch(existing == null).setForced(true).call();
          git.reset().setMode(ResetType.HARD).setRef(startPoint).call();
          return null;
        });
  }

  public String commitAll(Path repositoryDirectory, String message) {
    Arguments.requireNonBlank(message, "message");
    return withGit(
        repositoryDirectory,
        git -> {
          git.add().addFilepattern(".").call();
          git.add().addFilepattern(".").setUpdate(true).call();
          git.commit().setMessage(message).setAuthor(DEFAULT_AUTHOR).setCommitter(DEFAULT_AUTHOR).call();
          try {
            return git.getRepository().resolve(Constants.HEAD).name();
          } catch (IOException e) {
            throw new GitException("Failed to resolve HEAD", repositoryDirectory.toString(), e);
          }
        });
  }

  public String headCommit(Path repositoryDirectory) {
    return withGit(
        repositoryDirectory,
        git -> {
          Repository repository = git.getRepository();
          try {
            return repository.resolve(Constants.HEAD).name();
          } catch (IOException e) {
            throw new GitException("Failed to resolve HEAD", repositoryDirectory.toString(), e);
          }
        });
  }

  public String currentBranch(Path repositoryDirectory) {
    return withGit(
        repositoryDirectory,
        git -> {
          try {
            return git.getRepository().getBranch();
          } catch (IOException e) {
            throw new GitException("Failed to resolve branch", repositoryDirectory.toString(), e);
          }
        });
  }

  public void push(Path repositoryDirectory) {
    withGit(
        repositoryDirectory,
        git -> {
          String fullBranch;
          try {
            fullBranch = git.getRepository().getFullBranch();
          } catch (IOException e) {
            throw new GitException("Failed to resolve HEAD ref", repositoryDirectory.toString(), e);
          }
          if (Constants.HEAD.equals(fullBranch) || fullBranch.matches("[0-9a-fA-F]{40}")) {
            throw new GitException("Cannot push detached HEAD", repositoryDirectory.toString());
          }
          String branch = Repository.shortenRefName(fullBranch);
          RefSpec refSpec =
              new RefSpec(Constants.R_HEADS + branch + ":" + Constants.R_HEADS + branch);
          Iterable<PushResult> results =
              git.push().setRemote("origin").setRefSpecs(refSpec).call();
          validatePush(results);
          return null;
        });
  }

  private static void validatePush(Iterable<PushResult> results) {
    for (PushResult result : results) {
      Collection<org.eclipse.jgit.transport.RemoteRefUpdate> updates = result.getRemoteUpdates();
      for (org.eclipse.jgit.transport.RemoteRefUpdate update : updates) {
        org.eclipse.jgit.transport.RemoteRefUpdate.Status status = update.getStatus();
        if (status != org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK
            && status != org.eclipse.jgit.transport.RemoteRefUpdate.Status.UP_TO_DATE) {
          throw new GitException(
              "Push rejected", update.getRemoteName() + " status=" + status + " message=" + update.getMessage());
        }
      }
    }
  }

  private static <T> T withGit(Path repositoryDirectory, GitCallback<T> callback) {
    Arguments.requireNonNull(repositoryDirectory, "repositoryDirectory");
    try (Git git = Git.open(repositoryDirectory.toFile())) {
      return callback.apply(git);
    } catch (IOException e) {
      throw new GitException("Failed to open repository", repositoryDirectory.toString(), e);
    } catch (GitAPIException e) {
      throw new GitException("Git operation failed", repositoryDirectory.toString(), e);
    }
  }

  @FunctionalInterface
  private interface GitCallback<T> {
    T apply(Git git) throws GitAPIException;
  }
}
