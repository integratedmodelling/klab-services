package org.integratedmodelling.klab.services.runtime.digitaltwin;

import java.util.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;

/** A prepared semantic layer change, usable only inside the private modeled-cessation protocol.
 * The native readset and cessation origin set must both be checked by that protocol; this is not
 * a public remove-role capability. All semantic work is completed before native commit locking. */
public final class PreparedRoleWithdrawal {
  public record Support(RolePresenceKey key,long sourceId,String ruleId,long activityId,
      boolean acquired,String before,String after,String authority,boolean current) {}
  public record Ledger(List<Support> supports,Set<String> independentlySuppliedRoles) {
    public Ledger {supports=List.copyOf(supports);independentlySuppliedRoles=Set.copyOf(independentlySuppliedRoles);}
  }
  private final Observation original,replacement;
  private final KnowledgeGraph.ConsequenceReadSet reads;
  private final Set<Long> ceasedOrigins;
  private final Ledger before,after;
  private final Set<RolePresenceKey> exhausted;
  private final SemanticDependencyRequest request;
  private final SemanticDependencyWitness witness;
  private final List<SemanticEvidenceBinding> sourceBindings;
  private final ResolutionCycleBinding cycle;
  private final Object owner;
  private final String user;
  private final long observer,focus;

  private PreparedRoleWithdrawal(Observation original,Observation replacement,KnowledgeGraph.ConsequenceReadSet reads,
      Set<Long> ceasedOrigins,Ledger before,Ledger after,Set<RolePresenceKey> exhausted,
      SemanticDependencyRequest request,SemanticDependencyWitness witness,List<SemanticEvidenceBinding> sourceBindings,
      ServiceContextScope scope) {
    this.original=detached(original);this.replacement=detached(replacement);this.reads=reads;
    this.ceasedOrigins=Set.copyOf(ceasedOrigins);this.before=before;this.after=after;this.exhausted=Set.copyOf(exhausted);
    this.request=request;this.witness=witness;this.sourceBindings=List.copyOf(sourceBindings);
    this.cycle=Objects.requireNonNull(scope.getResolutionCycleBinding());this.owner=Objects.requireNonNull(scope.getResolutionCycle());
    this.user=scope.getUser().getUsername();this.observer=scope.getObserverId()==null?0:scope.getObserverId();
    this.focus=scope.getContextObservation()==null?0:scope.getContextObservation().getId();
  }
  public Observation original(){return detached(original);}
  public Observation replacement(){return detached(replacement);}
  public KnowledgeGraph.ConsequenceReadSet reads(){return reads;}
  public Set<Long> ceasedOrigins(){return ceasedOrigins;}
  public Ledger before(){return before;}
  public Ledger after(){return after;}
  public Set<RolePresenceKey> exhaustedRoleKeys(){return exhausted;}
  public SemanticDependencyRequest semanticRequest(){return request;}
  public SemanticDependencyWitness semanticWitness(){return witness;}

  /** Must run outside all native locks; the caller then validates reads() in its exact write TX. */
  public void require(ServiceContextScope scope) {
    if(scope.getResolutionCycle()!=owner||!cycle.equals(scope.getResolutionCycleBinding())
        ||!Objects.equals(user,scope.getUser().getUsername())||!Objects.equals(reads.contextId(),scope.getId())
        ||observer!=(scope.getObserverId()==null?0:scope.getObserverId())
        ||focus!=(scope.getContextObservation()==null?0:scope.getContextObservation().getId()))
      throw new IllegalStateException("Role withdrawal left its owned semantic cycle or perspective");
    var reasoner=scope.getService(Reasoner.class);
    for(var binding:sourceBindings)revalidate(binding,reasoner,scope);
    var current=reasoner.semanticDependencies(request,scope);
    requireWitness(current,request,scope);
    if(!SemanticDependencyWitness.revalidates(witness,current))throw new IllegalStateException("Role withdrawal semantic proof changed");
  }

  /** Package-private preparation seam: no caller-supplied list/boolean becomes source authority.
   * A private modeled-cessation owner must independently match ceasedOrigins to its actual plan. */
  static PreparedRoleWithdrawal prepare(long recipientId,Set<Long> ceasedOrigins,
      KnowledgeGraph.ConsequenceReadSet reads,ServiceContextScope scope) {
    if(recipientId<=0||ceasedOrigins.isEmpty()||ceasedOrigins.stream().anyMatch(id->id==null||id<=0)
        ||ceasedOrigins.contains(recipientId)||!Objects.equals(reads.contextId(),scope.getId())
        ||scope.getResolutionCycle()==null||scope.getResolutionCycleBinding()==null)
      throw new IllegalStateException("Role withdrawal requires a live recipient and owned cessation cycle");
    if(!reads.selections().contains(new KnowledgeGraph.ConsequenceEdgeSelection(recipientId,
        GraphModel.Relationship.CLASSIFIED,GraphModel.Relationship.Direction.INCOMING)))
      throw new IllegalStateException("Complete native attribution support is missing");
    var nativeRecipient=asset(reads,recipientId);
    if(Boolean.FALSE.equals(nativeRecipient.properties().get("klabCurrent")))throw new IllegalStateException("Recipient is already inoperative");
    var original=scope.getDigitalTwin().getKnowledgeGraph().materializeConsequenceObservation(nativeRecipient,scope);
    if(original==null||original.getId()!=recipientId||original.getObservable()==null
        ||!SemanticType.isEnumerableSubstantial(original.getObservable().getSemantics().getType()))
      throw new IllegalStateException("Role withdrawal must preserve its actual substantial recipient");
    var reasoner=scope.getService(Reasoner.class);var supports=new ArrayList<Support>();
    var bindings=new ArrayList<SemanticEvidenceBinding>();var protectedRoles=new TreeSet<String>();
    var acquiredRoles=new TreeSet<String>();var roles=new TreeMap<String,Concept>();
    for(var edge:reads.edges()) {
      if(edge.relationship()!=GraphModel.Relationship.CLASSIFIED||!Objects.equals(edge.target().id(),recipientId))continue;
      if(!(edge.source().id() instanceof Number activityId)||!edge.source().labels().contains(GraphModel.Labels.ACTIVITY))
        throw new IllegalStateException("Attribution support lacks its actual native Activity");
      var activity=asset(reads,activityId.longValue());var fields=activity.nativeProperties();
      if(!Activity.Outcome.SUCCESS.name().equals(fields.get(GraphModel.Fields.OUTCOME)))
        throw new IllegalStateException("Attribution support Activity did not succeed");
      var metadata=map(fields.get(GraphModel.Fields.METADATA));var properties=edge.properties();
      var binding=binding(metadata);revalidate(binding,reasoner,scope);bindings.add(binding);
      if(properties.containsKey("sourceId")) {
        if(!Activity.Type.CLASSIFICATION.name().equals(fields.get(GraphModel.Fields.TYPE))
            ||!ConsequenceRoleSupportResult.CONTRACT.equals(properties.get("supportContract")))
          throw new IllegalStateException("Legacy consequence support is incomplete; cessation must defer");
        var row=matchingRow(metadata.get(RoleAttributionEvents.METADATA_KEY),properties,
            List.of("sourceId","ruleId","role","before","after","authority","supportContract","acquired",
                "contextId","observerId","focusId","recipientObserverSemantics"));
        if(!Objects.equals(integer(row.get("recipientId")),recipientId))throw new IllegalStateException("Attribution row names another recipient");
        long source=positive(properties.get("sourceId"));var sourceAsset=asset(reads,source);
        String roleUrn=text(properties.get("role"));var role=reasoner.resolveConcept(roleUrn);
        if(role==null||!role.is(SemanticType.ROLE))throw new IllegalStateException("Stored role is no longer available");roles.put(roleUrn,role);
        var key=new RolePresenceKey(text(properties.get("contextId")),nonnegative(properties.get("observerId")),
            nonnegative(properties.get("focusId")),recipientId,roleUrn,Objects.toString(properties.get("recipientObserverSemantics"),null));
        var observerMeaning=original.getObservable().getObserverSemantics();
        if(!Objects.equals(key.contextId(),reads.contextId())||!Objects.equals(key.recipientObserverSemantics(),observerMeaning==null?"":observerMeaning.getUrn()))
          throw new IllegalStateException("Stored role support has another semantic qualification");
        var cause=map(metadata.get(org.integratedmodelling.klab.api.digitaltwin.Consequence.METADATA_KEY));
        if(!Objects.equals(cause.get("contextId"),key.contextId())||!Objects.equals(integer(cause.get("observerId")),key.observerId())
            ||!("ROLE_CONFERRAL".equals(cause.get("kind"))||"ROLE_IMPLICATION".equals(cause.get("kind"))))
          throw new IllegalStateException("Role support differs from its original consequence perspective");
        if(!(properties.get("acquired") instanceof Boolean acquired))throw new IllegalStateException("Role acquisition evidence is missing");
        var before=text(properties.get("before"));var after=text(properties.get("after"));
        if(!binding.obligations().containsAll(List.of(roleUrn,before,after)))
          throw new IllegalStateException("Role support accounting omits its actual before/after meanings");
        var oldMeaning=reasoner.resolveObservable(before);var newMeaning=reasoner.resolveObservable(after);
        if(oldMeaning==null||newMeaning==null||reasoner.hasRole(oldMeaning,role)==acquired||!reasoner.hasRole(newMeaning,role)
            ||!acquired&&!before.equals(after))throw new IllegalStateException("Role support acquisition contradicts its actual meanings");
        var authority=text(properties.get("authority"));var authorityData=map(authority);
        if(!(authorityData.get("rule") instanceof Map<?,?> sourceRule)||!Objects.equals(sourceRule.get("ruleId"),properties.get("ruleId")))
          throw new IllegalStateException("Role support lost its original admitted rule identity");
        if(acquired)acquiredRoles.add(roleUrn);
        supports.add(new Support(key,source,text(properties.get("ruleId")),activityId.longValue(),acquired,before,after,authority,
            !Boolean.FALSE.equals(sourceAsset.properties().get("klabCurrent"))&&frameCurrent(key,reads,scope)));
      } else {
        // Ordinary classifier support remains independent of consequence origins.
        var row=matchingRow(metadata.get(Metadata.IM_ATTRIBUTIONS),properties,List.of("predicate","before","after"));
        if(!Objects.equals(integer(row.get("memberId")),recipientId))throw new IllegalStateException("Classifier row names another recipient");
        var predicate=reasoner.resolveConcept(text(row.get("predicate")));
        if(predicate==null)throw new IllegalStateException("Independent classifier meaning is unavailable");
        if(predicate.is(SemanticType.ROLE))protectedRoles.add(predicate.getUrn());
      }
    }
    if(supports.isEmpty())throw new IllegalStateException("No typed consequence role supports were captured");
    // If a role was already present at every recorded origin, no consequence acquired it.
    for(var role:roles.keySet())if(!acquiredRoles.contains(role))protectedRoles.add(role);
    var independentBuilder=original.getObservable().builder(scope).without(roles.values());
    var independent=independentBuilder.buildObservable();
    if(independent==null||Utils.Notifications.hasErrors(independentBuilder.getNotifications())
        ||!reasoner.satisfiable(independent.getSemantics()))throw new IllegalStateException("Independent recipient meaning cannot be established");
    for(var entry:roles.entrySet()) {
      if(reasoner.hasRole(independent,entry.getValue())||protectedRoles.stream().map(urn->{
          var value=reasoner.resolveConcept(urn);if(value==null)throw new IllegalStateException("Independent role meaning is unavailable");return value;
        }).anyMatch(ordinary->ordinary.getUrn().equals(entry.getKey())||reasoner.is(ordinary,entry.getValue())))protectedRoles.add(entry.getKey());
      if(supports.stream().anyMatch(value->value.current()&&value.key().roleUrn().equals(entry.getKey()))
          &&!reasoner.hasRole(original.getObservable(),entry.getValue()))
        throw new IllegalStateException("Live role support contradicts the current native meaning");
    }
    var afterSupports=supports.stream().map(value->new Support(value.key(),value.sourceId(),value.ruleId(),value.activityId(),
        value.acquired(),value.before(),value.after(),value.authority(),value.current()&&!ceasedOrigins.contains(value.sourceId())
            &&!ceasedOrigins.contains(value.key().observerId())&&!ceasedOrigins.contains(value.key().focusId()))).toList();
    var managed=new ArrayList<Concept>();var surviving=new ArrayList<Concept>();var exhausted=new HashSet<RolePresenceKey>();
    for(var entry:roles.entrySet())if(!protectedRoles.contains(entry.getKey())) {
      managed.add(entry.getValue());
      if(afterSupports.stream().anyMatch(value->value.current()&&value.key().roleUrn().equals(entry.getKey())))surviving.add(entry.getValue());
      for(var key:supports.stream().filter(value->value.key().roleUrn().equals(entry.getKey())).map(Support::key).distinct().toList())
        if(afterSupports.stream().noneMatch(value->value.current()&&samePerspective(value.key(),key)
            &&(value.key().roleUrn().equals(entry.getKey())||reasoner.is(roles.get(value.key().roleUrn()),entry.getValue()))))exhausted.add(key);
    }
    Observable meaning=original.getObservable();
    if(!managed.isEmpty()) {
      // Re-add surviving roles explicitly: their previous presence may have followed from a
      // different managed subtype whose last supporting origin is being retired now.
      var builder=meaning.builder(scope).without(managed);for(var role:surviving)builder.withRole(role);meaning=builder.buildObservable();
      if(meaning==null||Utils.Notifications.hasErrors(builder.getNotifications())||!reasoner.satisfiable(meaning.getSemantics()))
        throw new IllegalStateException("Role withdrawal cannot preserve a legal recipient meaning");
      for(var role:surviving)if(!reasoner.hasRole(meaning,role))throw new IllegalStateException("Surviving independent role support was lost");
    }
    var replacement=((ObservationImpl)detached(original)).copyForAttribution(meaning);
    var obligations=new TreeSet<String>();bindings.forEach(value->obligations.addAll(value.obligations()));
    obligations.add(original.getObservable().getUrn());obligations.add(meaning.getUrn());obligations.addAll(roles.keySet());
    var epoch=scope.getResolutionCycleBinding().semanticBinding();
    var request=new SemanticDependencyRequest(epoch.reasonerServiceId(),epoch.knowledgeRevision(),epoch.semanticFingerprint(),List.copyOf(obligations));
    var witness=reasoner.semanticDependencies(request,scope);requireWitness(witness,request,scope);
    return new PreparedRoleWithdrawal(original,replacement,reads,ceasedOrigins,new Ledger(supports,protectedRoles),
        new Ledger(afterSupports,protectedRoles),exhausted,request,witness,bindings,scope);
  }

  private static void revalidate(SemanticEvidenceBinding binding,Reasoner reasoner,ServiceContextScope scope) {
    var epoch=scope.getResolutionCycleBinding().semanticBinding();
    var request=new SemanticDependencyRequest(epoch.reasonerServiceId(),epoch.knowledgeRevision(),epoch.semanticFingerprint(),binding.obligations());
    var current=reasoner.semanticDependencies(request,scope);requireWitness(current,request,scope);
    if(!request.inputHash().equals(binding.witness().inputHash())||!SemanticDependencyWitness.revalidates(binding.witness(),current))
      throw new IllegalStateException("Original role source accounting no longer revalidates");
  }
  private static void requireWitness(SemanticDependencyWitness proof,SemanticDependencyRequest request,ServiceContextScope scope) {
    var epoch=scope.getResolutionCycleBinding().semanticBinding();
    if(proof==null||!proof.completeAt(System.currentTimeMillis())||!request.inputHash().equals(proof.inputHash())
        ||!Objects.equals(proof.serviceId(),epoch.reasonerServiceId())||proof.knowledgeRevision()!=epoch.knowledgeRevision()
        ||!Objects.equals(proof.baseFingerprint(),epoch.semanticFingerprint()))throw new IllegalStateException("Role withdrawal semantic accounting is unavailable");
  }
  private static SemanticEvidenceBinding binding(Map<String,Object> metadata) {
    var value=metadata.get(SemanticEvidenceBinding.DATA_KEY);
    if(!(value instanceof String text))throw new IllegalStateException("Attribution scientific accounting is missing");
    var result=Utils.Json.parseObject(text,SemanticEvidenceBinding.class);
    if(result==null||!SemanticEvidenceBinding.VERSION.equals(result.contractVersion())||!result.complete()||result.witness()==null)
      throw new IllegalStateException("Attribution scientific accounting is incomplete");return result;
  }
  private static KnowledgeGraph.ConsequenceAssetSnapshot asset(KnowledgeGraph.ConsequenceReadSet reads,long id) {
    return reads.assets().stream().filter(value->value.id()==id).findFirst().orElseThrow(()->new IllegalStateException("Role support native input is not captured"));
  }
  @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value) {
    if(!(value instanceof String text))throw new IllegalStateException("Native role support metadata is missing");
    return Utils.Json.parseObject(text,Map.class);
  }
  @SuppressWarnings("unchecked") private static Map<String,Object> matchingRow(Object value,Map<String,Object> properties,List<String> fields) {
    if(!(value instanceof List<?> rows))throw new IllegalStateException("Native attribution audit rows are missing");
    for(var item:rows)if(item instanceof Map<?,?> row&&fields.stream().allMatch(field->same(row.get(field),properties.get(field))))return (Map<String,Object>)row;
    throw new IllegalStateException("Native edge differs from its original attribution audit");
  }
  private static boolean same(Object a,Object b){return a instanceof Number&&b instanceof Number?Objects.equals(integer(a),integer(b)):Objects.equals(a,b);}
  private static boolean samePerspective(RolePresenceKey a,RolePresenceKey b) {
    return a.contextId().equals(b.contextId())&&a.observerId()==b.observerId()&&a.focusId()==b.focusId()
        &&a.recipientId()==b.recipientId()&&a.recipientObserverSemantics().equals(b.recipientObserverSemantics());
  }
  private static boolean frameCurrent(RolePresenceKey key,KnowledgeGraph.ConsequenceReadSet reads,ServiceContextScope scope) {
    if(key.focusId()>0&&Boolean.FALSE.equals(asset(reads,key.focusId()).properties().get("klabCurrent")))return false;
    if(key.observerId()>0) {
      var observer=asset(reads,key.observerId());if(Boolean.FALSE.equals(observer.properties().get("klabCurrent")))return false;
      var actual=scope.getDigitalTwin().getKnowledgeGraph().materializeConsequenceObservation(observer,scope);
      if(actual==null||actual.getId()!=key.observerId()||actual.getObservable()==null||!actual.getObservable().is(SemanticType.AGENT))
        throw new IllegalStateException("Stored role observer is no longer an accessible Agent");
    }
    return true;
  }
  private static Long integer(Object value){return value instanceof Byte||value instanceof Short||value instanceof Integer||value instanceof Long?((Number)value).longValue():null;}
  private static long positive(Object value){Long id=integer(value);if(id==null||id<=0)throw new IllegalStateException("Durable role origin required");return id;}
  private static long nonnegative(Object value){Long id=integer(value);if(id==null||id<0)throw new IllegalStateException("Durable role perspective required");return id;}
  private static String text(Object value){if(!(value instanceof String text)||text.isBlank())throw new IllegalStateException("Typed role provenance is missing");return text;}
  private static Observation detached(Observation value){return org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper().convertValue(Observation.forTransport(value),Observation.class);}
}
