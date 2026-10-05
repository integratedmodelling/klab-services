package org.integratedmodelling.klab.services.resources.workflow;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import org.integratedmodelling.klab.api.lang.kactors.KActorsAction;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.resources.workflow.*;
import org.integratedmodelling.klab.runtime.kactors.RuntimeAgentBase;
import org.integratedmodelling.klab.runtime.kactors.compiler.AgentCompiler;
import org.integratedmodelling.klab.runtime.kactors.compiler.runtime.AgentRegistry;

/** One isolated agent per mutation: persistence, not a live actor, is the source of truth. */
public class WorkflowBehaviorBridge {
  private final Supplier<AgentCompiler.Resolver> resolvers;
  private final Function<String, UserScope> owners;

  public WorkflowBehaviorBridge() { this(() -> new AgentCompiler.Resolver() {}, owner -> null); }
  public WorkflowBehaviorBridge(Supplier<AgentCompiler.Resolver> resolvers,
      Function<String, UserScope> owners) {
    this.resolvers = resolvers;
    this.owners = owners;
  }

  /** Read-only editor facade, freshly bound for each call; no UI object or mutation verbs. */
  public record Editor(String flowId, String stageId, String title, String description,
      String owner, long revision, List<String> attachmentNames) {}

  /** The manager stages payload changes in the same operation as the behavior checkpoint. */
  public interface Attachments {
    Flow.Attachment add(Flow.State stage, Flow.AttachmentUpload upload);
    byte[] read(Flow.State stage, String id);
    boolean remove(Flow.State stage, String id);
  }

  /** Content mutation agent; fluent return values keep Java calls finite. */
  public static final class Content {
    private final Flow.State stage;
    private final Attachments attachments;

    private Content(Flow.State stage, Attachments attachments) { this.stage = stage; this.attachments = attachments; }
    public List<Flow.Attachment> attachments() { return List.copyOf(stage.getAttachments()); }
    public byte[] attachment(String id) { return attachmentAccess().read(stage, id); }
    public boolean removeAttachment(String id) { return attachmentAccess().remove(stage, id); }
    public Flow.Attachment attachText(String type, String name, String mediaType, String text) {
      return attachBytes(type, name, mediaType, text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    public Flow.Attachment attachBytes(String type, String name, String mediaType, byte[] bytes) {
      return attachBytes(type, name, mediaType, null, bytes);
    }
    public Flow.Attachment attachBytes(String type, String name, String mediaType, String assetType, byte[] bytes) {
      var upload = Flow.AttachmentUpload.create();
      upload.setType(type); upload.setFileName(name); upload.setMediaType(mediaType);
      if (assetType != null) upload.setAssetType(org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.valueOf(assetType.toUpperCase(Locale.ROOT)));
      upload.setContent(bytes == null ? null : bytes.clone());
      return attachmentAccess().add(stage, upload);
    }
    public Flow.Attachment attachFile(String type, String path, String mediaType) {
      return attachBytes(type, java.nio.file.Path.of(path).getFileName().toString(), mediaType,
          WorkflowAttachmentSources.file(path, byteLimit(mediaType)));
    }
    public Flow.Attachment attachUrl(String type, String url, String name, String mediaType) {
      return attachBytes(type, name, mediaType, WorkflowAttachmentSources.url(url, byteLimit(mediaType)));
    }
    private int byteLimit(String mediaType) {
      int limit = "application/vnd.klab.proposal+yaml".equals(mediaType) ? ProposalReview.MAX_PROPOSAL_BYTES
          : "application/vnd.klab.ontology".equals(mediaType) ? ProposalReview.MAX_ONTOLOGY_BYTES : ProposalReview.MAX_UPLOAD_BYTES;
      return (int) Math.max(0, Math.min(limit, ProposalReview.MAX_STAGE_BYTES
          - stage.getAttachments().stream().mapToLong(Flow.Attachment::getSize).sum()));
    }
    private Attachments attachmentAccess() {
      if (attachments == null) throw new IllegalStateException("No workflow attachment transaction is bound");
      return attachments;
    }
    public String title(String value) { stage.setTitle(value); return value; }
    public String description(String value) { stage.setDescription(value); return value; }
    public Object metadata(String key, Object value) {
      RuntimeAgentBase.portableState(value);
      stage.getMetadata().put(key, value);
      return value;
    }
    public Object value(String key) { return stage.getMetadata().get(key); }
  }

  public Session open(Workflow workflow, Flow flow, Flow.State stage, UserScope caller) {
    return open(workflow, flow, stage, caller, null);
  }

  public Session open(Workflow workflow, Flow flow, Flow.State stage, UserScope caller, Attachments attachments) {
    return new Session(workflow, flow, stage, caller, attachments);
  }

  public final class Session implements AutoCloseable {
    private final Workflow workflow;
    private final Flow flow;
    private final UserScope caller;
    private final UserScope owner;
    private final AgentCompiler.Resolver resolver;
    private final KActorsBehavior behavior;
    private final Map<String, KActorsAction> actions = new LinkedHashMap<>();
    private RuntimeAgentBase runtime;
    private final Attachments attachments;
    private Map<String, Object> targetActors;

    private Session(Workflow workflow, Flow flow, Flow.State stage, UserScope caller, Attachments attachments) {
      this.attachments = attachments;
      this.workflow = workflow;
      this.flow = flow;
      this.caller = caller;
      this.owner = Objects.equals(flow.getOwner(), caller.getUser().getUsername())
          ? caller : owners.apply(flow.getOwner());
      if (owner == null) throw new IllegalStateException("Workflow owner's scope is unavailable; owner must reconnect");
      resolver = resolvers.get();
      behavior = resolver.resolveBehavior(workflow.getBehavior(), owner);
      if (behavior == null || behavior.getBehaviorType() != KActorsBehavior.Type.BEHAVIOR)
        throw new IllegalArgumentException("Workflow requires a resolvable behavior category: " + workflow.getBehavior());
      collectActions(behavior, new HashSet<>());
      var checkpoint = flow.getBehaviorCheckpoint();
      if (checkpoint != null && (!Objects.equals(checkpoint.behaviorUrn(), behavior.getUrn())
          || !Objects.equals(checkpoint.version(), Objects.toString(behavior.getVersion(), ""))))
        throw new IllegalStateException("Behavior version changed; explicit checkpoint migration is required");
      validateBindings();
    }

    private void collectActions(KActorsBehavior source, Set<String> seen) {
      if (!seen.add(source.getUrn())) return;
      for (var action : source.getStatements()) actions.putIfAbsent(action.getUrn(), action);
      for (var inherited : source.getInheritedBehaviors()) {
        var parent = resolver.resolveBehavior(inherited.getImportedBehavior(), owner);
        if (parent == null) throw new IllegalArgumentException("Unresolved inherited workflow behavior");
        collectActions(parent, seen);
      }
    }

    private void validateBindings() {
      var bindings = new ArrayList<WorkflowBehavior.Action>();
      workflow.getStates().values().forEach(state -> {
        bindings.addAll(state.getOnStart()); bindings.addAll(state.getOnCommit());
        bindings.addAll(state.getActions());
      });
      workflow.getTransitions().values().forEach(transition -> bindings.addAll(transition.getActions()));
      for (var binding : bindings) {
        var action = action(binding.action());
        var names = action.getArguments().stream().map(KActorsAction.Argument::getName).toList();
        if (!names.containsAll(binding.parameters().keySet()))
          throw new IllegalArgumentException("Unknown configured parameter for " + binding.action());
      }
    }

    private KActorsAction action(String name) {
      var ret = actions.get(name);
      if (ret == null) throw new IllegalArgumentException("Unknown workflow behavior action " + name);
      return ret;
    }

    private Map<String, Object> context(Flow.State stage, Workflow.TransitionSchema transition) {
      var context = new LinkedHashMap<String, Object>();
      context.put("workflow", workflow);
      context.put("flow", flow);
      context.put("stage", stage);
      context.put("content", new Content(stage, attachments));
      context.put("transition", transition);
      context.put("user", caller.getUser());
      context.put("participant", WorkflowParticipant.from(caller));
      context.put("editor", new Editor(flow.getId(), stage.getId(), stage.getTitle(),
          stage.getDescription(), stage.getOwner(), flow.getRevision(),
          stage.getAttachments().stream().map(Flow.Attachment::getFileName).toList()));
      context.putAll(targetActors());
      return context;
    }

    private Map<String, Object> targetActors() {
      if (targetActors != null) return targetActors;
      targetActors = new LinkedHashMap<>();
      boolean needed = actions.values().stream().flatMap(action -> action.getArguments().stream()).anyMatch(argument -> {
        var contract = org.integratedmodelling.klab.api.lang.kactors.KActorsVisitor.actionArgumentType(argument);
        String javaName = contract == null || contract.javaClassName() == null ? "" : contract.javaClassName().replace('$', '.');
        javaName = javaName.substring(javaName.lastIndexOf('.') + 1);
        return Set.of("document", "project", "ontology", "namespace", "strategy_document", "behavior_document").contains(argument.getName())
            || contract != null && (contract.behaviorUrn() != null && Set.of("core.document", "core.project", "core.ontology", "core.namespace", "core.strategy_document", "core.behavior_document").contains(contract.behaviorUrn())
                || Set.of("Document", "Project", "Ontology", "Namespace", "StrategyDocument", "BehaviorDocument").contains(javaName));
      });
      if (!needed) return targetActors;
      var services = new LinkedHashSet<>(owner.getServices(org.integratedmodelling.klab.api.services.ResourcesService.class));
      var preferred = owner.getService(org.integratedmodelling.klab.api.services.ResourcesService.class);
      if (preferred != null) services.add(preferred);
      for (var service : services) {
        if (flow.getAssetType() == org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.PROJECT) {
          var project = service.retrieve(flow.getAssetUrn(), org.integratedmodelling.klab.api.knowledge.organization.Project.class, owner);
          if (project != null) {
            targetActors.put("project", new org.integratedmodelling.klab.runtime.libraries.CoreActorLibrary.Project(project.getUrn(), service.serviceId())); break;
          }
        } else if (flow.getAssetType() != null) {
          var type = flow.getAssetType() == org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.COMPONENT
              ? org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior.class : flow.getAssetType().getAssetClass();
          if (!org.integratedmodelling.klab.api.lang.kim.KlabDocument.class.isAssignableFrom(type)) break;
          var asset = service.retrieve(flow.getAssetUrn(), type, owner);
          if (asset instanceof org.integratedmodelling.klab.api.lang.kim.KlabDocument<?> document && document.getProjectName() != null) {
            var wrapper = org.integratedmodelling.klab.runtime.libraries.CoreActorLibrary.Document.documentReference(
                document.getUrn(), document.getProjectName(), service.serviceId(), org.integratedmodelling.klab.api.knowledge.KlabAsset.classify(document));
            targetActors.put("document", wrapper);
            targetActors.put(switch (wrapper) {
              case org.integratedmodelling.klab.runtime.libraries.CoreActorLibrary.Ontology ignored -> "ontology";
              case org.integratedmodelling.klab.runtime.libraries.CoreActorLibrary.Namespace ignored -> "namespace";
              case org.integratedmodelling.klab.runtime.libraries.CoreActorLibrary.StrategyDocument ignored -> "strategy_document";
              default -> "behavior_document";
            }, wrapper);
            targetActors.put("project", wrapper.project()); break;
          }
        }
      }
      return targetActors;
    }

    private void start(Flow.State stage, Workflow.TransitionSchema transition) {
      if (runtime != null) return;
      var checkpoint = flow.getBehaviorCheckpoint();
      Object[] init = checkpoint == null && actions.containsKey("init")
          ? bind(action("init"), Map.of(), context(stage, transition), null) : new Object[0];
      runtime = AgentRegistry.INSTANCE.checkpointAgent(behavior, owner, resolver,
          checkpoint == null ? null : checkpoint.globals(), init, caller);
      if (checkpoint == null && actions.containsKey("main"))
        runtime.invokeCheckpointAction("main", bind(action("main"), Map.of(), context(stage, transition), null));
    }

    public void invoke(List<WorkflowBehavior.Action> bindings, Flow.State stage,
        Workflow.TransitionSchema transition) {
      // Bind every action before invoking any to catch missing inputs without partial action effects.
      for (var binding : bindings)
        bind(action(binding.action()), binding.parameters(), context(stage, transition), null);
      start(stage, transition);
      for (var binding : bindings)
        runtime.invokeCheckpointAction(binding.action(),
            bind(action(binding.action()), binding.parameters(), context(stage, transition), null));
    }

    public List<WorkflowBehavior.AvailableAction> available(Flow.State stage) {
      var ret = new ArrayList<WorkflowBehavior.AvailableAction>();
      for (var binding : workflow.getStates().get(stage.getSchemaId()).getActions()) {
        var missing = new ArrayList<WorkflowBehavior.Parameter>();
        bind(action(binding.action()), binding.parameters(), context(stage, null), missing);
        ret.add(new WorkflowBehavior.AvailableAction(binding.id(), binding.label(), binding.action(), missing, flow.getRevision()));
      }
      return ret;
    }

    public void execute(WorkflowBehavior.Action binding, Flow.State stage, Map<String, Object> supplied) {
      var missing = new ArrayList<WorkflowBehavior.Parameter>();
      bind(action(binding.action()), binding.parameters(), context(stage, null), missing);
      var allowed = missing.stream().map(WorkflowBehavior.Parameter::name).toList();
      if (!allowed.containsAll(supplied.keySet()))
        throw new IllegalArgumentException("Only unresolved action parameters may be supplied");
      var parameters = new LinkedHashMap<>(binding.parameters());
      parameters.putAll(supplied);
      var arguments = bind(action(binding.action()), parameters, context(stage, null), null);
      start(stage, null);
      runtime.invokeCheckpointAction(binding.action(), arguments);
      checkpoint(stage);
    }

    public void checkpoint(Flow.State stage) {
      if (runtime == null) throw new IllegalStateException("Behavior has not started");
      var checkpoint = new WorkflowBehavior.Checkpoint(behavior.getUrn(),
          Objects.toString(behavior.getVersion(), ""), runtime.checkpointState());
      flow.setBehaviorCheckpoint(checkpoint);
      stage.setBehaviorCheckpoint(checkpoint);
    }

    @Override public void close() { if (runtime != null) runtime.stop(); }
  }

  static Object[] bind(KActorsAction action, Map<String, Object> configured,
      Map<String, Object> context, List<WorkflowBehavior.Parameter> missing) {
    var ret = new ArrayList<Object>();
    for (var argument : action.getArguments()) {
      String name = argument.getName();
      var contract = org.integratedmodelling.klab.api.lang.kactors.KActorsVisitor.actionArgumentType(argument);
      String javaType = contract == null ? null : contract.javaClassName();
      String behaviorType = contract == null ? null : contract.behaviorUrn();
      Object value = null;
      boolean found = false;
      if (context.containsKey(name)) { value = context.get(name); found = true; }
      else if (configured.containsKey(name)) { value = configured.get(name); found = true; }
      else if (javaType != null) {
        String type = javaType;
        var matches = context.values().stream().distinct().filter(Objects::nonNull)
            .filter(candidate -> matches(candidate.getClass(), type)).toList();
        if (matches.size() > 1) throw new IllegalArgumentException("Ambiguous context type for " + name);
        if (matches.size() == 1) { value = matches.getFirst(); found = true; }
      }
      else if (behaviorType != null) {
        var matches = context.values().stream().filter(Objects::nonNull).distinct()
            .filter(candidate -> actorMatches(candidate.getClass(), behaviorType)).toList();
        if (matches.size() > 1) throw new IllegalArgumentException("Ambiguous actor type for " + name);
        if (matches.size() == 1) { value = matches.getFirst(); found = true; }
      }
      if (found && value instanceof Number number && javaType != null)
        value = numberForType(number, javaType);
      if (found && value != null && javaType != null && !matches(value.getClass(), javaType))
        throw new IllegalArgumentException("Wrong Java type for action parameter " + name + ": " + javaType);
      if (!found) {
        if (missing == null) throw new IllegalArgumentException("Missing workflow action parameter " + name);
        missing.add(new WorkflowBehavior.Parameter(name, javaType, behaviorType));
      }
      ret.add(value);
    }
    return ret.toArray();
  }

  private static Object numberForType(Number number, String type) {
    String normalized = type.startsWith("java.lang.") ? type.substring(10) : type;
    try {
      var decimal = new java.math.BigDecimal(number.toString());
      return switch (normalized.toLowerCase(Locale.ROOT)) {
        case "byte" -> decimal.byteValueExact();
        case "short" -> decimal.shortValueExact();
        case "int", "integer" -> decimal.intValueExact();
        case "long" -> decimal.longValueExact();
        case "float" -> {
          float converted = decimal.floatValue();
          if (!Float.isFinite(converted)) throw new ArithmeticException("Numeric overflow");
          yield converted;
        }
        case "double" -> {
          double converted = decimal.doubleValue();
          if (!Double.isFinite(converted)) throw new ArithmeticException("Numeric overflow");
          yield converted;
        }
        default -> number;
      };
    } catch (ArithmeticException failure) {
      throw new IllegalArgumentException("Invalid numeric value for " + type, failure);
    }
  }

  private static boolean matches(Class<?> actual, String type) {
    if (actual.getName().equals(type) || actual.getSimpleName().equalsIgnoreCase(type)
        || (actual == Integer.class && "int".equals(type))
        || (actual == Character.class && "char".equals(type))) return true;
    for (var implemented : actual.getInterfaces()) if (matches(implemented, type)) return true;
    return actual.getSuperclass() != null && matches(actual.getSuperclass(), type);
  }

  private static boolean actorMatches(Class<?> actual, String type) {
    var actor = actual.getAnnotation(org.integratedmodelling.klab.api.services.runtime.extension.Actor.class);
    return actor != null && ("core." + actor.name()).equals(type)
        || actual.getSuperclass() != null && actorMatches(actual.getSuperclass(), type);
  }
}
