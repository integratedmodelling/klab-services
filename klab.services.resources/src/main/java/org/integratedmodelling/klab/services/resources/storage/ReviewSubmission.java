package org.integratedmodelling.klab.services.resources.storage;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectMaterial;
import org.integratedmodelling.klab.api.services.resources.workflow.ProposalReview.CheckStatus;
import org.integratedmodelling.klab.resources.FileProjectStorage;
import org.integratedmodelling.klab.resources.ProjectMaterialIO;
import org.integratedmodelling.klab.services.resources.workflow.ProposalSchemaValidator;

/** Validation and manifest construction for a project review submission. */
public final class ReviewSubmission {
  private static final ObjectMapper JSON = new ObjectMapper()
      .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
      .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
  private static final ObjectMapper YAML = new ObjectMapper(
      YAMLFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
      .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

  public record Target(String project, String kind, String urn) {}
  public record Input(String manifest, String sha256) {}
  public record Base(String state, String path, String revision, byte[] content) {}
  public record Anchor(int start, int end, String statement) {}
  public record Request(String kind, Target target, String id, String created_at, Base base,
      byte[] payload, byte[] candidate, List<Input> inputs, Anchor anchor) {
    public Request(String kind, Target target, String id, String created_at, Base base,
        byte[] payload, byte[] candidate, List<Input> inputs) {
      this(kind, target, id, created_at, base, payload, candidate, inputs, null);
    }
  }
  public record Evidence(Target target, String id, String filename, String origin, String attribution,
                         String relation, byte[] content) {}
  public record Receipt(String path, String sha256) {}
  private record FileDescriptor(String sha256, int size) {}
  record Bundle(String directory, Map<String, byte[]> files, List<Input> inputs,
      String proposalId, String supersedesRevision) {
    Receipt receipt() {
      return new Receipt(directory + "/manifest.json", hash(files.get("manifest.json")));
    }
  }

  private ReviewSubmission() {}

  static WorkspaceManager.ReviewApplication prepareApplication(FileProjectStorage storage,
      Target target, String editor, String baseRevision, String baseText, List<Input> inputs) throws IOException {
    if (inputs == null || inputs.size() > 101)
      throw new IllegalArgumentException("A review application requires up to 101 input references");
    var proposalIds = new LinkedHashSet<String>();
    var proposalRevisions = new ArrayList<String>();
    var referenced = new HashSet<String>();
    for (var input : inputs) {
      validateInput(input);
      if (!referenced.add(input.manifest())) throw new IllegalArgumentException("Duplicate review input");
      var bytes = ProjectMaterialIO.read(storage, input.manifest());
      if (bytes == null || !hash(bytes).equals(input.sha256()))
        throw new IllegalArgumentException("Review input is missing or has changed: " + input.manifest());
      var manifest = JSON.readTree(bytes);
      boolean proposal = input.manifest().startsWith("review/proposals/");
      if (manifest == null || !target.project().equals(manifest.path("target").path("project").asText())
          || !(proposal ? "proposal" : "comment").equals(manifest.path("kind").asText())
          || !manifest.path("id").isTextual())
        throw new IllegalArgumentException("Invalid review input: " + input.manifest());
      if (proposal) {
        var revision = manifest.path("id").asText();
        var payload = ProjectMaterialIO.read(storage, input.manifest().replaceFirst("manifest\\.json$", "proposal.yaml"));
        var descriptor = manifest.path("files").path("proposal.yaml");
        if (payload == null || payload.length != descriptor.path("size").asInt(-1)
            || !hash(payload).equals(descriptor.path("sha256").asText()))
          throw new IllegalArgumentException("Proposal payload is missing or has changed: " + input.manifest());
        var document = YAML.readTree(payload);
        var record = document == null ? null : document.path("proposal");
        if (record == null || !record.path("id").isTextual() || !revision.equals(record.path("revision_id").asText()))
          throw new IllegalArgumentException("Proposal identity differs from its manifest: " + input.manifest());
        proposalIds.add(record.path("id").asText());
        proposalRevisions.add(revision);
      }
    }
    var previous = ProjectMaterialIO.readReviewDecisions(storage);
    var event = new LinkedHashMap<String, Object>();
    String id = "review-" + UUID.randomUUID();
    event.put("id", id);
    event.put("created_at", Instant.now().toString());
    event.put("editor", editor);
    event.put("target", target);
    event.put("disposition", "applied");
    event.put("rationale", "Applied the reviewed ontology candidate from Workspaces.");
    event.put("inputs", List.copyOf(inputs));
    event.put("proposal_ids", proposalIds);
    event.put("proposal_revision_ids", proposalRevisions);
    event.put("base_revision", baseRevision);
    event.put("resulting_revision", baseRevision);
    byte[] baseBytes = baseText.getBytes(StandardCharsets.UTF_8);
    if (baseBytes.length > ProjectMaterialIO.MAX_BYTES) throw new IOException("Base snapshot exceeds 32 MiB");
    var baseSnapshot = new FileDescriptor(hash(baseBytes), baseBytes.length);
    event.put("snapshots", Map.of("base", baseSnapshot, "result", baseSnapshot));
    if ((previous == null ? 0 : previous.length) + JSON.writeValueAsBytes(event).length + 2 > ProjectMaterialIO.MAX_BYTES)
      throw new IOException("Review history exceeds 32 MiB");
    return (resultingRevision, resultingText) -> {
      byte[] resultBytes = resultingText.getBytes(StandardCharsets.UTF_8);
      event.put("resulting_revision", resultingRevision);
      event.put("snapshots", Map.of("base", baseSnapshot, "result", new FileDescriptor(hash(resultBytes), resultBytes.length)));
      ProjectMaterialIO.appendReviewDecision(storage, previous, JSON.writeValueAsBytes(event),
          "review/decisions/" + id, Map.of("base.kwv", baseBytes, "result.kwv", resultBytes));
    };
  }

  private static void validateInput(Input input) {
    if (input == null || input.manifest() == null
        || !input.manifest().matches("review/(proposals|comments)/[^/]+/[^/]+/manifest\\.json")
        || input.sha256() == null || !input.sha256().matches("[a-f0-9]{64}"))
      throw new IllegalArgumentException("Review inputs require a manifest path and SHA-256");
    ProjectMaterial.canonicalPath(input.manifest());
  }

  static Bundle prepare(Request request, String contributor) throws IOException {
    if (request == null || request.target() == null || request.base() == null
        || request.payload() == null || request.payload().length == 0
        || request.id() == null || !request.id().matches("[a-z0-9][a-z0-9-]{0,63}"))
      throw new IllegalArgumentException("A target, ID, base and payload are required");
    var target = request.target();
    String targetKey = targetKey(target);
    if (request.created_at() == null || !request.created_at().matches("\\d{4}-.*Z"))
      throw new IllegalArgumentException("A UTC creation timestamp is required");
    Instant timestamp;
    try {
      timestamp = Instant.parse(request.created_at());
    } catch (DateTimeParseException invalid) {
      throw new IllegalArgumentException("Invalid UTC creation timestamp", invalid);
    }
    var base = request.base();
    long size = request.payload().length + (request.candidate() == null ? 0L : request.candidate().length)
        + (base.content() == null ? 0L : base.content().length);
    if (size > ProjectMaterialIO.MAX_BYTES)
      throw new IllegalArgumentException("Review bundle exceeds 32 MiB");
    var inputs = request.inputs() == null ? List.<Input>of() : request.inputs();
    if (inputs.size() > 100) throw new IllegalArgumentException("Too many review inputs");
    inputs.forEach(ReviewSubmission::validateInput);
    if (inputs.stream().map(Input::manifest).distinct().count() != inputs.size())
      throw new IllegalArgumentException("Review inputs must be unique");
    var files = new LinkedHashMap<String, byte[]>();
    var baseMetadata = new LinkedHashMap<String, Object>();
    baseMetadata.put("state", base.state());
    String extension = switch (target.kind()) {
      case "ontology" -> "kwv";
      case "namespace" -> "kim";
      case "behavior" -> "kactor";
      default -> "bin";
    };
    if ("snapshot".equals(base.state())) {
      if (base.content() == null || base.path() == null || base.path().isBlank())
        throw new IllegalArgumentException("A snapshot requires the original path and bytes");
      if (base.path().contains("\\") || base.path().contains(":")
          || List.of(base.path().split("/", -1)).stream().anyMatch(
              part -> part.isBlank() || part.equals(".") || part.equals("..")))
        throw new IllegalArgumentException("Invalid snapshot source path");
      String name = "base." + extension;
      files.put(name, base.content());
      baseMetadata.put("path", base.path());
      if (base.revision() != null) baseMetadata.put("revision", base.revision());
      baseMetadata.put("file", name);
    } else if (!("absent".equals(base.state()) && ("proposal".equals(request.kind())
        || "comment".equals(request.kind()) && "project".equals(target.kind()))
        || "reference".equals(base.state()) && "comment".equals(request.kind())) || base.content() != null
        || base.path() != null || base.revision() != null) {
      throw new IllegalArgumentException("Specify a snapshot, new proposal base absence, or comment proposal reference");
    }
    var anchor = request.anchor();
    if (anchor != null && (!"snapshot".equals(base.state()) || anchor.start() < 0 || anchor.end() < anchor.start()
        || anchor.end() > new String(base.content(), StandardCharsets.UTF_8).length()
        || anchor.statement() != null && anchor.statement().length() > 2048))
      throw new IllegalArgumentException("The review position must be inside its base snapshot");

    String schema;
    String proposalId = null;
    String supersedes = null;
    if ("proposal".equals(request.kind()) && "ontology".equals(target.kind())) {
      var result = ProposalSchemaValidator.bundled().validate(request.payload());
      if (result.status() != CheckStatus.PASS)
        throw new IllegalArgumentException("Ontology proposal failed schema validation: " + result);
      var proposal = YAML.readTree(request.payload()).path("proposal");
      if (!request.id().equals(proposal.path("revision_id").asText()))
        throw new IllegalArgumentException("Bundle ID must match proposal.revision_id");
      proposalId = proposal.path("id").asText();
      supersedes = proposal.path("supersedes_revision").asText(null);
      files.put("proposal.yaml", request.payload());
      if (request.candidate() != null) files.put("candidate.kwv", request.candidate());
      schema = "domain-context-proposal/1.3";
    } else if ("comment".equals(request.kind()) && request.candidate() == null) {
      if (files.isEmpty() && !"project".equals(target.kind())
          && inputs.stream().noneMatch(input -> input.manifest().startsWith("review/proposals/")))
        throw new IllegalArgumentException("Comments require a document snapshot or input proposal");
      com.fasterxml.jackson.databind.JsonNode comments;
      try {
        comments = JSON.readTree(request.payload());
      } catch (IOException invalid) {
        throw new IllegalArgumentException("Malformed JSON review comments", invalid);
      }
      if (comments == null || !comments.isArray() || comments.isEmpty())
        throw new IllegalArgumentException("Expected a nonempty JSON array of review comments");
      var ids = new java.util.HashSet<String>();
      for (var comment : comments) {
        if (!comment.isObject() || !comment.path("commentId").isTextual()
            || comment.path("commentId").asText().isBlank()
            || !ids.add(comment.path("commentId").asText())
            || !comment.path("target").isTextual() || comment.path("target").asText().isBlank()
            || !comment.path("comment").isTextual() || comment.path("comment").asText().isBlank())
          throw new IllegalArgumentException("Comments require a unique commentId, target and comment");
      }
      files.put("comments.json", request.payload());
      schema = "review-comments/1";
    } else {
      throw new IllegalArgumentException("Only ontology proposals and project review comments are supported");
    }

    var manifest = new LinkedHashMap<String, Object>();
    manifest.put("version", 1);
    manifest.put("kind", request.kind());
    manifest.put("target", target);
    manifest.put("id", request.id());
    manifest.put("contributor", contributor);
    manifest.put("created_at", timestamp.toString());
    manifest.put("status", "submitted");
    manifest.put("payload_schema", schema);
    manifest.put("base", baseMetadata);
    if (anchor != null) manifest.put("anchor", anchor);
    manifest.put("inputs", new ArrayList<>(inputs));
    var descriptors = new LinkedHashMap<String, FileDescriptor>();
    files.forEach((name, bytes) ->
        descriptors.put(name, new FileDescriptor(hash(bytes), bytes.length)));
    manifest.put("files", descriptors);
    byte[] manifestBytes = JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest);
    if (size + manifestBytes.length > ProjectMaterialIO.MAX_BYTES)
      throw new IllegalArgumentException("Review bundle including manifest exceeds 32 MiB");
    files.put("manifest.json", manifestBytes);
    String directory = "review/" + request.kind() + "s/" + targetKey + "/"
        + TIMESTAMP.format(timestamp) + "--user-"
        + hash(contributor.getBytes(StandardCharsets.UTF_8)).substring(0, 12) + "--" + request.id();
    return new Bundle(directory, files, List.copyOf(inputs), proposalId, supersedes);
  }

  static Bundle prepareEvidence(Evidence evidence, String contributor) throws IOException {
    if (evidence.id() == null || !evidence.id().matches("[a-z0-9][a-z0-9-]{0,63}")
        || evidence.content() == null || evidence.content().length == 0
        || !description(evidence.filename()) || !description(evidence.origin())
        || !description(evidence.attribution()) || !description(evidence.relation()))
      throw new IllegalArgumentException("Evidence requires a file, origin, attribution and relation");
    var content = evidence.content();
    if (content.length > ProjectMaterialIO.MAX_BYTES) throw new IllegalArgumentException("Evidence exceeds 32 MiB");
    var record = new LinkedHashMap<String, Object>();
    record.put("version", 1);
    record.put("target", evidence.target());
    record.put("id", evidence.id());
    record.put("filename", evidence.filename());
    record.put("origin", evidence.origin());
    record.put("attribution", evidence.attribution());
    record.put("relation", evidence.relation());
    record.put("contributor", contributor);
    record.put("file", "source.bin");
    record.put("sha256", hash(content));
    byte[] metadata = JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(record);
    if (content.length + (long) metadata.length > ProjectMaterialIO.MAX_BYTES)
      throw new IllegalArgumentException("Evidence including its record exceeds 32 MiB");
    return new Bundle("review/evidence/" + targetKey(evidence.target()) + "/" + evidence.id(),
        Map.of("evidence.json", metadata, "source.bin", content), List.of(), null, null);
  }

  private static boolean description(String value) { return value != null && !value.isBlank() && value.length() <= 2048; }

  private static String targetKey(Target target) {
    if (target == null || target.kind() == null || target.kind().length() > 32
        || !target.kind().matches("[a-z][a-z0-9]*(?:-[a-z0-9]+)*") || !description(target.urn()))
      throw new IllegalArgumentException("Invalid review target");
    String urn = target.urn();
    return target.kind() + "--" + (urn.matches("[a-z0-9][a-z0-9._-]{0,63}") && !urn.endsWith(".")
        ? urn : "sha256--" + hash(urn.getBytes(StandardCharsets.UTF_8)));
  }

  public static String hash(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  static boolean matchesPredecessor(byte[] manifestBytes, byte[] payload, Bundle bundle,
      Target target) throws IOException {
    var manifest = JSON.readTree(manifestBytes);
    if (!bundle.supersedesRevision().equals(manifest.path("id").asText())
        || !JSON.valueToTree(target).equals(manifest.path("target")) || payload == null)
      return false;
    var descriptor = manifest.path("files").path("proposal.yaml");
    if (payload.length != descriptor.path("size").asInt(-1)
        || !hash(payload).equals(descriptor.path("sha256").asText()))
      return false;
    var proposal = YAML.readTree(payload).path("proposal");
    return bundle.proposalId().equals(proposal.path("id").asText())
        && bundle.supersedesRevision().equals(proposal.path("revision_id").asText());
  }
}
