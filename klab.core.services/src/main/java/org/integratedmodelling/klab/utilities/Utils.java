package org.integratedmodelling.klab.utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import maven.fetcher.MavenFetchRequest;
import maven.fetcher.MavenFetcher;
import org.apache.commons.io.FileUtils;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositoryCache;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.integratedmodelling.common.authentication.Authentication;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.authentication.ExternalAuthenticationCredentials;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.view.UI;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

public class Utils extends org.integratedmodelling.common.utils.Utils {

  public static void main(String[] args) {

    /**
     * Should: If no SNAPSHOT in the version, check local repo, take for good if it's there, then
     * retrieve all available versions and see if there's anything new. Otherwise download anyway
     * and reload if the size or hash is different.
     *
     * <p>ALSO: just upload the base jar and the runtime/resource can check if there are
     * dependencies that are not in the classpath. Definitely don't use shading if the plugin
     * requires no additional jars vs. the distribution. The deps come along with the result. OR the
     * plugin could declare the deps it needs explicitly and we could recurse.
     */
    System.out.println("Fetching....");

    var result =
        Maven.mavenFetcher.fetchArtifacts(
            new MavenFetchRequest(
                "org.integratedmodelling:klab.component.generators:1.0-SNAPSHOT"));

    result
        .artifacts()
        .peek(
            fetchedArtifact ->
                System.out.println(
                    "CIUCCIA " + fetchedArtifact.coordinates() + ": " + fetchedArtifact.path()))
        .toList();

    System.out.println(
        "FETCHATO: MO' PROVO CON L'ALTRO: "
            + Maven.synchronizeArtifact(
                "org" + ".integratedmodelling", "klab.component.generators", "1.0-SNAPSHOT", true));
  }

  /** Functions to access Maven artifacts */
  public static class Maven {

    private static final MavenFetcher mavenFetcher =
        new MavenFetcher()
            .localRepositoryPath(System.getProperty("user.home") + "/.m2/repository")
            .addRemoteRepository(
                "ossrh", "https://oss.sonatype.org/content/repositories/snapshots");

    public static File getLocalJarArtifact(
        String mavenGroupId, String mavenArtifactId, String version) {

      var artifact = new DefaultArtifact(mavenGroupId + ":" + mavenArtifactId + ":" + version);
      ArtifactRequest request = new ArtifactRequest();
      request.setArtifact(artifact);
      var session = Maven.buildSession(Maven.DEFAULT_REPO_LOCAL);

      ArtifactResult zoaz = null;
      try {
        zoaz = Maven.system.resolveArtifact(session, request);
      } catch (ArtifactResolutionException e) {
        throw new RuntimeException(e);
      }

      if (zoaz.isResolved() && zoaz.getLocalArtifactResult().isAvailable()) {
        return zoaz.getLocalArtifactResult().getFile();
      }

      return null;
    }

    /**
     * True if the artifact is not in the local repository or it is there with a different hash.
     * Should work with SNAPSHOT artifacts to determine if there is a new build available.
     *
     * <p>TODO pass a local file and verify vs the hash. Should add the hash to the repo
     *
     * @param mavenGroupId
     * @param mavenArtifactId
     * @param version
     */
    public static boolean needsUpdate(String mavenGroupId, String mavenArtifactId, String version) {

      if (version.contains("SNAPSHOT")) {
        // TODO if version exists in repo, check local hash vs. remote; if different, return true.
        // At the moment whatever is
        //  in the local repo will do.
        //                return true;
      }
      return getLocalJarArtifact(mavenGroupId, mavenArtifactId, version) == null;
    }

    public static File synchronizeArtifact(
        String mavenGroupId, String mavenArtifactId, String version, boolean verifySignature) {
      if (needsUpdate(mavenGroupId, mavenArtifactId, version)) {
        var request = new MavenFetchRequest(mavenGroupId + ":" + mavenArtifactId + ":" + version);
        var result = mavenFetcher.fetchArtifacts(request);
        if (result.artifacts().findAny().isPresent()) {
          return result.artifacts().toList().getFirst().path().toFile();
        }
      }
      return getLocalJarArtifact(mavenGroupId, mavenArtifactId, version);
    }

    private static final String DEFAULT_REPO_LOCAL =
        String.format("%s/.m2/repository", System.getProperty("user.home"));
    private static final RemoteRepository DEFAULT_REPO_REMOTE =
        new RemoteRepository.Builder("central", "default", "https://repo1.maven.org/maven2/")
            .build();
    private static final Set<String> DEFAULT_SCOPES = Set.of(JavaScopes.RUNTIME);

    private static final RepositorySystem system;

    static {
      var locator = MavenRepositorySystemUtils.newServiceLocator();

      locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
      locator.addService(TransporterFactory.class, FileTransporterFactory.class);
      locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
      //            locator.addService(TransporterFactory.class, ClasspathTransporterFactory.class);

      system = locator.getService(RepositorySystem.class);
    }

    public static List<String> resolve(String... coords)
        throws DependencyResolutionException, NoLocalRepositoryManagerException {
      return resolve(
          List.of(coords), DEFAULT_SCOPES, DEFAULT_REPO_LOCAL, List.of(DEFAULT_REPO_REMOTE));
    }

    /**
     * resolve
     *
     * @param coords eg: org.apache.logging.log4j:log4j-core:2.19.0
     * @param scopes default to DEFAULT_SCOPES if null or empty
     * @param localRepo default to DEFAULT_REPO_LOCAL if null
     * @param remoteRepos default to DEFAULT_REPO_REMOTE if null or empty
     * @return jar files absolute path
     */
    public static List<String> resolve(
        List<String> coords,
        Set<String> scopes,
        String localRepo,
        List<RemoteRepository> remoteRepos)
        throws DependencyResolutionException, NoLocalRepositoryManagerException {
      if (coords.isEmpty()) return java.util.Collections.emptyList();
      if (scopes == null || scopes.isEmpty()) scopes = DEFAULT_SCOPES;
      if (localRepo == null) localRepo = DEFAULT_REPO_LOCAL;
      if (remoteRepos == null || remoteRepos.isEmpty()) remoteRepos = List.of(DEFAULT_REPO_REMOTE);

      RepositorySystemSession session = buildSession(localRepo);

      List<Dependency> dependencies =
          coords.stream()
              .map(DefaultArtifact::new)
              .map(artifact -> new Dependency(artifact, null))
              .toList();
      var collectRequest = new CollectRequest(dependencies, null, remoteRepos);

      var request =
          new DependencyRequest(collectRequest, DependencyFilterUtils.classpathFilter(scopes));
      DependencyResult result = system.resolveDependencies(session, request);

      var nodeListGenerator = new PreorderNodeListGenerator();
      result.getRoot().accept(nodeListGenerator);

      return nodeListGenerator.getFiles().stream().map(File::getAbsolutePath).toList();
    }

    private static RepositorySystemSession buildSession(String localRepo) {
      var session = MavenRepositorySystemUtils.newSession();
      session.setLocalRepositoryManager(
          system.newLocalRepositoryManager(session, new LocalRepository(localRepo)));
      session.setCache(new DefaultRepositoryCache());
      return session;
    }

    public static void main(String[] args)
        throws DependencyResolutionException, NoLocalRepositoryManagerException {
      test();
    }

    public static void test()
        throws DependencyResolutionException, NoLocalRepositoryManagerException {
      String localRepo = "out";
      RemoteRepository aliRepo =
          new RemoteRepository.Builder(
                  "aliyun", "default", "https://maven.aliyun.com/repository/central")
              .build();
      List<RemoteRepository> remotes = List.of(DEFAULT_REPO_REMOTE, aliRepo);
      var coords = List.of("org.apache.logging.log4j:log4j-core:2.20.0");
      List<String> jars = resolve(coords, null, localRepo, remotes);
      System.out.printf(">>>>>> jars: %s%n", jars);
    }
  }

  public static class Classpath {

    /**
     * Extract the OWL assets in the classpath (under /knowledge/**) to the specified filesystem
     * directory.
     *
     * @param destinationDirectory
     * @throws IOException
     */
    public static void extractKnowledgeFromClasspath(File destinationDirectory) {
      try {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("/knowledge/**");
        for (Resource resource : resources) {

          String path = null;
          if (resource instanceof FileSystemResource) {
            path = ((FileSystemResource) resource).getPath();
          } else if (resource instanceof ClassPathResource) {
            path = ((ClassPathResource) resource).getPath();
          }
          if (path == null) {
            throw new KlabIOException("internal: cannot establish path for resource " + resource);
          }

          if (!path.endsWith("owl")) {
            continue;
          }

          String filePath = path.substring(path.indexOf("knowledge/") + "knowledge/".length());

          int pind = filePath.lastIndexOf('/');
          if (pind >= 0) {
            String fileDir = filePath.substring(0, pind);
            File destDir = new File(destinationDirectory + File.separator + fileDir);
            destDir.mkdirs();
          }
          File dest = new File(destinationDirectory + File.separator + filePath);
          InputStream is = resource.getInputStream();
          FileUtils.copyInputStreamToFile(is, dest);
          is.close();
        }
      } catch (IOException ex) {
        throw new KlabIOException(ex);
      }
    }

    /**
     * Only works for a flat hierarchy!
     *
     * @param resourcePattern
     * @param destinationDirectory
     */
    public static void extractResourcesFromClasspath(
        String resourcePattern, File destinationDirectory) {

      try {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(resourcePattern);
        for (Resource resource : resources) {

          String path = null;
          if (resource instanceof FileSystemResource) {
            path = ((FileSystemResource) resource).getPath();
          } else if (resource instanceof ClassPathResource) {
            path = ((ClassPathResource) resource).getPath();
          }
          if (path == null) {
            throw new KlabIOException("internal: cannot establish path for resource " + resource);
          }
          String fileName = org.integratedmodelling.klab.api.utils.Utils.Files.getFileName(path);
          File dest = new File(destinationDirectory + File.separator + fileName);
          InputStream is = resource.getInputStream();
          FileUtils.copyInputStreamToFile(is, dest);
          is.close();
        }
      } catch (IOException ex) {
        throw new KlabIOException(ex);
      }
    }
  }

  public static class Files extends org.integratedmodelling.klab.api.utils.Utils.Files {

    public static final Set<String> JAVA_ARCHIVE_EXTENSIONS = Set.of("zip", "jar");

    public static String hash(File file) {
      String ret = "";
      try (FileInputStream fis = new FileInputStream(file)) {
        ret = org.apache.commons.codec.digest.DigestUtils.md5Hex(fis);
      } catch (IOException e) {
        throw new KlabIOException(e);
      }
      return ret;
    }

    public static void deleteDirectory(File pdir) {
      try {
        FileUtils.deleteDirectory(pdir);
      } catch (IOException e) {
        throw new KlabIOException(e);
      }
    }

    public static void touch(File file) {
      try {
        FileUtils.touch(file);
      } catch (IOException e) {
        throw new KlabIOException(e);
      }
    }

    public static boolean deleteQuietly(File pdir) {
      return FileUtils.deleteQuietly(pdir);
    }

    public static void copyDirectory(File directory, File backupDir) {
      try {
        FileUtils.copyDirectory(directory, backupDir);
      } catch (IOException e) {
        throw new KlabIOException(e);
      }
    }

    //        public static void writeStringToFile(String string, File file) {
    //            try {
    //                FileUtils.write(file, string, StandardCharsets.UTF_8);
    //            } catch (IOException e) {
    //                throw new KlabIOException(e);
    //            }
    //        }

  }

  public static class Git {

    public static final String MAIN_BRANCH = "master";

    /**
     * Compound repository operations (as implemented in Utils.Git in the common package) return one
     * of these, which contains notifications (they should be checked for errors before anything
     * else is done) and the relative paths that were affected. When changes affect a {@link
     * org.integratedmodelling.klab.api.knowledge.organization.Workspace}, they can be converted
     * into {@link org.integratedmodelling.klab.api.services.resources.ResourceSet} by a resources
     * server that knows mutual dependencies.
     *
     * <p>FIXME these are often wrong. Must return: for pull: all changes w.r.t. head before pull (I
     * think it does id) for commit: only those changes that come from the fetch before commit
     * reset: what was reset in head + whatever comes from the pull after
     *
     * <p>All the changed paths should be reported in an INFO notification
     */
    public static class Modifications {

      private String repositoryName;

      private List<String> addedPaths = new ArrayList<>();
      private List<String> removedPaths = new ArrayList<>();
      private List<String> modifiedPaths = new ArrayList<>();
      private List<Notification> notifications = new ArrayList<>();

      public List<String> getAddedPaths() {
        return addedPaths;
      }

      public void setAddedPaths(List<String> addedPaths) {
        this.addedPaths = addedPaths;
      }

      public List<String> getRemovedPaths() {
        return removedPaths;
      }

      public void setRemovedPaths(List<String> removedPaths) {
        this.removedPaths = removedPaths;
      }

      public List<String> getModifiedPaths() {
        return modifiedPaths;
      }

      public void setModifiedPaths(List<String> modifiedPaths) {
        this.modifiedPaths = modifiedPaths;
      }

      public List<Notification> getNotifications() {
        return notifications;
      }

      public void setNotifications(List<Notification> notifications) {
        this.notifications = notifications;
      }

      public String getRepositoryName() {
        return repositoryName;
      }

      public void setRepositoryName(String repositoryName) {
        this.repositoryName = repositoryName;
      }
    }

    /**
     * Perform a fetch, if no issues do a merge, then commit any changes and push to origin. Use any
     * credentials installed for the origin repository.
     *
     * @param localRepository
     * @return Modifications record. Empty notifications means all OK. May have no errors but
     *     warnings, no info. Use {@link Notifications#hasErrors(Collection)} on the notifications
     *     element to check.
     */
    public static Modifications fetchCommitAndPush(
        File localRepository, String commitMessage, Scope scope) {

      Modifications ret = new Modifications();

      ret.setRepositoryName(Files.getFileBaseName(localRepository));

      try (var repo = new FileRepository(new File(localRepository + File.separator + ".git"))) {

        try (var git = new org.eclipse.jgit.api.Git(repo)) {

          ObjectId oldHead = repo.resolve("HEAD^{tree}");

          PullCommand pullCmd = git.pull();
          pullCmd.setCredentialsProvider(getCredentialsProvider(git, scope));
          PullResult result = pullCmd.call();
          if (result != null && result.isSuccessful()) {
            var messages = result.getFetchResult().getMessages();
            if (messages != null && !messages.isEmpty()) {
              ret.getNotifications().add(Notification.create(messages, Notification.Level.Info));
            }
            if (result.getMergeResult().getConflicts() != null
                && !result.getMergeResult().getConflicts().isEmpty()) {
              ret.getNotifications()
                  .add(
                      Notification.error(
                          "Conflicts during merge of "
                              + Strings.join(result.getMergeResult().getConflicts().keySet(), ", "),
                          UI.Interactivity.DISPLAY));
            } else {

              // commit locally
              try {

                var commit = git.commit().setMessage(commitMessage);
                commit.setAll(true);
                commit.setCredentialsProvider(getCredentialsProvider(git, scope));
                var commitResult = commit.call();
                PushCommand pushCommand = git.push();
                pushCommand.setRemote("origin");
                pushCommand.setCredentialsProvider(getCredentialsProvider(git, scope));
                pushCommand.call();
              } catch (GitAPIException ex) {
                ret.getNotifications().add(Notification.error(ex, UI.Interactivity.DISPLAY));
              }
            }
          }
          compileDiff(repo, git, oldHead, ret);
        }
      } catch (Exception e) {
        ret.getNotifications().add(Notification.error(e, UI.Interactivity.DISPLAY));
      }
      return ret;
    }

    private static CredentialsProvider getCredentialsProvider(
        org.eclipse.jgit.api.Git git, Scope scope) {

      CredentialsProvider ret = null;
      ExternalAuthenticationCredentials credentials = null;
      try {
        for (RemoteConfig remoteConfig : git.remoteList().call()) {
          if ("origin".equals(remoteConfig.getName()) && !remoteConfig.getURIs().isEmpty()) {
            for (var uri : remoteConfig.getURIs()) {
              credentials = Authentication.INSTANCE.getCredentials(uri.toString(), scope);
              if (credentials != null) {
                break;
              }
            }
          }
        }
      } catch (GitAPIException e) {
        throw new RuntimeException(e);
      }

      return getCredentialsProvider(credentials);
    }

    public static CredentialsProvider getCredentialsProvider(
        ExternalAuthenticationCredentials credentials) {

      CredentialsProvider ret = null;
      if (credentials != null) {
        ret =
            switch (credentials.getScheme()) {
              case ExternalAuthenticationCredentials.BASIC ->
                  new UsernamePasswordCredentialsProvider(
                      credentials.getCredentials().get(0), credentials.getCredentials().get(1));
              case ExternalAuthenticationCredentials.KEY -> null;
              default -> null;
            };

        // check if we need to add a transport mechanism instead
        if (ret == null && ExternalAuthenticationCredentials.SSH.equals(credentials.getScheme())) {

          //                    SshSessionFactory sshSessionFactory = new
          //                    JschConfigSessionFactory() {
          //                        @Override
          //                        protected void configure(Host host, Session session) {
          //                            // do nothing
          //                        }
          //
          //                        @Override
          //                        protected JSch createDefaultJSch(FS fs) throws
          //                        JSchException {
          //                            JSch defaultJSch = super.createDefaultJSch(fs);
          //                            defaultJSch.addIdentity("c:/path/to/my/private_key");
          //
          //                            // if key is protected with passphrase
          //                            // defaultJSch.addIdentity("c:/path/to/my/private_key",
          //                            "my_passphrase");
          //
          //                            return defaultJSch;
          //                        }
          //                    };
          //
          //                    command.setTransportConfigCallback(transport -> {
          //                        SshTransport sshTransport = (SshTransport) transport;
          //                        sshTransport.setSshSessionFactory(sshSessionFactory);
          //                    });
        }
      }
      return ret;
    }

    /**
     * Perform a safe pull operations from origin, using any installed credentials.
     *
     * @param localRepository
     * @return Modifications record. Empty notifications means all OK. May have no errors but
     *     warnings, no info. Use {@link Notifications#hasErrors(Collection)} on the notifications
     *     element to check.
     */
    public static Modifications fetchAndMerge(File localRepository, Scope scope) {

      Modifications ret = new Modifications();

      ret.setRepositoryName(Files.getFileBaseName(localRepository));

      try (var repo = new FileRepository(new File(localRepository + File.separator + ".git"))) {
        try (var git = new org.eclipse.jgit.api.Git(repo)) {

          ObjectId oldHead = repo.resolve("HEAD^{tree}");

          PullCommand pullCmd = git.pull();
          pullCmd.setCredentialsProvider(getCredentialsProvider(git, scope));
          PullResult result = pullCmd.call();
          if (result != null && result.isSuccessful()) {
            var messages = result.getFetchResult().getMessages();
            if (messages != null && !messages.isEmpty()) {
              ret.getNotifications().add(Notification.create(messages, Notification.Level.Info));
            }
            if (result.getMergeResult().getConflicts() != null
                && !result.getMergeResult().getConflicts().isEmpty()) {
              ret.getNotifications()
                  .add(
                      Notification.error(
                          "Conflicts during merge of "
                              + Strings.join(result.getMergeResult().getConflicts().keySet(), ", "),
                          UI.Interactivity.DISPLAY));
            } else {
              compileDiff(repo, git, oldHead, ret);
            }
          } else {
            ret.getNotifications()
                .add(
                    Notification.error(
                        "Pull from default remote of "
                            + "repository "
                            + repo.getIdentifier()
                            + " unsuccessful",
                        UI.Interactivity.DISPLAY));
          }

          /*
          report changes
           */
        }
      } catch (CheckoutConflictException c) {

        StringBuilder message =
            new StringBuilder(
                "Conflicts exist between the local version "
                    + "and the on in the published repository.\nPlease resolve the conflicts using "
                    + "Git in"
                    + " the "
                    + "repository located at\n"
                    + localRepository.getAbsolutePath()
                    + "\n\nThe "
                    + "conflicting paths are:");

        for (var conflict : c.getConflictingPaths()) {
          message.append("\n   ").append(conflict);
        }

        ret.getNotifications()
            .add(Notification.error(message.toString(), UI.Interactivity.DISPLAY));

      } catch (Throwable e) {
        ret.getNotifications().add(Notification.create(e));
      }

      return ret;
    }

    private static void compileDiff(
        Repository repository, org.eclipse.jgit.api.Git git, ObjectId oldHead, Modifications ret) {

      try {
        var head = repository.resolve("HEAD^{tree}");
        ObjectReader reader = repository.newObjectReader();
        CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
        oldTreeIter.reset(reader, oldHead);
        CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
        newTreeIter.reset(reader, head);
        for (var diff : git.diff().setNewTree(newTreeIter).setOldTree(oldTreeIter).call()) {
          switch (diff.getChangeType()) {
            case ADD -> {
              ret.getAddedPaths().add(diff.getNewPath());
            }
            case MODIFY -> {
              ret.getModifiedPaths().add(diff.getOldPath());
            }
            case DELETE -> {
              ret.getRemovedPaths().add(diff.getOldPath());
            }
            case COPY -> {
              ret.getAddedPaths().add(diff.getNewPath());
            }
          }
        }
      } catch (Exception e) {
        ret.getNotifications().add(Notification.create(e));
      }
    }

    public static Modifications mergeChangesFrom(File localRepository, String branch) {
      // TODO ziobue
      return null;
    }

    /**
     * Commit any current changes before switching to the passed branch (either remote or local). If
     * the branch is new, create it based on current and instrument it for push/pull to/from origin.
     *
     * @param localRepository
     * @param branch
     * @return Modifications record. Empty notifications means all OK. May have no errors but
     *     warnings, no info. Use {@link Notifications#hasErrors(Collection)} on the notifications
     *     element to check.
     */
    public static Modifications commitAndSwitch(File localRepository, String branch) {

      Modifications ret = new Modifications();

      ret.setRepositoryName(Files.getFileBaseName(localRepository));

      try (var repo = new FileRepository(new File(localRepository + File.separator + ".git"))) {
        try (var git = new org.eclipse.jgit.api.Git(repo)) {
          ObjectId oldHead = repo.resolve("HEAD^{tree}");

          // TODO

          compileDiff(repo, git, oldHead, ret);
        }
      } catch (IOException e) {
        ret.getNotifications().add(Notification.create(e));
      }

      return ret;
    }

    /**
     * Perform a hard reset, bringing the current repository to the last commit.
     *
     * @param localRepository
     * @return Modifications record. Empty notifications means all OK. May have no errors but
     *     warnings, no info. Use {@link Notifications#hasErrors(Collection)} on the notifications
     *     element to check.
     */
    public static Modifications hardReset(File localRepository) {

      Modifications ret = new Modifications();

      ret.setRepositoryName(Files.getFileBaseName(localRepository));

      try (var repo = new FileRepository(new File(localRepository + File.separator + ".git"))) {
        try (var git = new org.eclipse.jgit.api.Git(repo)) {
          ObjectId oldHead = repo.resolve("HEAD^{tree}");
          var result = git.reset().setMode(ResetCommand.ResetType.HARD).call();
          compileDiff(repo, git, oldHead, ret);
        }
      } catch (Exception e) {
        ret.getNotifications().add(Notification.create(e));
      }

      return ret;
    }

    /**
     * Clone repository.
     *
     * <p>TODO use authentication
     *
     * @param gitUrl the git url
     * @param directory the directory
     * @param removeIfExisting the remove if existing
     * @return the string
     */
    public static String clone(
        String gitUrl, File directory, boolean removeIfExisting, Scope scope) {

      String dirname = URLs.getURLBaseName(gitUrl);

      File pdir = new File(directory + File.separator + dirname);
      if (pdir.exists()) {
        if (removeIfExisting) {
          try {
            Files.deleteDirectory(pdir);
          } catch (Throwable e) {
            throw new KlabIOException(e);
          }
        } else {
          throw new KlabIOException("git clone: directory " + pdir + " already exists");
        }
      }

      String[] pdefs = gitUrl.split("#");
      String branch;
      if (pdefs.length < 2) {
        branch = MAIN_BRANCH;
      } else {
        branch = branchExists(pdefs[0], pdefs[1]) ? pdefs[1] : MAIN_BRANCH;
      }
      String url = pdefs[0];

      Logging.INSTANCE.info("cloning Git repository " + url + " branch " + branch + " ...");

      CredentialsProvider credentialsProvider =
          getCredentialsProvider(Authentication.INSTANCE.getCredentials(url, scope));

      try (org.eclipse.jgit.api.Git result =
          org.eclipse.jgit.api.Git.cloneRepository()
              .setURI(url)
              .setCredentialsProvider(credentialsProvider)
              .setBranch(branch)
              .setDirectory(pdir)
              .call()) {

        Logging.INSTANCE.info("cloned Git repository: " + result.getRepository());

        if (!branch.equals(MAIN_BRANCH)) {
          result
              .checkout()
              .setName(branch)
              .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
              .setStartPoint("origin/" + branch)
              .call();
          Logging.INSTANCE.info(
              "switched repository: " + result.getRepository() + " to " + "branch " + branch);
        }

      } catch (Throwable e) {
        throw new KlabIOException(e);
      }

      return dirname;
    }

    //        /**
    //         * Pull local repository in passed directory.
    //         * <p>
    //         * TODO use authentication
    //         *
    //         * @param localRepository main directory (containing .git/)
    //         */
    //        public static void pull(File localRepository) {
    //
    //            try (Repository localRepo = new FileRepository(localRepository + File.separator +
    // "
    //            .git")) {
    //                try (org.eclipse.jgit.api.Git git = new org.eclipse.jgit.api.Git(localRepo)) {
    //
    //                    Logging.INSTANCE.info("fetch/merge changes in repository: " + git
    //                    .getRepository());
    //
    //                    PullCommand pullCmd = git.pull();
    //                    PullResult result = pullCmd.call();
    //                    // return result != null && result.getFetchResult() != null &&
    //                    // result.getFetchResult().
    //
    //                } catch (Throwable e) {
    //                    throw new KlabIOException("error pulling repository " + localRepository +
    // ":
    //                    " + e.getLocalizedMessage());
    //                }
    //            } catch (IOException e) {
    //                throw new KlabIOException(e);
    //            }
    //        }

    /**
     * If a Git repository with the repository name corresponding to the URL exists in gitDirectory,
     * pull it from origin; otherwise clone it from the passed Git URL.
     *
     * <p>TODO: Assumes branch is already set correctly if repo is pulled. Should check branch and
     * checkout if necessary.
     *
     * <p>TODO use authentication
     *
     * @param gitUrl the git url
     * @param gitDirectory the git directory
     * @return the string
     */
    public static Modifications requireUpdatedRepository(
        String gitUrl, File gitDirectory, Scope scope) {

      Modifications ret = null;
      String repositoryName = URLs.getURLBaseName(gitUrl);
      File repoDir = new File(gitDirectory + File.separator + repositoryName);
      File gitDir = new File(repoDir + File.separator + ".git");

      if (gitDir.exists() && gitDir.isDirectory() && gitDir.canRead() && repoDir.exists()) {

        ret = fetchAndMerge(repoDir, scope);
        /*
         * TODO check branch and switch/pull if necessary
         */
      } else {
        if (gitDir.exists()) {
          Files.deleteQuietly(gitDir);
        }
        clone(gitUrl, gitDirectory, true, scope);
      }

      return ret;
    }

    /**
     * Checks if is remote git URL.
     *
     * @param string the string
     * @return a boolean.
     */
    public static boolean isRemoteGitURL(String string) {
      return string.startsWith("http:")
          || string.startsWith("git:")
          || string.startsWith("https" + ":")
          || string.startsWith("git@");
    }

    /**
     * Check if remote branch exists
     *
     * @param gitUrl the remote repository
     * @param branch the branch (without refs/heads/)
     * @return true if branch exists
     */
    public static boolean branchExists(String gitUrl, String branch) {
      final LsRemoteCommand lsCmd = new LsRemoteCommand(null);
      lsCmd.setRemote(gitUrl);
      try {
        return lsCmd.call().stream()
                .filter(ref -> ref.getName().equals("refs/heads/" + branch))
                .count()
            == 1;
      } catch (GitAPIException e) {
        e.printStackTrace();
        return false;
      }
    }
  }
}
