package org.integratedmodelling.resources.server.controllers;

import java.security.Principal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.integratedmodelling.common.services.client.DocumentSemanticValidation;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.data.RepositoryState;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest;
import org.integratedmodelling.klab.api.services.resources.AuthoringSource;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.settings.ProjectSettings;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;
import org.integratedmodelling.klab.services.resources.storage.WorkspaceManager;
import org.integratedmodelling.resources.server.ResourcesServer;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Authenticated, project-scoped source and settings for the Resources Workspaces page. */
@RestController
@Secured(Role.USER)
@RequestMapping(ServicesAPI.API_BASE + "/workspaces")
public class WorkspacesController {

  private static final Pattern CONCEPT_REFERENCE =
      Pattern.compile("(?<![\\w.:-])[a-z_]+(?:\\.[a-z_-]+)*:[A-Z][A-Za-z_0-9]*\\b");

  private final ResourcesServer server;
  private final WorkspaceLanguageServers languageServers;

  public WorkspacesController(ResourcesServer server, WorkspaceLanguageServers languageServers) {
    this.server = server;
    this.languageServers = languageServers;
  }

  public record WorkspaceItem(String urn, List<String> projects,
                              Map<String, RepositoryState.Status> repositoryStatuses) {}

  public record DocumentItem(String id, String label, String language, String extension, String path,
                             ProjectStorage.ResourceType type, long errors, long warnings) {}

  public record Revision(String branch, List<String> branches, RepositoryState.Status status,
                         boolean remote, Map<String, RepositoryState.Status> files,
                         List<String> untrackedFolders) {}

  public record ProjectView(String urn, String description, String worldview, String definedWorldview,
                            String version, boolean snapshot, boolean editable, boolean canManageRepository,
                            boolean administrator, Map<String, Object> metadata, String permissions,
                            String settingsRevision, Revision repository, List<DocumentItem> documents) {}

  public record Diagnostic(String level, String message, Integer offset, Integer length) {
    static Diagnostic of(Notification notification) {
      var context = notification.getLexicalContext();
      var level = notification.getLevel() == Notification.Level.SystemError
          ? Notification.Level.Error : notification.getLevel();
      return new Diagnostic(level.name(), notification.getMessage(),
          context == null ? null : context.getOffsetInDocument(),
          context == null ? null : context.getLength());
    }
  }

  public record Source(DocumentItem document, String text, String revision, List<Diagnostic> diagnostics,
                       Map<String, String> concepts) {}
  public record SourceEdit(String revision, String text) {}
  public record Draft(String text) {}
  public record LanguageSession(UUID id) {}
  public record SettingsEdit(String revision, ProjectSettings settings) {}
  public record SaveResult(Source source, List<Diagnostic> notifications) {}
  public record SemanticResult(String status, String reason, List<Diagnostic> diagnostics) {}
  public record RepositoryEdit(RepositoryState.Operation operation, String parameter) {}

  @GetMapping
  public List<WorkspaceItem> workspaces(Principal principal) {
    return service().list(Workspace.class, user(principal)).stream()
        .map(workspace -> {
          var statuses = new TreeMap<String, RepositoryState.Status>();
          for (var project : workspace.getProjects()) {
            var state = project.getRepositoryState();
            if (state != null) statuses.put(project.getUrn(), state.getOverallStatus());
          }
          return new WorkspaceItem(workspace.getUrn(),
              workspace.getProjects().stream().map(Project::getUrn).sorted().toList(), statuses);
        })
        .sorted(Comparator.comparing(WorkspaceItem::urn))
        .toList();
  }

  @GetMapping("/project")
  public ProjectView project(@RequestParam String workspace, @RequestParam String project,
                             Principal principal) {
    var scope = user(principal);
    var current = project(workspace, project, scope);
    var manifest = current.getManifest();
    var manager = service().getWorkspaceManager();
    var editable = service().canProjectOperation(project, CRUDOperation.UPDATE, scope)
        && manager.canLockProject(project, scope);
    var administrator = service().canAdministerProjectSettings(scope);
    var repository = current.getRepositoryState();
    return new ProjectView(project,
        manifest == null ? null : manifest.getDescription(), manifest == null ? null : manifest.getWorldview(),
        manifest == null ? null : manifest.getDefinedWorldview(),
        manifest == null || manifest.getVersion() == null ? null : manifest.getVersion().toString(),
        manifest != null && manifest.isSnapshot(), editable,
        administrator && isAdministrator(principal) && editable && repository != null && repository.getCurrentBranch() != null,
        administrator, current.getSettings().getMetadata(),
        editable ? current.getSettings().getPermissions() : null,
        settingsRevision(current), revision(repository),
        current.allDocuments().stream().map(WorkspacesController::item).toList());
  }

  @GetMapping("/source")
  public Source source(@RequestParam String workspace, @RequestParam String project,
                       @RequestParam String document, Principal principal) {
    var current = project(workspace, project, user(principal));
    return source(current, document(current, document));
  }

  @GetMapping("/source/semantics")
  public SemanticResult semantics(@RequestParam String workspace, @RequestParam String project,
                                  @RequestParam String document, @RequestParam String revision,
                                  Principal principal) {
    var scope = user(principal);
    var current = document(project(workspace, project, scope), document);
    checkRevision(current, revision);
    if (!(current instanceof KimOntology || current instanceof KimNamespace))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document has no semantic validator");
    var request = SemanticValidationRequest.of(current, revision);
    var result = DocumentSemanticValidation.validate(request, scope);
    return new SemanticResult(result.getStatus().name(), result.getReason(), diagnostics(result.getNotifications()));
  }

  @PostMapping("/source/diagnostics")
  public List<Diagnostic> draftDiagnostics(@RequestParam String workspace, @RequestParam String project,
                                           @RequestParam String document, @RequestBody Draft draft,
                                           Principal principal) {
    var scope = user(principal);
    var current = document(project(workspace, project, scope), document);
    service().requireProjectOperation(project, CRUDOperation.UPDATE, scope);
    if (draft.text() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing source");
    return diagnostics(service().getWorkspaceManager().checkSourceSyntax(
        ProjectStorage.ResourceType.classify(current), draft.text()));
  }

  @PostMapping("/source/lsp")
  public LanguageSession connectLanguageServer(@RequestParam String workspace, @RequestParam String project,
      @RequestParam String document, @RequestBody WorkspaceLanguageServers.Snapshot snapshot, Principal principal) {
    var scope = user(principal);
    var current = writableDocument(workspace, project, document, scope);
    return new LanguageSession(languageServers.open(languageOwner(workspace, project, document, scope),
        current.getLanguage(), snapshot));
  }

  @PutMapping("/source/lsp/{session}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void updateLanguageServer(@RequestParam String workspace, @RequestParam String project,
      @RequestParam String document, @PathVariable UUID session,
      @RequestBody WorkspaceLanguageServers.Snapshot snapshot, Principal principal) {
    var scope = user(principal);
    writableDocument(workspace, project, document, scope);
    languageServers.sync(languageOwner(workspace, project, document, scope), session, snapshot);
  }

  @GetMapping("/source/lsp/{session}")
  public WorkspaceLanguageServers.Problems languageProblems(@RequestParam String workspace,
      @RequestParam String project, @RequestParam String document, @PathVariable UUID session, Principal principal) {
    var scope = user(principal);
    writableDocument(workspace, project, document, scope);
    return languageServers.problems(languageOwner(workspace, project, document, scope), session);
  }

  @GetMapping("/source/lsp/{session}/completion")
  public List<WorkspaceLanguageServers.Suggestion> completeLanguage(@RequestParam String workspace,
      @RequestParam String project, @RequestParam String document, @PathVariable UUID session,
      @RequestParam int version, @RequestParam int line, @RequestParam int column, Principal principal) {
    var scope = user(principal);
    writableDocument(workspace, project, document, scope);
    return languageServers.complete(languageOwner(workspace, project, document, scope), session, version, line, column);
  }

  @DeleteMapping("/source/lsp/{session}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void disconnectLanguageServer(@RequestParam String workspace, @RequestParam String project,
      @RequestParam String document, @PathVariable UUID session, Principal principal) {
    languageServers.close(languageOwner(workspace, project, document, user(principal)), session);
  }

  private KlabDocument<?> writableDocument(String workspace, String project, String id, UserScope scope) {
    var current = document(project(workspace, project, scope), id);
    service().requireProjectOperation(project, CRUDOperation.UPDATE, scope);
    if (!service().getWorkspaceManager().canLockProject(project, scope))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    return current;
  }

  private static WorkspaceLanguageServers.Owner languageOwner(
      String workspace, String project, String document, UserScope scope) {
    var owner = WorkspaceManager.projectLockOwner(scope);
    if (owner == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    return new WorkspaceLanguageServers.Owner(owner, workspace, project, document);
  }

  @PutMapping("/source")
  public SaveResult save(@RequestParam String workspace, @RequestParam String project,
                         @RequestParam String document, @RequestBody SourceEdit edit, Principal principal) {
    var scope = user(principal);
    project(workspace, project, scope);
    service().requireProjectOperation(project, CRUDOperation.UPDATE, scope);
    if (edit.text() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing source");
    return service().getWorkspaceManager().withProjectLock(project, scope, () -> {
      var current = document(project(workspace, project, scope), document);
      checkRevision(current, edit.revision());
      var type = ProjectStorage.ResourceType.classify(current);
      var changes = service().updateDocument(project, AuthoringSource.selector(current), type, edit.text(), false, scope);
      var updated = project(workspace, project, scope);
      var saved = updated.allDocuments().stream()
          .filter(candidate -> ProjectStorage.ResourceType.classify(candidate) == type)
          .filter(candidate -> Objects.equals(candidate.getSourceCode(), edit.text()))
          .filter(candidate -> id(candidate).equals(document)
              || changes.stream().flatMap(WorkspacesController::changedDocuments)
                  .anyMatch(change -> change.getOperation() != CRUDOperation.DELETE
                      && AuthoringSource.matches(change, candidate)))
          .findFirst().orElse(null);
      return new SaveResult(saved == null ? null : source(updated, saved), notifications(changes));
    });
  }

  @PutMapping("/project")
  public List<Diagnostic> saveSettings(@RequestParam String workspace, @RequestParam String project,
                                      @RequestBody SettingsEdit edit, Principal principal) {
    var scope = user(principal);
    project(workspace, project, scope);
    service().requireProjectOperation(project, CRUDOperation.UPDATE, scope);
    var manager = service().getWorkspaceManager();
    return manager.withProjectLock(project, scope, () -> {
      if (!settingsRevision(project(workspace, project, scope)).equals(edit.revision()))
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Project settings have changed.");
      return diagnostics(manager.replaceProjectSettings(workspace, project, edit.settings(), scope).getNotifications());
    });
  }

  @PostMapping("/repository")
  @Secured(Role.ADMINISTRATOR)
  public List<Diagnostic> repository(@RequestParam String workspace, @RequestParam String project,
                                    @RequestBody RepositoryEdit edit, Principal principal) {
    var scope = user(principal);
    var current = project(workspace, project, scope);
    service().requireProjectOperation(project, CRUDOperation.UPDATE, scope);
    if (!isAdministrator(principal) || !service().canAdministerProjectSettings(scope))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    if (edit.operation() == null || current.getRepositoryState() == null
        || current.getRepositoryState().getCurrentBranch() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Repository operation unavailable");
    }
    return service().getWorkspaceManager().withProjectLock(project, scope, () ->
        notifications(service().manageRepository(project, edit.operation(), scope, null,
            edit.parameter() == null || edit.parameter().isBlank()
                ? new String[0] : new String[] {edit.parameter()})));
  }

  @ExceptionHandler({KlabResourceAccessException.class, SecurityException.class})
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public void forbidden() {}

  private static boolean isAdministrator(Principal principal) {
    return principal instanceof EngineAuthorization authorization
        && authorization.getAuthorities().contains(Role.ROLE_ADMINISTRATOR);
  }

  private ResourcesProvider service() {
    return server.klabService();
  }

  private static UserScope user(Principal principal) {
    if (principal instanceof EngineAuthorization authorization
        && authorization.getScope() instanceof UserScope scope
        && scope.getUser() != null && !scope.getUser().isAnonymous()) return scope;
    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
  }

  private Project project(String workspace, String name, UserScope scope) {
    var visible = service().retrieveWorkspace(workspace, scope);
    if (visible != null) {
      for (var project : visible.getProjects()) {
        if (Objects.equals(project.getUrn(), name)) return project;
      }
    }
    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
  }

  private static String id(KlabDocument<?> document) {
    var path = AuthoringSource.id(document);
    return ProjectStorage.ResourceType.classify(document).name() + ":" + (path == null ? document.getUrn() : path);
  }

  private static KlabDocument<?> document(Project project, String id) {
    return project.allDocuments().stream().filter(source -> id(source).equals(id)).findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
  }

  private static DocumentItem item(KlabDocument<?> document) {
    var type = ProjectStorage.ResourceType.classify(document);
    var path = Optional.ofNullable(AuthoringSource.id(document))
        .orElseGet(() -> ProjectStorage.getRelativeFilePath(document.getUrn(), type, "/"));
    return new DocumentItem(id(document), AuthoringSource.label(document), document.getLanguage().languageId(),
        document.getLanguage().fileExtension(), path, type,
        document.getNotifications().stream().filter(n -> n.getLevel().severity >= Notification.Level.Error.severity).count(),
        document.getNotifications().stream().filter(n -> n.getLevel() == Notification.Level.Warning).count());
  }

  private Source source(Project project, KlabDocument<?> document) {
    var text = Objects.requireNonNullElse(document.getSourceCode(), "");
    return new Source(item(document), text, revision(document),
        diagnostics(document.getNotifications()), conceptClassifications(project, text));
  }

  private Map<String, String> conceptClassifications(Project project, String text) {
    var references = CONCEPT_REFERENCE.matcher(text).results().map(result -> result.group())
        .distinct().limit(3000).collect(java.util.stream.Collectors.toSet());
    if (references.isEmpty()) return Map.of();
    var namespaces = references.stream().map(urn -> urn.substring(0, urn.indexOf(':')))
        .collect(java.util.stream.Collectors.toSet());
    var concepts = new TreeMap<String, String>();
    var worldview = service().getWorkspaceManager().getWorldview();
    var ontologies = worldview == null ? Stream.<KimOntology>empty() : worldview.getOntologies().stream();
    // The project has already passed its read check. The installed worldview supplies shared declarations.
    Stream.concat(ontologies, project.getOntologies().stream())
        .filter(ontology -> namespaces.contains(ontology.getUrn()))
        .forEach(ontology -> classify(ontology.getUrn(), ontology.getStatements(), references, concepts));
    return concepts;
  }

  private static void classify(String namespace, List<KimConceptStatement> statements,
                               java.util.Set<String> references, Map<String, String> concepts) {
    for (var statement : statements) {
      var urn = namespace + ":" + statement.getUrn();
      var types = statement.getType();
      if (references.contains(urn) && !types.isEmpty()) {
        var type = SemanticType.fundamentalType(types);
        if (types.contains(SemanticType.DOMAIN)) type = SemanticType.DOMAIN;
        concepts.put(urn, SemanticType.TRAIT_TYPES.contains(type) ? "TRAIT" : type.name());
      }
      classify(namespace, statement.getChildren(), references, concepts);
    }
  }

  private static String settingsRevision(Project project) {
    return SemanticValidationRequest.sourceHash(Utils.Json.asString(project.getSettings()));
  }

  private static String revision(KlabDocument<?> document) {
    return SemanticValidationRequest.sourceHash(
        AuthoringSource.selector(document) + "\u0000" + document.getSourceCode());
  }

  private static void checkRevision(KlabDocument<?> document, String revision) {
    if (!revision(document).equals(revision))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "The source has changed. Reload it before saving.");
  }

  private static List<Diagnostic> diagnostics(java.util.Collection<Notification> notifications) {
    return notifications.stream().map(Diagnostic::of).distinct().toList();
  }

  private static List<Diagnostic> notifications(List<ResourceSet> changes) {
    return changes.stream().flatMap(change -> change.getNotifications().stream())
        .map(Diagnostic::of).distinct().toList();
  }

  private static Stream<ResourceSet.Resource> changedDocuments(ResourceSet changes) {
    return Stream.of(changes.getOntologies(), changes.getNamespaces(), changes.getObservationStrategies(),
        changes.getBehaviors(), changes.getResults()).flatMap(java.util.Collection::stream);
  }

  private static Revision revision(RepositoryState state) {
    if (state == null) return null;
    var files = new TreeMap<String, RepositoryState.Status>();
    // JGit reports uncommitted changes as a union. The IDE prefers the specific state.
    state.getUncommittedPaths().forEach(path -> files.put(path, RepositoryState.Status.MODIFIED));
    state.getRemovedPaths().forEach(path -> files.put(path, RepositoryState.Status.REMOVED));
    state.getModifiedPaths().forEach(path -> files.put(path, RepositoryState.Status.MODIFIED));
    state.getUntrackedPaths().forEach(path -> files.put(path, RepositoryState.Status.UNTRACKED));
    state.getAddedPaths().forEach(path -> files.put(path, RepositoryState.Status.ADDED));
    state.getConflictingPaths().forEach(path -> files.put(path, RepositoryState.Status.CONFLICTED));
    return new Revision(state.getCurrentBranch(), state.getBranchNames(), state.getOverallStatus(),
        state.getRepositoryUrl() != null, files, state.getUntrackedFolders().stream().sorted().toList());
  }
}
