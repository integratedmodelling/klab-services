package org.integratedmodelling.resources.server.controllers;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.IOException;
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
import org.integratedmodelling.klab.api.lang.kactors.KActorsAction;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.lang.kim.KimModel;
import org.integratedmodelling.klab.api.lang.kim.KlabStatement;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService.ProjectEditingLock;
import org.integratedmodelling.klab.api.services.ResourcesService.ProjectLockRequest;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest;
import org.integratedmodelling.klab.api.services.resources.AuthoringSource;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.settings.ProjectSettings;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.modeler.model.NavigableProject;
import org.integratedmodelling.klab.modeler.model.NavigableWorkspace;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;
import org.integratedmodelling.klab.services.resources.storage.ReviewSubmission;
import org.integratedmodelling.klab.services.resources.storage.WorkspaceManager;
import org.integratedmodelling.resources.server.ResourcesServer;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
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
  private final long lockIdleMillis;

  public WorkspacesController(ResourcesServer server, WorkspaceLanguageServers languageServers,
      @Value("${klab.resources.workspace-lock-idle-seconds:900}") long lockIdleSeconds) {
    this.server = server;
    this.languageServers = languageServers;
    if (lockIdleSeconds <= 0) throw new IllegalArgumentException("Workspace lock timeout must be positive");
    this.lockIdleMillis = Math.multiplyExact(lockIdleSeconds, 1000);
  }

  public record WorkspaceItem(String urn, List<String> projects,
                              Map<String, RepositoryState.Status> repositoryStatuses) {}

  public record DocumentItem(String id, String label, String language, String extension, String path,
                             ProjectStorage.ResourceType type, long errors, long warnings,
                             String sourceVersion, String urn) {}

  public record Revision(String branch, List<String> branches, RepositoryState.Status status,
                         boolean remote, Map<String, RepositoryState.Status> files,
                         List<String> untrackedFolders) {}

  public record ProjectView(String urn, String description, String worldview, String definedWorldview,
                            String version, boolean snapshot, ProjectEditingLock lock, boolean canManageRepository,
                            boolean administrator, Map<String, Object> metadata, String permissions,
                            String settingsRevision, Revision repository, List<DocumentItem> documents,
                            @JsonInclude(JsonInclude.Include.NON_NULL) List<NavigationNode> navigation) {}

  public record ProjectNavigation(String urn, Revision repository, List<NavigationNode> children) {}

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record NavigationNode(String label, String kind, DocumentItem document, Integer offset, Integer length,
                               List<NavigationNode> children) {}

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
                       Map<String, String> concepts, ProjectEditingLock lock) {}
  public record SourceEdit(String revision, String text, List<ReviewSubmission.Input> reviewInputs) {
    public SourceEdit(String revision, String text) { this(revision, text, null); }
  }
  public record Draft(String text) {}
  public record LanguageSession(UUID id) {}
  public record SettingsEdit(String revision, ProjectSettings settings) {}
  public record SaveResult(Source source, List<Diagnostic> notifications,
                           @JsonInclude(JsonInclude.Include.NON_NULL) String reviewWarning) {}
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

  @GetMapping("/navigation")
  public List<ProjectNavigation> navigation(@RequestParam String workspace, Principal principal) {
    var visible = service().retrieveWorkspace(workspace, user(principal));
    if (visible == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found");
    return new NavigableWorkspace(visible, false).children().stream()
        .map(NavigableProject.class::cast)
        .map(project -> new ProjectNavigation(project.getUrn(), revision(project.getRepositoryState()),
            navigationChildren(project)))
        .toList();
  }

  @GetMapping("/project")
  public ProjectView project(@RequestParam String workspace, @RequestParam String project,
                             @RequestParam(defaultValue = "false") boolean includeNavigation,
                             Principal principal) {
    var scope = user(principal);
    var current = project(workspace, project, scope);
    var manifest = current.getManifest();
    var canEdit = service().canProjectOperation(project, CRUDOperation.UPDATE, scope);
    var lock = lockAccess(project, scope, canEdit);
    var administrator = service().canAdministerProjectSettings(scope);
    var repository = current.getRepositoryState();
    return new ProjectView(project,
        manifest == null ? null : manifest.getDescription(), manifest == null ? null : manifest.getWorldview(),
        manifest == null ? null : manifest.getDefinedWorldview(),
        manifest == null || manifest.getVersion() == null ? null : manifest.getVersion().toString(),
        manifest != null && manifest.isSnapshot(), lock,
        administrator && isAdministrator(principal) && canEdit && repository != null && repository.getCurrentBranch() != null,
        administrator, current.getSettings().getMetadata(),
        canEdit ? current.getSettings().getPermissions() : null,
        settingsRevision(current), revision(repository),
        current.allDocuments().stream().map(WorkspacesController::item).toList(),
        includeNavigation ? navigationChildren(new NavigableProject(current, null)) : null);
  }

  private static List<NavigationNode> navigationChildren(NavigableAsset asset) {
    return asset.children().stream().map(child -> {
      var delegate = child.getDelegate();
      if (delegate instanceof KlabDocument<?> document) {
        return new NavigationNode(AuthoringSource.label(document), "document", item(document), null, null,
            navigationChildren(child));
      }
      if (delegate instanceof KlabStatement statement) {
        String kind = switch (statement) {
          case KimConceptStatement ignored -> "concept";
          case KimModel ignored -> "model";
          case KActorsAction ignored -> "action";
          default -> "statement";
        };
        return new NavigationNode(child.getUrn(), kind, null, statement.getOffsetInDocument(), statement.getLength(),
            navigationChildren(child));
      }
      return new NavigationNode(child.getUrn(), "folder", null, null, null, navigationChildren(child));
    }).toList();
  }

  @GetMapping("/project/lock")
  public ProjectEditingLock projectLock(@RequestParam String workspace, @RequestParam String project, Principal principal) {
    var scope = lockScope(workspace, project, principal);
    return lockAccess(project, scope, service().canProjectOperation(project, CRUDOperation.UPDATE, scope));
  }

  @PostMapping("/project/lock")
  public ProjectEditingLock acquireLock(@RequestParam String workspace, @RequestParam String project, Principal principal) {
    var scope = lockScope(workspace, project, principal);
    service().requireProjectOperation(project, CRUDOperation.UPDATE, scope);
    service().getWorkspaceManager().acquireProjectEditLease(project, scope, lockIdleMillis);
    return lockAccess(project, scope, true);
  }

  @PutMapping("/project/lock")
  public ProjectEditingLock renewLock(@RequestParam String workspace, @RequestParam String project, Principal principal) {
    var scope = lockScope(workspace, project, principal);
    var allowed = service().canProjectOperation(project, CRUDOperation.UPDATE, scope);
    if (allowed) service().getWorkspaceManager().renewProjectEditLease(project, scope);
    return lockAccess(project, scope, allowed);
  }

  @DeleteMapping("/project/lock")
  public ProjectEditingLock releaseLock(@RequestParam String workspace, @RequestParam String project, Principal principal) {
    var scope = lockScope(workspace, project, principal);
    service().getWorkspaceManager().unlockProject(project, scope);
    return lockAccess(project, scope, service().canProjectOperation(project, CRUDOperation.UPDATE, scope));
  }

  @PostMapping("/project/edit-lock")
  public ProjectEditingLock editLock(@RequestBody ProjectLockRequest request, Principal principal) {
    if (request.workspace() == null || request.project() == null || request.action() == null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workspace, project and lock action are required.");
    return switch (request.action()) {
      case STATUS -> projectLock(request.workspace(), request.project(), principal);
      case ACQUIRE -> acquireLock(request.workspace(), request.project(), principal);
      case RENEW -> renewLock(request.workspace(), request.project(), principal);
      case RELEASE -> releaseLock(request.workspace(), request.project(), principal);
    };
  }

  private UserScope lockScope(String workspace, String project, Principal principal) {
    var scope = user(principal);
    if (!service().canReadWorkspaceProject(workspace, project, scope))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
    return scope;
  }

  private ProjectEditingLock lockAccess(String project, UserScope scope, boolean canEdit) {
    var state = service().getWorkspaceManager().projectLockStatus(project, scope);
    return new ProjectEditingLock(canEdit && state.supported() && state.owned(),
        canEdit && state.supported() && !state.locked(), state.locked(), state.owned(),
        state.idleTimeoutMillis(), state.remainingMillis());
  }

  @GetMapping("/source")
  public Source source(@RequestParam String workspace, @RequestParam String project,
                       @RequestParam String document, Principal principal) {
    var scope = user(principal);
    var current = project(workspace, project, scope);
    return source(current, document(current, document),
        lockAccess(project, scope, service().canProjectOperation(project, CRUDOperation.UPDATE, scope)));
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
    var current = writableDocument(workspace, project, document, scope);
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
    if (!service().getWorkspaceManager().projectLockStatus(project, scope).owned())
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
    var manager = service().getWorkspaceManager();
    return manager.withOwnedProjectLock(project, scope, () -> {
      var current = document(project(workspace, project, scope), document);
      checkRevision(current, edit.revision());
      var type = ProjectStorage.ResourceType.classify(current);
      WorkspaceManager.ReviewApplication application = null;
      if (edit.reviewInputs() != null) {
        if (type != ProjectStorage.ResourceType.ONTOLOGY || edit.text().equals(current.getSourceCode()))
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A review application must change an ontology");
        try {
          application = manager.prepareReviewApplication(project, current.getUrn(), edit.revision(),
              current.getSourceCode(), edit.reviewInputs(), scope);
        } catch (IllegalArgumentException invalid) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage(), invalid);
        } catch (IOException unavailable) {
          throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "Review history is unavailable", unavailable);
        }
      }
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
      var savedSource = saved == null ? null : source(updated, saved, null);
      String reviewWarning = null;
      if (savedSource != null && application != null) {
        try {
          application.record(savedSource.revision(), savedSource.text());
        } catch (IOException failure) {
          org.slf4j.LoggerFactory.getLogger(WorkspacesController.class)
              .error("Ontology saved but review decision could not be staged for {}/{} ({} to {})",
                  project, current.getUrn(), edit.revision(), savedSource.revision(), failure);
          reviewWarning = "The candidate was saved. Resources could not finish recording and staging its review decision.";
        }
      }
      return new SaveResult(savedSource, notifications(changes), reviewWarning);
    });
  }

  @PutMapping("/project")
  public List<Diagnostic> saveSettings(@RequestParam String workspace, @RequestParam String project,
                                      @RequestBody SettingsEdit edit, Principal principal) {
    var scope = user(principal);
    project(workspace, project, scope);
    service().requireProjectOperation(project, CRUDOperation.UPDATE, scope);
    var manager = service().getWorkspaceManager();
    return manager.withOwnedProjectLock(project, scope, () -> {
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
    return service().getWorkspaceManager().withOwnedProjectLock(project, scope, () ->
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
        document.getNotifications().stream().filter(n -> n.getLevel() == Notification.Level.Warning).count(),
        Objects.toString(document.getMetadata().get(AuthoringSource.VERSION),
            Long.toString(document.getLastUpdateTimestamp())), document.getUrn());
  }

  private Source source(Project project, KlabDocument<?> document, ProjectEditingLock lock) {
    var text = Objects.requireNonNullElse(document.getSourceCode(), "");
    return new Source(item(document), text, revision(document),
        diagnostics(document.getNotifications()), conceptClassifications(project, text), lock);
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
