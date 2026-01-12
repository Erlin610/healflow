package com.healflow.engine.git;

import com.healflow.common.validation.Arguments;
import com.healflow.engine.dto.CommitInfo;
import com.healflow.engine.dto.FileDiffStat;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.jgit.api.CleanCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.util.io.DisabledOutputStream;

/**
 * @deprecated Use {@link GitWorkspaceManager} for workspace preparation and checkout.
 */
@Deprecated(since = "1.0.0-SNAPSHOT", forRemoval = true)
public final class JGitManager {

  private static final PersonIdent DEFAULT_AUTHOR = new PersonIdent("healflow", "healflow@local");
  private final String gitToken;

  public JGitManager() {
    this(null);
  }

  public JGitManager(String gitToken) {
    this.gitToken = gitToken;
  }

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

  public Optional<CommitInfo> commitFix(Path repositoryDirectory, String incidentId, String errorType) {
    Arguments.requireNonBlank(incidentId, "incidentId");
    Arguments.requireNonBlank(errorType, "errorType");
    deleteNoiseFiles(repositoryDirectory);
    String message = "fix: " + incidentId.trim() + " " + errorType.trim();
    return withGit(
        repositoryDirectory,
        git -> {
          git.add().addFilepattern(".").call();
          git.add().addFilepattern(".").setUpdate(true).call();
          if (git.status().call().isClean()) {
            return Optional.empty();
          }
          RevCommit commit =
              git.commit().setMessage(message).setAuthor(DEFAULT_AUTHOR).setCommitter(DEFAULT_AUTHOR).call();
          return Optional.of(buildCommitInfo(git.getRepository(), commit));
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
          pushCheckedOutBranch(git, Repository.shortenRefName(fullBranch));
          return null;
        });
  }

  public void push(Path repositoryDirectory, String branch) {
    Arguments.requireNonBlank(branch, "branch");
    String shortBranch = shortenBranch(branch);
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
          String checkedOutBranch = Repository.shortenRefName(fullBranch);
          if (!checkedOutBranch.equals(shortBranch)) {
            throw new GitException(
                "Cannot push non-checked-out branch",
                "checkedOut=" + checkedOutBranch + " requested=" + shortBranch);
          }
          pushCheckedOutBranch(git, shortBranch);
          return null;
        });
  }

  private void pushCheckedOutBranch(Git git, String branch) throws GitAPIException {
    RefSpec refSpec = new RefSpec(Constants.R_HEADS + branch + ":" + Constants.R_HEADS + branch);
    var pushCommand = git.push().setRemote("origin").setRefSpecs(refSpec);
    if (gitToken != null && !gitToken.isBlank()) {
      pushCommand.setCredentialsProvider(
          new org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider(gitToken, ""));
    }
    Iterable<PushResult> results = pushCommand.call();
    validatePush(results);
  }

  private static String shortenBranch(String branch) {
    String trimmed = branch.trim();
    if (trimmed.startsWith(Constants.R_HEADS)) {
      return Repository.shortenRefName(trimmed);
    }
    return trimmed;
  }

  private static void deleteNoiseFiles(Path repositoryDirectory) {
    Path gitDir = repositoryDirectory.resolve(".git").normalize();
    try (Stream<Path> stream = Files.walk(repositoryDirectory)) {
      stream
          .filter(Files::isRegularFile)
          .filter(path -> !path.startsWith(gitDir))
          .filter(JGitManager::isNoiseFile)
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              });
    } catch (UncheckedIOException e) {
      throw new GitException("Failed to delete noise files", repositoryDirectory.toString(), e.getCause());
    } catch (IOException e) {
      throw new GitException("Failed to delete noise files", repositoryDirectory.toString(), e);
    }
  }

  private static boolean isNoiseFile(Path path) {
    String name = path.getFileName().toString().toLowerCase();
    return name.endsWith(".log") || name.endsWith(".tmp");
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

  private static CommitInfo buildCommitInfo(Repository repository, RevCommit commit) {
    try (RevWalk walk = new RevWalk(repository);
        ObjectReader reader = repository.newObjectReader();
        DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
      RevCommit parsedCommit = walk.parseCommit(commit.getId());
      RevCommit parent =
          parsedCommit.getParentCount() > 0
              ? walk.parseCommit(parsedCommit.getParent(0).getId())
              : null;
      formatter.setRepository(repository);
      formatter.setDetectRenames(true);

      AbstractTreeIterator oldTree =
          parent == null ? new EmptyTreeIterator() : treeParser(reader, parent);
      AbstractTreeIterator newTree = treeParser(reader, parsedCommit);
      List<DiffEntry> diffs = formatter.scan(oldTree, newTree);

      List<String> changedFiles = new ArrayList<>();
      List<FileDiffStat> gitDiff = new ArrayList<>();
      for (DiffEntry diff : diffs) {
        String path = resolveDiffPath(diff);
        changedFiles.add(path);
        FileHeader header = formatter.toFileHeader(diff);
        gitDiff.add(toFileDiffStat(path, header.toEditList()));
      }
      return new CommitInfo(parsedCommit.getId().name(), parsedCommit.getFullMessage(), changedFiles, gitDiff);
    } catch (IOException e) {
      throw new GitException("Failed to compute commit info", repository.toString(), e);
    }
  }

  private static CanonicalTreeParser treeParser(ObjectReader reader, RevCommit commit) throws IOException {
    CanonicalTreeParser parser = new CanonicalTreeParser();
    parser.reset(reader, commit.getTree());
    return parser;
  }

  private static String resolveDiffPath(DiffEntry diff) {
    if (DiffEntry.DEV_NULL.equals(diff.getNewPath())) {
      return diff.getOldPath();
    }
    return diff.getNewPath();
  }

  private static FileDiffStat toFileDiffStat(String path, EditList edits) {
    int added = 0;
    int deleted = 0;
    for (Edit edit : edits) {
      switch (edit.getType()) {
        case INSERT:
          added += edit.getLengthB();
          break;
        case DELETE:
          deleted += edit.getLengthA();
          break;
        case REPLACE:
          added += edit.getLengthB();
          deleted += edit.getLengthA();
          break;
        default:
          break;
      }
    }
    return new FileDiffStat(path, added, deleted);
  }
}
