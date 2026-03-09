package org.integratedmodelling.klab.utilities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.io.inputstream.ZipInputStream;
import net.lingala.zip4j.model.LocalFileHeader;
import net.lingala.zip4j.model.ZipParameters;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
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
import org.ehcache.spi.service.ServiceConfiguration;
import org.integratedmodelling.common.authentication.Authentication;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.common.knowledge.KnowledgeRepository;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.authentication.ExternalAuthenticationCredentials;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Histogram;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.data.impl.HistogramImpl;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.lang.kim.KimLiteral;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.adapters.Adapter;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.view.UIView;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.runtime.scale.space.ShapeImpl;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.configuration.ResourcesConfiguration;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

public class Utils extends org.integratedmodelling.common.utils.Utils {

  public static class Resources extends org.integratedmodelling.common.utils.Utils.Resources {
    /**
     * Resolve a resource adapter by URN, using and prioritizing all the available services,
     * handling any needed dependencies, and checking the local ComponentRegistry for previously
     * installed adapters.
     *
     * @param urn
     * @param scope
     * @return
     */
    public static Adapter resolveAdapter(String urn, UserScope scope, BaseService targetService) {

      var ret = targetService.getComponentRegistry().getAdapter(urn, Version.ANY_VERSION, scope);
      if (ret != null) {
        return ret;
      }

      var result =
          queryResources(
              scope, ResourcesService.class, service -> service.resolveResourceAdapter(urn, scope));

      if (!result.isEmpty()) {
        if (targetService.getComponentRegistry().loadComponents(result, scope)) {
          return targetService.getComponentRegistry().getAdapter(urn, Version.ANY_VERSION, scope);
        }
      }

      return null;
    }
  }

  public static class ServiceConfiguration {

    public static void printExampleConfig() {
      var serviceConfiguration = new ResourcesConfiguration();
      var privileges = new ResourcePrivileges();
      privileges.setAllowedGroups(Set.of("ESA.INSTITUTIONAL", "ARIESTEAM"));
      serviceConfiguration.getPermissions().put(CRUDOperation.CREATE, privileges);
      serviceConfiguration.getPermissions().put(CRUDOperation.READ, privileges);
      serviceConfiguration.getPermissions().put(CRUDOperation.UPDATE, privileges);
      serviceConfiguration.getPermissions().put(CRUDOperation.UPDATE_METADATA, privileges);
      serviceConfiguration.getPermissions().put(CRUDOperation.DELETE, privileges);
      System.out.println(YAML.asString(serviceConfiguration));
    }
  }

  /** Utils to simplify accessing the JTS objects behind a scale or geometry */
  public static class Space {

    /**
     * If the passed geometry/scale has a shape, return the prepared geometry corresponding to it.
     * The prepared geometry is cached along with the scale.
     *
     * @param geometry
     * @return a prepared geometry or null
     */
    public static PreparedGeometry getPreparedJTSShape(Geometry geometry) {
      var scale = GeometryRepository.INSTANCE.scale(geometry);
      var space = scale.getSpace();
      if (space != null && space.getGeometricShape() instanceof ShapeImpl shape) {
        return shape.getPreparedGeometry();
      }
      return null;
    }

    /**
     * If the passed geometry/scale has a shape, return the JTS geometry corresponding to it.
     *
     * @param geometry
     * @return a JTS geometry or null
     */
    public static org.locationtech.jts.geom.Geometry getJTSShape(Geometry geometry) {
      var scale = GeometryRepository.INSTANCE.scale(geometry);
      var space = scale.getSpace();
      if (space != null && space.getGeometricShape() instanceof ShapeImpl shape) {
        return shape.getJTSGeometry();
      }
      return null;
    }

    /**
     * If the passed geometry/scale has a shape, return the JTS geometry corresponding to it,
     * ensuring it is in EPSG:4326 projection.
     *
     * @param geometry
     * @return a JTS geometry or null
     */
    public static org.locationtech.jts.geom.Geometry getStandardizedJTSShape(Geometry geometry) {
      var scale = GeometryRepository.INSTANCE.scale(geometry);
      var space = scale.getSpace();
      if (space != null && space.getGeometricShape() instanceof ShapeImpl shape) {
        return shape.getStandardizedGeometry();
      }
      return null;
    }
  }

  public static class Zip {

    /**
     * Create a zip with the passed directory's contents in it. The directory will be the top entry
     * in the file if storeDirectory is true.
     *
     * @param zipFile
     * @param directory
     */
    public static void zip(
        File zipFile, File directory, boolean storeDirectory, boolean readHiddenFiles) {

      // dest = buildDestinationZipFilePath(srcFile, dest);
      ZipParameters parameters = new ZipParameters();
      // parameters.setCompressionMethod(Zip4jConstants.COMP_DEFLATE); //
      // parameters.setCompressionLevel(Zip4jConstants.DEFLATE_LEVEL_NORMAL); //
      parameters.setIncludeRootFolder(storeDirectory);
      // parameters.setReadHiddenFiles(readHiddenFiles);
      try (var zipF = new ZipFile(zipFile)) {
        zipF.addFolder(directory, parameters);
      } catch (Exception e) {
        throw new KlabIOException(e);
      }
    }

    /**
     * Unzip the contents of the zip file in the passed destination directory. If the directory does
     * not exist, create it.
     *
     * @param zipFile
     * @param destDir
     */
    public static void unzip(File zipFile, File destDir) {

      try (var zFile = new ZipFile(zipFile)) {
        if (!zFile.isValidZipFile()) {
          throw new KlabIOException("file " + zipFile + " is not a valid archive");
        }

        if (!destDir.exists()) {
          destDir.mkdirs();
        }
        // if (zFile.isEncrypted()) {
        // zFile.setPassword(passwd.toCharArray());
        // }
        zFile.extractAll(destDir.toString());
      } catch (Exception e) {
        throw new KlabIOException(e);
      }
    }

    public static void extractDirectories(
        File zipFilePath, File destinationPath, Collection<String> pathsWanted) throws IOException {
      extractDirectories(
          zipFilePath, destinationPath, pathsWanted.toArray(new String[pathsWanted.size()]));
    }

    public static void extractDirectories(
        File zipFilePath, File destinationPath, String... pathsWanted) throws IOException {

      destinationPath.mkdirs();

      ZipInputStream zis = null;
      try {

        zis = new ZipInputStream(new FileInputStream(zipFilePath));

        LocalFileHeader entry;
        while ((entry = zis.getNextEntry()) != null) {

          boolean ok = false;

          for (String ss : pathsWanted) {
            if (entry.getFileName().startsWith(ss)) {
              ok = true;
              break;
            }
          }

          if (!ok) {
            continue;
          }

          File entryFile = new File(destinationPath, entry.getFileName());
          if (entry.isDirectory()) {

            if (!entryFile.exists()) {
              entryFile.mkdirs();
            }

          } else {
            copy(zis, entryFile);
          }
        }
      } finally {
        closeQuietly(zis);
      }
    }

    private static void closeQuietly(ZipInputStream zis) {
      try {
        zis.close();
      } catch (IOException e) {
      }
    }

    /*
     * copy istream to file; do not close the istream
     */
    private static int copy(InputStream iStream, File entryFile) throws IOException {

      if (entryFile.getParentFile() != null && !entryFile.getParentFile().exists()) {
        entryFile.getParentFile().mkdirs();
      }

      if (!entryFile.exists()) {
        entryFile.createNewFile();
      }

      BufferedOutputStream fOut = null;
      int bytes = 0;
      try {
        try {
          fOut = new BufferedOutputStream(new FileOutputStream(entryFile));
          byte[] buffer = new byte[32 * 1024];
          int bytesRead = 0;
          if (iStream != null) {
            while ((bytesRead = iStream.read(buffer)) != -1) {
              fOut.write(buffer, 0, bytesRead);
              bytes += bytesRead;
            }
          }
        } catch (Exception e) {
          throw new IOException("writeToFile failed, got: " + e.toString());
        } finally {
          fOut.close();
        }
      } catch (Exception e) {
        throw new IOException(e);
      }

      return bytes;
    }

    public static int copyAndClose(InputStream iStream, File entryFile) throws IOException {

      if (entryFile.getParentFile() != null && !entryFile.getParentFile().exists()) {
        entryFile.getParentFile().mkdirs();
      }

      if (!entryFile.exists()) {
        entryFile.createNewFile();
      }

      BufferedOutputStream fOut = null;
      int bytes = 0;
      try {
        try {
          fOut = new BufferedOutputStream(new FileOutputStream(entryFile));
          byte[] buffer = new byte[32 * 1024];
          int bytesRead = 0;
          if (iStream != null) {
            while ((bytesRead = iStream.read(buffer)) != -1) {
              fOut.write(buffer, 0, bytesRead);
              bytes += bytesRead;
            }
          }
        } catch (Exception e) {
          throw new IOException("writeToFile failed: " + e.toString());
        } finally {
          if (iStream != null) iStream.close();
          fOut.close();
        }
      } catch (Exception e) {
        throw new IOException(e);
      }

      return bytes;
    }
  }

  public static class Templates extends org.integratedmodelling.klab.api.utils.Utils.Templates {

    public static class TemplateBuilder {

      private final File rootFolder;
      private final List<Pair<String, Map<Object, Object>>> templates = new ArrayList<>();
      private final List<Pair<String, String>> verbatim = new ArrayList<>();
      private Map<Object, Object> data = new HashMap<>();

      public TemplateBuilder(File rootFolder) {
        this.rootFolder = rootFolder;
      }

      /**
       * Add data that will be used in substitutions.
       *
       * @param data key,value pairs for templates
       * @return this same builder instance
       */
      public TemplateBuilder with(Object... data) {
        this.data.putAll(Parameters.create(data));
        return this;
      }

      /**
       * Create a file at build() at the passed relative path (using slash separators). Contents of
       * the file depend on the remaining arguments.
       *
       * @param relativePath either a .jte template found in the classpath or another file name to
       *     use as is
       * @param content key,value pairs for templates, which will be added to the data set so far
       *     (overriding any existing key, locally to this template); string content or nothing for
       *     verbatim. If nothing is passed, the file will be created empty.
       * @return this same builder instance
       */
      public TemplateBuilder file(String relativePath, Object... content) {
        if (relativePath.endsWith(".jte")) {
          var sData = new HashMap<>(this.data);
          if (content != null) {
            var aData = Parameters.create(content);
            sData.putAll(aData);
          }
          this.templates.add(Pair.of(relativePath, sData));
        } else {
          this.verbatim.add(
              Pair.of(
                  relativePath,
                  content == null || content.length == 0 ? "" : content[0].toString()));
        }
        return this;
      }

      public File build() {
        rootFolder.mkdirs();
        for (var pair : verbatim) {
          Utils.Files.writeStringToFile(
              pair.getSecond(), Path.of(rootFolder.getAbsolutePath(), pair.getFirst()).toFile());
        }
        for (var pair : templates) {
          // TODO render template
        }
        return rootFolder;
      }

      // TODO may add some more methods to automatically set up git
    }

    public static TemplateBuilder builder(File rootFolder) {
      return new TemplateBuilder(rootFolder);
    }
  }

  public static class Data extends org.integratedmodelling.klab.api.utils.Utils.Data {

    public static Histogram adaptHistogram(com.dynatrace.dynahist.Histogram histogram) {

      /*
       * TO ADAPT TO FIXED BIN NUMBER (slow, only use once in low-priority thread):
       *
       * double[] boundaries = {0.0, 1.0, 2.0, ..., 10.0}; // Example boundaries for 10 bins (11 values)
       * HistogramLayout layout = new CustomLayout(boundaries);
       * Histogram histogram = new DynamicHistogram.Builder().layout(layout).create();
       */

      if (histogram.getMax() <= histogram.getMin()) {
        return Histogram.empty();
      }

      var ret = new HistogramImpl();
      ret.setMin(histogram.getMin());
      ret.setMax(histogram.getMax());
      try {
        for (var bin : histogram.nonEmptyBinsAscending()) {
          var hBin = new HistogramImpl.BinImpl();
          hBin.setCount(bin.getBinCount());
          hBin.setMax(bin.getUpperBound());
          hBin.setMin(bin.getLowerBound());
          ret.getBins().add(hBin);
        }
      } catch (Throwable e) {
        Logging.INSTANCE.error("adaptHistogram failed: " + e.toString());
      }
      return ret;
    }
  }

  /** Utility class for Maven Central operations, including checking snapshot release dates. */
  public static class Maven {

    //    private static final Logger logger = LoggerFactory.getLogger(Maven.class);

    private static final String MAVEN_CENTRAL_SEARCH_API =
        "https://search.maven.org/solrsearch/select";
    private static final String MAVEN_CENTRAL_SNAPSHOTS_API =
        "https://central.sonatype.com/repository/maven-snapshots";
    private static final String MAVEN_CENTRAL_RELEASES_URL = "https://repo.maven.apache.org/maven2";
    private static final String MAVEN_CENTRAL_SNAPSHOTS_URL =
        "https://central.sonatype.com/repository/maven-snapshots";
    private static final String LOCAL_MAVEN_REPOSITORY =
        System.getProperty("user.home") + File.separator + ".m2" + File.separator + "repository";
    private static final HttpClient HTTP_CLIENT =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Maven coordinates holder class */
    public static class MavenCoordinates {
      private final String groupId;
      private final String artifactId;
      private final String version;

      public MavenCoordinates(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
      }

      public String getGroupId() {
        return groupId;
      }

      public String getArtifactId() {
        return artifactId;
      }

      public String getVersion() {
        return version;
      }

      @Override
      public String toString() {
        return String.format("%s:%s:%s", groupId, artifactId, version);
      }
    }

    /** Result class containing snapshot information */
    public static class SnapshotInfo {
      private final LocalDateTime lastModified;
      private final String version;
      private final boolean found;

      public SnapshotInfo(LocalDateTime lastModified, String version, boolean found) {
        this.lastModified = lastModified;
        this.version = version;
        this.found = found;
      }

      public LocalDateTime getLastModified() {
        return lastModified;
      }

      public String getVersion() {
        return version;
      }

      public boolean isFound() {
        return found;
      }

      @Override
      public String toString() {
        if (!found) {
          return "Snapshot not found";
        }
        return String.format(
            "Version: %s, Last Modified: %s",
            version, lastModified.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
      }
    }

    /**
     * Checks the latest date of a snapshot release in Maven Central for the given coordinates.
     *
     * @param coordinates The Maven coordinates (groupId, artifactId, version)
     * @return SnapshotInfo containing the last modified date and version information
     * @throws IOException if there's an error communicating with Maven Central
     */
    public static SnapshotInfo getLatestSnapshotDate(MavenCoordinates coordinates) {
      if (!coordinates.getVersion().endsWith("-SNAPSHOT")) {
        throw new KlabIllegalArgumentException("Version must be a SNAPSHOT version");
      }

      try {
        // First, try to get snapshot info from Maven Central search API
        Optional<SnapshotInfo> searchResult = getSnapshotInfoFromSearch(coordinates);
        return searchResult.orElseGet(() -> getSnapshotInfoFromRepository(coordinates));

        // If search API doesn't work, try the direct repository approach

      } catch (Exception e) {
        Thread.currentThread().interrupt();
        throw new KlabIOException("Request was interrupted");
      }
    }

    /** Attempts to get snapshot information using Maven Central's search API */
    private static Optional<SnapshotInfo> getSnapshotInfoFromSearch(MavenCoordinates coordinates) {

      String query =
          String.format(
              "q=g:%s+AND+a:%s+AND+v:%s&core=gav&rows=1&wt=json",
              coordinates.getGroupId(), coordinates.getArtifactId(), coordinates.getVersion());

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(MAVEN_CENTRAL_SEARCH_API + "?" + query))
              .timeout(Duration.ofSeconds(30))
              .GET()
              .build();

      HttpResponse<String> response = null;
      try {
        response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      } catch (Exception e) {
        throw new KlabIOException(e);
      }

      if (response.statusCode() != 200) {
        Logging.INSTANCE.warn(
            "Maven Central search returned status {}: {}", response.statusCode(), response.body());
        return Optional.empty();
      }

      try {
        JsonNode root = OBJECT_MAPPER.readTree(response.body());
        JsonNode docs = root.path("response").path("docs");

        if (docs.isArray() && docs.size() > 0) {
          JsonNode doc = docs.get(0);
          long timestamp = doc.path("timestamp").asLong();
          String version = doc.path("v").asText();

          LocalDateTime lastModified =
              LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());

          return Optional.of(new SnapshotInfo(lastModified, version, true));
        }
      } catch (Exception e) {
        Logging.INSTANCE.warn("Error parsing Maven Central search response", e);
      }

      return Optional.empty();
    }

    /** Attempts to get snapshot information by checking the repository metadata */
    private static SnapshotInfo getSnapshotInfoFromRepository(MavenCoordinates coordinates) {

      String metadataPath =
          String.format(
              "%s/%s/%s/%s/maven-metadata.xml",
              MAVEN_CENTRAL_SNAPSHOTS_API,
              coordinates.getGroupId().replace('.', '/'),
              coordinates.getArtifactId(),
              coordinates.getVersion());

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(metadataPath))
              .timeout(Duration.ofSeconds(30))
              .GET()
              .build();

      HttpResponse<String> response = null;
      try {
        response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      } catch (Exception e) {
        throw new KlabIOException(e);
      }

      if (response.statusCode() == 404) {
        return new SnapshotInfo(null, null, false);
      }

      if (response.statusCode() != 200) {
        throw new KlabIOException(
            "Failed to fetch metadata from Maven Central: HTTP " + response.statusCode());
      }

      // Parse the maven-metadata.xml to extract timestamp information
      return parseMetadataXml(response.body(), coordinates.getVersion());
    }

    /** Parses Maven metadata XML to extract snapshot information */
    private static SnapshotInfo parseMetadataXml(String xmlContent, String version) {
      try {
        // Simple XML parsing for lastUpdated timestamp
        // In a production environment, you might want to use a proper XML parser
        String lastUpdatedPattern = "<lastUpdated>(\\d{14})</lastUpdated>";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(lastUpdatedPattern);
        java.util.regex.Matcher matcher = pattern.matcher(xmlContent);

        if (matcher.find()) {
          String timestamp = matcher.group(1);
          // Format: yyyyMMddHHmmss
          DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
          LocalDateTime lastModified = LocalDateTime.parse(timestamp, formatter);

          return new SnapshotInfo(lastModified, version, true);
        }
      } catch (Exception e) {
        Logging.INSTANCE.warn("Error parsing metadata XML", e);
      }

      return new SnapshotInfo(null, version, false);
    }

    /**
     * Convenience method to check snapshot date using string coordinates
     *
     * @param groupId Maven group ID
     * @param artifactId Maven artifact ID
     * @param version Maven version (must end with -SNAPSHOT)
     * @return SnapshotInfo containing the last modified date and version information
     * @throws IOException if there's an error communicating with Maven Central
     */
    public static SnapshotInfo getLatestSnapshotDate(
        String groupId, String artifactId, String version) {
      return getLatestSnapshotDate(new MavenCoordinates(groupId, artifactId, version));
    }

    /**
     * Downloads an artifact file with the specified classifier and suffix from Maven Central. For
     * SNAPSHOT versions, it will attempt to get the latest timestamp version.
     *
     * @param coordinates The Maven coordinates (groupId, artifactId, version)
     * @param classifier The classifier (e.g., "sources", "javadoc", "tests"), can be null for main
     *     artifact
     * @param suffix The file suffix (e.g., "jar", "pom", "xml")
     * @param targetDirectory The directory where the file should be downloaded to
     * @return The downloaded file, or null if download failed
     */
    public static File downloadArtifactFile(
        MavenCoordinates coordinates, String classifier, String suffix, File targetDirectory) {
      boolean isSnapshot = coordinates.getVersion().endsWith("-SNAPSHOT");
      String baseUrl;
      String artifactVersion = coordinates.getVersion();
      String artifactPath =
          String.format(
              "%s/%s/%s",
              coordinates.getGroupId().replace('.', '/'),
              coordinates.getArtifactId(),
              coordinates.getVersion());

      // For snapshots, we need to get the actual timestamped version
      if (isSnapshot) {
        baseUrl = MAVEN_CENTRAL_SNAPSHOTS_URL;

        try {
          // Get the latest snapshot metadata
          SnapshotInfo snapshotInfo = getLatestSnapshotDate(coordinates);
          if (!snapshotInfo.isFound()) {
            return null;
          }

          // Parse maven-metadata.xml to get the exact timestamped version
          String metadataUrl = String.format("%s/%s/maven-metadata.xml", baseUrl, artifactPath);

          HttpRequest request =
              HttpRequest.newBuilder()
                  .uri(URI.create(metadataUrl))
                  .timeout(Duration.ofSeconds(30))
                  .GET()
                  .build();

          HttpResponse<String> response =
              HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

          if (response.statusCode() != 200) {
            throw new IOException(
                "Failed to fetch snapshot metadata: HTTP " + response.statusCode());
          }

          // Extract timestamp and buildNumber
          String xmlContent = response.body();
          String timestampPattern = "<timestamp>([\\d.]+)</timestamp>";
          String buildNumberPattern = "<buildNumber>([\\d]+)</buildNumber>";

          java.util.regex.Pattern tsPattern = java.util.regex.Pattern.compile(timestampPattern);
          java.util.regex.Pattern bnPattern = java.util.regex.Pattern.compile(buildNumberPattern);

          java.util.regex.Matcher tsMatcher = tsPattern.matcher(xmlContent);
          java.util.regex.Matcher bnMatcher = bnPattern.matcher(xmlContent);

          if (tsMatcher.find() && bnMatcher.find()) {
            String timestamp = tsMatcher.group(1);
            String buildNumber = bnMatcher.group(1);

            // Form the actual version with timestamp
            String snapshotVersion = coordinates.getVersion();
            // Replace SNAPSHOT with timestamp-buildNumber
            artifactVersion =
                snapshotVersion.replace("-SNAPSHOT", "-" + timestamp + "-" + buildNumber);
          }
        } catch (Exception e) {
          Thread.currentThread().interrupt();
          return null;
        }
      } else {
        baseUrl = MAVEN_CENTRAL_RELEASES_URL;
      }

      // Construct the file name
      String fileName = coordinates.getArtifactId() + "-" + artifactVersion;
      if (classifier != null && !classifier.isEmpty()) {
        fileName += "-" + classifier;
      }
      fileName += "." + suffix;

      // Construct the download URL
      String downloadUrl = String.format("%s/%s/%s", baseUrl, artifactPath, fileName);

      Logging.INSTANCE.info("Downloading artifact from: {}", downloadUrl);

      // Create target directory if it doesn't exist
      if (!targetDirectory.exists()) {
        if (!targetDirectory.mkdirs()) {
          throw new KlabIOException("Failed to create target directory: " + targetDirectory);
        }
      }

      // Download the file
      File targetFile = new File(targetDirectory, fileName);

      try {
        HttpRequest request =
            HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .timeout(Duration.ofSeconds(120)) // Longer timeout for downloads
                .GET()
                .build();

        HttpResponse<InputStream> response =
            HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
          Logging.INSTANCE.error("Failed to download artifact: HTTP " + response.statusCode());
          return null;
        }

        // Save the file
        try (InputStream is = response.body()) {
          java.nio.file.Files.copy(is, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
          Logging.INSTANCE.error(e);
          return null;
        }

        Logging.INSTANCE.info(
            "Successfully downloaded {} to {}", fileName, targetFile.getAbsolutePath());
        return targetFile;

      } catch (Exception e) {
        Logging.INSTANCE.error(e);
        return null;
      }
    }

    /**
     * Convenience method to download artifact file using string coordinates
     *
     * @param groupId Maven group ID
     * @param artifactId Maven artifact ID
     * @param version Maven version
     * @param classifier The classifier (e.g., "sources", "javadoc"), can be null
     * @param suffix The file suffix (e.g., "jar", "pom")
     * @param targetDirectory The directory where the file should be downloaded to
     * @return The downloaded file, or null if download failed
     */
    public static File downloadArtifactFile(
        String groupId,
        String artifactId,
        String version,
        String classifier,
        String suffix,
        File targetDirectory) {
      return downloadArtifactFile(
          new MavenCoordinates(groupId, artifactId, version), classifier, suffix, targetDirectory);
    }

    /** Example usage method */
    public static void main(String[] args) {

      // Example usage
      MavenCoordinates coords =
          new MavenCoordinates(
              "org.integratedmodelling", "klab.component.geospatial", "1.0-SNAPSHOT");

      SnapshotInfo info = getLatestSnapshotDate(coords);
      System.out.println("Snapshot info for " + coords + ": " + info);

      // Example download
      File downloadedFile =
          downloadArtifactFile(
              "org.integratedmodelling",
              "klab.component.geospatial",
              "1.0-SNAPSHOT",
              "component",
              "kar",
              new File("./downloads"));
      System.out.println("Downloaded file: " + downloadedFile);
    }

    /**
     * Downloads a file from a stable (non-SNAPSHOT) Maven repository with specified classifier and
     * type. Also downloads the MD5 hash file for the main artifact.
     *
     * @param groupId Maven group ID
     * @param artifactId Maven artifact ID
     * @param version Maven version (should NOT end with -SNAPSHOT)
     * @param classifier Optional classifier (e.g., "sources", "javadoc"), can be null for main
     *     artifact
     * @param type The file type (e.g., "jar", "pom", "war")
     * @param targetDirectory The directory where the files should be downloaded to
     * @return The downloaded main file, or null if download failed
     * @throws IOException If there's an error during download
     * @throws IllegalArgumentException If the version is a SNAPSHOT version
     */
    public static File downloadStableArtifactWithMd5(
        String groupId,
        String artifactId,
        String version,
        String classifier,
        String type,
        File targetDirectory)
        throws IOException {

      if (version.endsWith("-SNAPSHOT")) {
        throw new IllegalArgumentException(
            "This method is for stable versions only. Version must not end with -SNAPSHOT");
      }

      String artifactPath =
          String.format("%s/%s/%s", groupId.replace('.', '/'), artifactId, version);

      // Construct the file name
      String fileName = artifactId + "-" + version;
      if (classifier != null && !classifier.isEmpty()) {
        fileName += "-" + classifier;
      }
      fileName += "." + type;

      // Construct the download URL
      String baseUrl = MAVEN_CENTRAL_RELEASES_URL;
      String downloadUrl = String.format("%s/%s/%s", baseUrl, artifactPath, fileName);
      String md5Url = downloadUrl + ".md5";

      Logging.INSTANCE.info("Downloading artifact from: {}", downloadUrl);
      Logging.INSTANCE.info("Downloading MD5 hash from: {}", md5Url);

      // Create target directory if it doesn't exist
      if (!targetDirectory.exists()) {
        if (!targetDirectory.mkdirs()) {
          throw new IOException("Failed to create target directory: " + targetDirectory);
        }
      }

      // Download the main file
      File targetFile = new File(targetDirectory, fileName);
      File md5File = new File(targetDirectory, fileName + ".md5");

      try {
        // Download the main artifact
        HttpRequest request =
            HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .timeout(Duration.ofSeconds(120)) // Longer timeout for downloads
                .GET()
                .build();

        HttpResponse<InputStream> response =
            HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
          throw new IOException("Failed to download artifact: HTTP " + response.statusCode());
        }

        // Save the main file
        try (InputStream is = response.body()) {
          java.nio.file.Files.copy(is, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        // Download the MD5 file
        request =
            HttpRequest.newBuilder()
                .uri(URI.create(md5Url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() == 200) {
          // Save the MD5 file
          try (InputStream is = response.body()) {
            java.nio.file.Files.copy(is, md5File.toPath(), StandardCopyOption.REPLACE_EXISTING);
          }
          Logging.INSTANCE.info(
              "Successfully downloaded MD5 hash to {}", md5File.getAbsolutePath());

          // Verify the MD5 hash
          String expectedMd5 = java.nio.file.Files.readString(md5File.toPath()).trim();
          String actualMd5 =
              org.apache.commons.codec.digest.DigestUtils.md5Hex(
                  java.nio.file.Files.newInputStream(targetFile.toPath()));

          if (!expectedMd5.equals(actualMd5)) {
            Logging.INSTANCE.warn(
                "MD5 hash verification failed. Expected: {}, Actual: {}", expectedMd5, actualMd5);
            // We don't throw exception here, just warn about the mismatch
          }
        } else {
          Logging.INSTANCE.warn("Failed to download MD5 hash: HTTP {}", response.statusCode());
        }

        Logging.INSTANCE.info(
            "Successfully downloaded {} to {}", fileName, targetFile.getAbsolutePath());
        return targetFile;

      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("Download was interrupted", e);
      }
    }

    /**
     * Downloads a file from a stable (non-SNAPSHOT) Maven repository with specified classifier and
     * type. Also downloads the MD5 hash file for the main artifact.
     *
     * @param coordinates The Maven coordinates (groupId, artifactId, version)
     * @param classifier Optional classifier (e.g., "sources", "javadoc"), can be null for main
     *     artifact
     * @param type The file type (e.g., "jar", "pom", "war")
     * @param targetDirectory The directory where the files should be downloaded to
     * @return The downloaded main file, or null if download failed
     * @throws IOException If there's an error during download
     * @throws IllegalArgumentException If the version is a SNAPSHOT version
     */
    public static File downloadStableArtifactWithMd5(
        MavenCoordinates coordinates, String classifier, String type, File targetDirectory)
        throws IOException {
      return downloadStableArtifactWithMd5(
          coordinates.getGroupId(),
          coordinates.getArtifactId(),
          coordinates.getVersion(),
          classifier,
          type,
          targetDirectory);
    }

    /**
     * Checks if the artifact exists in the local Maven repository before attempting to download. If
     * found locally, returns the local file. Otherwise, downloads from Maven Central repository.
     *
     * @param groupId Maven group ID
     * @param artifactId Maven artifact ID
     * @param version Maven version
     * @param classifier The classifier (e.g., "sources", "javadoc"), can be null
     * @param suffix The file suffix (e.g., "jar", "pom")
     * @param targetDirectory The directory where the file should be copied to if found locally or
     *     downloaded to if not found locally
     * @return The file (either from local repository or downloaded), or null if not found and
     *     download failed
     * @throws IOException If there's an error during file operations or download
     */
    public static File findOrDownloadArtifactFile(
        String groupId,
        String artifactId,
        String version,
        String classifier,
        String suffix,
        File targetDirectory) {

      // First check if we can find the file in the local Maven repository
      File localFile = findLocalArtifactFile(groupId, artifactId, version, classifier, suffix);

      if (localFile != null && localFile.exists()) {
        Logging.INSTANCE.info(
            "Found artifact in local Maven repository: {}", localFile.getAbsolutePath());

        // Create target directory if it doesn't exist
        if (!targetDirectory.exists()) {
          if (!targetDirectory.mkdirs()) {
            throw new KlabIOException("Failed to create target directory: " + targetDirectory);
          }
        }

        // Construct the target file name
        String fileName = artifactId + "-" + version;
        if (classifier != null && !classifier.isEmpty()) {
          fileName += "-" + classifier;
        }
        fileName += "." + suffix;

        File targetFile = new File(targetDirectory, fileName);

        // Copy the file to the target directory if it's not already there
        if (!targetFile.exists()
            || targetFile.lastModified() != localFile.lastModified()
            || targetFile.length() != localFile.length()) {
          try {
            java.nio.file.Files.copy(
                localFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Logging.INSTANCE.info("Copied local artifact to: {}", targetFile.getAbsolutePath());
          } catch (IOException e) {
            throw new KlabIOException(e);
          }
        } else {
          Logging.INSTANCE.info(
              "Target file already exists and is identical: {}", targetFile.getAbsolutePath());
        }

        return targetFile;
      }

      // If not found locally, download from Maven repository
      Logging.INSTANCE.info("Artifact not found in local Maven repository, attempting download");
      return downloadArtifactFile(
          groupId, artifactId, version, classifier, suffix, targetDirectory);
    }

    /**
     * Find an artifact file in the local Maven repository.
     *
     * @param groupId Maven group ID
     * @param artifactId Maven artifact ID
     * @param version Maven version
     * @param classifier The classifier (e.g., "sources", "javadoc"), can be null
     * @param suffix The file suffix (e.g., "jar", "pom")
     * @return The file from local repository, or null if not found
     */
    public static File findLocalArtifactFile(
        String groupId, String artifactId, String version, String classifier, String suffix) {

      String artifactPath =
          String.format(
              "%s%s%s%s%s%s%s",
              LOCAL_MAVEN_REPOSITORY,
              File.separator,
              groupId.replace('.', File.separatorChar),
              File.separator,
              artifactId,
              File.separator,
              version);

      // For SNAPSHOT versions, we need to look for the timestamped version
      if (version.endsWith("-SNAPSHOT")) {
        File snapshotDir = new File(artifactPath);
        if (!snapshotDir.exists() || !snapshotDir.isDirectory()) {
          return null;
        }

        // Look for the maven-metadata-local.xml file to find the actual version
        File metadataFile = new File(snapshotDir, "maven-metadata-local.xml");
        if (!metadataFile.exists()) {
          // Try without the -local suffix
          metadataFile = new File(snapshotDir, "maven-metadata.xml");
          if (!metadataFile.exists()) {
            // If we can't find the metadata, try to find any matching file
            return findLatestSnapshotFileInDirectory(snapshotDir, artifactId, classifier, suffix);
          }
        }

        try {
          String xmlContent = java.nio.file.Files.readString(metadataFile.toPath());
          String timestampPattern = "<timestamp>([\\d.]+)</timestamp>";
          String buildNumberPattern = "<buildNumber>([\\d]+)</buildNumber>";

          java.util.regex.Pattern tsPattern = java.util.regex.Pattern.compile(timestampPattern);
          java.util.regex.Pattern bnPattern = java.util.regex.Pattern.compile(buildNumberPattern);

          java.util.regex.Matcher tsMatcher = tsPattern.matcher(xmlContent);
          java.util.regex.Matcher bnMatcher = bnPattern.matcher(xmlContent);

          if (tsMatcher.find() && bnMatcher.find()) {
            String timestamp = tsMatcher.group(1);
            String buildNumber = bnMatcher.group(1);

            // Form the actual version with timestamp
            String actualVersion =
                version.replace("-SNAPSHOT", "-" + timestamp + "-" + buildNumber);

            // Construct the file name
            String fileName = artifactId + "-" + actualVersion;
            if (classifier != null && !classifier.isEmpty()) {
              fileName += "-" + classifier;
            }
            fileName += "." + suffix;

            File artifactFile = new File(snapshotDir, fileName);
            if (artifactFile.exists()) {
              return artifactFile;
            }
          }

          // If we can't find the specific version, try to find any matching file
          return findLatestSnapshotFileInDirectory(snapshotDir, artifactId, classifier, suffix);

        } catch (IOException e) {
          Logging.INSTANCE.warn("Error reading snapshot metadata file: {}", e.getMessage());
          return findLatestSnapshotFileInDirectory(snapshotDir, artifactId, classifier, suffix);
        }
      } else {
        // For regular versions, just construct the path directly
        String fileName = artifactId + "-" + version;
        if (classifier != null && !classifier.isEmpty()) {
          fileName += "-" + classifier;
        }
        fileName += "." + suffix;

        File artifactFile = new File(artifactPath, fileName);
        if (artifactFile.exists()) {
          return artifactFile;
        }
      }

      return null;
    }

    /**
     * Helper method to find the latest snapshot file in a directory when metadata isn't available
     * or reliable.
     *
     * @param directory The snapshot directory
     * @param artifactId The artifact ID
     * @param classifier The classifier (can be null)
     * @param suffix The file suffix
     * @return The latest matching file, or null if none found
     */
    private static File findLatestSnapshotFileInDirectory(
        File directory, String artifactId, String classifier, String suffix) {
      if (!directory.exists() || !directory.isDirectory()) {
        return null;
      }

      // Pattern to match snapshot files
      final String classifierPart =
          classifier != null && !classifier.isEmpty() ? "-" + classifier : "";
      final String patternStr = artifactId + "-.*" + classifierPart + "\\." + suffix + "$";
      final java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(patternStr);

      // Find all matching files and sort by last modified date (newest first)
      File[] matchingFiles =
          directory.listFiles(
              file -> {
                return file.isFile() && pattern.matcher(file.getName()).matches();
              });

      if (matchingFiles == null || matchingFiles.length == 0) {
        return null;
      }

      // Sort by last modified (newest first)
      Arrays.sort(matchingFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

      return matchingFiles[0]; // Return the newest file
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

    public static boolean copy(InputStream source, File destination) {
      try (var output = new FileOutputStream(destination)) {
        IOUtils.copy(source, output);
      } catch (IOException e) {
        Logging.INSTANCE.error(e);
        return false;
      }
      return true;
    }

    public static boolean copy(File source, File destination) {
      try {
        FileUtils.copyFile(source, destination);
        return true;
      } catch (IOException e) {
        Logging.INSTANCE.error(e);
      }
      return false;
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
                          UIView.Interactivity.DISPLAY));
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
                ret.getNotifications().add(Notification.error(ex, UIView.Interactivity.DISPLAY));
              }
            }
          }
          compileDiff(repo, git, oldHead, ret);
        }
      } catch (Exception e) {
        ret.getNotifications().add(Notification.error(e, UIView.Interactivity.DISPLAY));
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
              credentials =
                  org.integratedmodelling.common.authentication.Authentication.INSTANCE
                      .getCredentials(uri.toString(), scope);
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
                          UIView.Interactivity.DISPLAY));
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
                        UIView.Interactivity.DISPLAY));
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
            .add(Notification.error(message.toString(), UIView.Interactivity.DISPLAY));

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
          getCredentialsProvider(
              org.integratedmodelling.common.authentication.Authentication.INSTANCE.getCredentials(
                  url, scope));

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
