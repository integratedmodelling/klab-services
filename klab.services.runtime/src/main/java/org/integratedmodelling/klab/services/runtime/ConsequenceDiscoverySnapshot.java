package org.integratedmodelling.klab.services.runtime;

import java.lang.reflect.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.CohortImpl;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.impl.ActivityImpl;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.services.runtime.neo4j.AbstractKnowledgeGraph;
import org.integratedmodelling.klab.utilities.Utils;

/** Native completed discovery, replayed through the ordinary Runtime verifier on a closed capture.
 * The projection permits only its captured one-hop reads. Missing reads are errors, never empty
 * answers. No resolver, graph write or live graph read is available during completion replay. */
public final class ConsequenceDiscoverySnapshot {
  private ConsequenceDiscoverySnapshot() {}
  private enum Purpose { CONSEQUENCE, ORDINARY_INPUT }

  /** Only actual completion replay can construct this receipt. It is not a wire capability. */
  public static final class Complete {
    private final KnowledgeGraph.ConsequenceReadSet inputs;
    private final List<Observation> members;
    private final Observation query;
    private final SemanticEvidenceBinding evidence,completionEvidence;
    private final ResolutionCycleBinding cycle;
    private final Long observer;
    private final long focus;
    private final Observation capturedFocus,capturedObserver;
    private final Map<Long,Map<String,Object>> payloads;
    private Complete(KnowledgeGraph.ConsequenceReadSet inputs,List<Observation> members,
        Observation query,SemanticEvidenceBinding evidence,SemanticEvidenceBinding completionEvidence,ServiceContextScope scope) {
      this.inputs=inputs;this.members=members.stream().map(ConsequenceDiscoverySnapshot::detached).toList();
      this.query=detached(query);this.evidence=evidence;this.completionEvidence=completionEvidence;
      this.cycle=scope.getResolutionCycleBinding();this.observer=scope.getObserverId();
      this.focus=scope.getContextObservation()==null?0:scope.getContextObservation().getId();
      this.capturedFocus=focus==0?null:detached(scope.getContextObservation());
      this.capturedObserver=observer==null?null:detached(scope.getObserver());
      var values=new LinkedHashMap<Long,Map<String,Object>>();
      for(var member:this.members)if(values.putIfAbsent(member.getId(),payload(member))!=null)
        throw new IllegalStateException("Duplicate completed recipient identity");
      this.payloads=Map.copyOf(values);
    }
    public KnowledgeGraph.ConsequenceReadSet inputs(){return inputs;}
    public List<Observation> members(){return members.stream().map(ConsequenceDiscoverySnapshot::detached).toList();}
    public Observation completedQuery(){return detached(query);}
    public SemanticEvidenceBinding semanticEvidence(){return evidence;}
    public SemanticEvidenceBinding completionEvidence(){return completionEvidence;}
    public ResolutionCycleBinding cycle(){return cycle;}
    public String expression(){return query.getObservable().getUrn();}
    public Long observerId(){return observer;}
    public long focusId(){return focus;}
    public Set<Long> memberIds(){return payloads.keySet();}
    /** A valid subset, duplicate, changed meaning/support or additional member is not this discovery. */
    public void requireExactMembers(Collection<? extends Observation> selected) {
      var values=new LinkedHashMap<Long,Map<String,Object>>();
      for(var member:selected)if(member==null||values.putIfAbsent(member.getId(),payload(member))!=null)
        throw new IllegalStateException("Discovery member identity is missing or repeated");
      if(!payloads.equals(values))throw new IllegalStateException("Recipients differ from complete captured discovery");
    }
    /** Must run outside the native graph monitor. Original inputs still need native commit guards. */
    public void revalidateForRequest(ServiceContextScope scope) {
      if(!Objects.equals(cycle,scope.getResolutionCycleBinding())||!Objects.equals(inputs.contextId(),scope.getId())
          ||!Objects.equals(observer,scope.getObserverId()))
        throw new IllegalStateException("Discovery request changed cycle, context or observer");
      // Secondary inherency changes the target focus. Verify the original accounted query under
      // its captured focus; the target transaction must guard both the original inputs and target.
      var originalContext=new ReceiptScope(scope,capturedObserver,capturedFocus);
      if(!SemanticExecutionEvidence.verified(completionEvidence,originalContext)||!SemanticExecutionEvidence.verified(evidence,originalContext))
        throw new IllegalStateException("Original discovery semantic receipt no longer revalidates");
    }
    public void revalidate(ServiceContextScope scope) {
      if(!Objects.equals(cycle,scope.getResolutionCycleBinding())||!Objects.equals(inputs.contextId(),scope.getId())
          ||!Objects.equals(observer,scope.getObserverId())
          ||focus!=(scope.getContextObservation()==null?0:scope.getContextObservation().getId())
          ||!SemanticExecutionEvidence.verified(completionEvidence,scope)||!SemanticExecutionEvidence.verified(evidence,scope))
        throw new IllegalStateException("Discovery cycle, perspective or semantic receipt no longer revalidates");
    }
  }
  private static Observation detached(Observation observation) {
    return org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper()
        .convertValue(Observation.forTransport(observation),Observation.class);
  }
  private static Map<String,Object> payload(Observation observation) {
    var nativeValue=(org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl)Observation.forTransport(observation);
    var map=new LinkedHashMap<>(AbstractKnowledgeGraph.persistedObservationProperties(nativeValue));
    map.put("capturedPerceivedGeometry",nativeValue.getPerceivedGeometry()==null?null:Geometry.forTransport(nativeValue.getPerceivedGeometry()).encode());
    map.put("capturedGeometry",observation.getGeometry()==null?null:Geometry.forTransport(observation.getGeometry()).encode());
    return Collections.unmodifiableMap(map);
  }

  public static Complete capture(RuntimeService runtime,Observation discovery,ServiceContextScope boundScope) {
    return capture(runtime,discovery,boundScope,Purpose.CONSEQUENCE);
  }
  /** Ordinary explicit model input proof is available even with automatic consequences disabled.
   * The identical private Runtime issuer, retained cycle and closed native replay are mandatory. */
  public static Complete captureOrdinaryInputs(RuntimeService runtime,Observation discovery,ServiceContextScope boundScope) {
    return capture(runtime,discovery,boundScope,Purpose.ORDINARY_INPUT);
  }
  private static Complete capture(RuntimeService runtime,Observation discovery,ServiceContextScope boundScope,Purpose purpose) {
    Objects.requireNonNull(runtime);Objects.requireNonNull(discovery);Objects.requireNonNull(boundScope);
    requireCycle(boundScope,purpose);requireOriginalReceipt(discovery,boundScope);
    var original=discovery;discovery=detached(discovery);
    if(discovery.isEmpty()||discovery.getObservable()==null||!discovery.getObservable().getSemantics().isCollective()
        ||discovery.getGeometry()==null)throw new IllegalStateException("A completed collective request with support is required");
    if(!discovery.getMetadata().containsKey(CollectiveSelectionView.DATA_KEY)
        &&(!boundScope.getService(Reasoner.class).directTraits(discovery.getObservable()).isEmpty()
          ||!boundScope.getService(Reasoner.class).directRoles(discovery.getObservable()).isEmpty()))
      throw new IllegalStateException("Qualified discovery requires its issued selection completion receipt");
    var cohort=runtime.getCohortFor(discovery.getObservable().getSemantics().singular(),boundScope,false);
    if(cohort==null||cohort.getId()<=0)throw new IllegalStateException("Completed discovery has no durable cohort");
    var ids=new TreeSet<Long>();ids.add(cohort.getId());
    if(discovery.getId()>0)ids.add(discovery.getId());
    if(boundScope.getObserverId()!=null)ids.add(boundScope.getObserverId());
    if(boundScope.getContextObservation()!=null)ids.add(boundScope.getContextObservation().getId());
    var selections=new LinkedHashSet<KnowledgeGraph.ConsequenceEdgeSelection>();
    select(selections,cohort.getId(),GraphModel.Relationship.HAS_MEMBER,GraphModel.Relationship.Direction.OUTGOING);
    select(selections,cohort.getId(),GraphModel.Relationship.CONTRIBUTED_TO,GraphModel.Relationship.Direction.INCOMING);
    select(selections,cohort.getId(),GraphModel.Relationship.HAS_CHILD,GraphModel.Relationship.Direction.INCOMING);
    KnowledgeGraph.ConsequenceReadSet captured=null;
    var graph=boundScope.getDigitalTwin().getKnowledgeGraph();
    var rounds=new ArrayList<KnowledgeGraph.ConsequenceReadSet>();
    try {
      for(int round=0;round<5;round++) {
        // Expansion can discover a lower ID. Release this round's locks before acquiring
        // any newly discovered nodes; never accumulate unsorted locks across rounds.
        try(var transaction=graph.createTransaction(boundScope)) {
          captured=transaction.captureConsequenceInputs(ids,selections);
        }
        rounds.add(captured);int beforeIds=ids.size(),beforeSelections=selections.size();
        for(var edge:captured.edges())for(var endpoint:List.of(edge.source(),edge.target()))
          if(endpoint.id() instanceof Number id&&(endpoint.labels().contains(GraphModel.Labels.OBSERVATION)
              ||endpoint.labels().contains(GraphModel.Labels.ACTIVITY)||endpoint.labels().contains(GraphModel.Labels.COHORT)))ids.add(id.longValue());
        for(var asset:captured.assets())if(asset.labels().contains(GraphModel.Labels.OBSERVATION))
          for(var effect:List.of(GraphModel.Relationship.INSTANTIATED,GraphModel.Relationship.CONNECTED,GraphModel.Relationship.CLASSIFIED,GraphModel.Relationship.CHARACTERIZED))
            select(selections,asset.id(),effect,GraphModel.Relationship.Direction.INCOMING);
        if(beforeIds==ids.size()&&beforeSelections==selections.size())break;
        if(round==4)throw new IllegalStateException("Discovery capture did not reach a closed local neighborhood");
      }
      // Acquire the complete sorted union once, then fence every earlier observation
      // under those same locks. A changed expansion cannot authorize a partial replay.
      try(var transaction=graph.createTransaction(boundScope)) {
        captured=transaction.captureConsequenceInputs(ids,selections);
        for(var prior:rounds)transaction.validateConsequenceInputs(prior);
      }
    }catch(java.io.IOException failure){throw new IllegalStateException("Discovery capture could not close safely",failure);}
    return verifyCaptured(runtime,original,boundScope,cohort.getId(),captured,graph,purpose);
  }
  private static void select(Set<KnowledgeGraph.ConsequenceEdgeSelection> selections,long id,
      GraphModel.Relationship relationship,GraphModel.Relationship.Direction direction) {
    selections.add(new KnowledgeGraph.ConsequenceEdgeSelection(id,relationship,direction));
  }
  private static void requireCycle(ServiceContextScope scope,Purpose purpose) {
    var cycle=scope.getResolutionCycleBinding();
    if(cycle==null||purpose==Purpose.CONSEQUENCE&&!cycle.consequencesEnabled()||cycle.semanticBinding()==null||!scope.getId().equals(cycle.contextId()))
      throw new IllegalStateException("Captured discovery requires an enabled retained semantic cycle");
  }
  private static SemanticEvidenceBinding requireOriginalReceipt(Observation view,ServiceContextScope scope) {
    var receipt=SemanticExecutionEvidence.issuedForConsequence(view,scope);
    if(view.getId()!=Observation.QUERY_ID||receipt==null||!SemanticExecutionEvidence.verified(receipt,scope))
      throw new IllegalStateException("An original authorized Runtime query receipt is required; no receipt-free replay is allowed");
    return receipt;
  }
  /** Package-private replay seam, with the same mandatory original issuer gate as public capture. */
  static Complete verifyCaptured(RuntimeService runtime,Observation discovery,ServiceContextScope original,
      long cohortId,KnowledgeGraph.ConsequenceReadSet reads,KnowledgeGraph observationDecoder) {
    return verifyCaptured(runtime,discovery,original,cohortId,reads,observationDecoder,Purpose.CONSEQUENCE);
  }
  private static Complete verifyCaptured(RuntimeService runtime,Observation discovery,ServiceContextScope original,
      long cohortId,KnowledgeGraph.ConsequenceReadSet reads,KnowledgeGraph observationDecoder,Purpose purpose) {
    requireCycle(original,purpose);var originalReceipt=requireOriginalReceipt(discovery,original);
    discovery=detached(discovery);
    if(!original.getId().equals(reads.contextId()))throw new IllegalStateException("Discovery capture belongs to another context");
    var view=new Projection(reads,cohortId,original,observationDecoder);
    var scope=new CapturedScope(original,view);
    var query=detached(discovery);
    var declared=discovery.getMetadata().get(Metadata.IM_QUERY_GEOMETRY);
    if(declared instanceof String support)((org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl)query).setGeometry(Geometry.create(support));
    var checked=discovery.getMetadata().containsKey(CollectiveSelectionView.DATA_KEY)
        ?CollectiveSelectionExecution.query(runtime,Observation.forTransport(discovery),scope)
        :runtime.completedCohortReference(query,scope);
    if(checked==null||checked.isEmpty()||RuntimeService.coverage(discovery.getGeometry(),checked.getGeometry())<1.0-1.0e-9)
      throw new IllegalStateException("Captured discovery replay is incomplete");
    if(!SemanticExecutionEvidence.sameCheckedPayload(discovery,checked))
      throw new IllegalStateException("Captured replay differs from the original authorized query receipt");
    var members=runtime.getMembers(checked,scope);
    var selected=checked.getMetadata().get(CollectiveSelectionView.DATA_KEY);
    var evidence=selected instanceof String encoded
        ?Utils.Json.parseObject(encoded,CollectiveSelectionView.class).semanticEvidence()
        :SemanticExecutionEvidence.decode(checked.getMetadata().get(SemanticEvidenceBinding.DATA_KEY));
    if(evidence==null||!evidence.complete()||!SemanticExecutionEvidence.verified(originalReceipt,scope)||!SemanticExecutionEvidence.verified(evidence,scope))
      throw new IllegalStateException("Ordinary completion replay did not issue complete semantic evidence");
    var cycle=scope.getResolutionCycleBinding();var epoch=cycle.semanticBinding();var caps=scope.getService(Reasoner.class).capabilities(scope);
    if(caps==null||!Objects.equals(caps.getServiceId(),epoch.reasonerServiceId())
        ||caps.getKnowledgeRevision()!=epoch.knowledgeRevision()||!Objects.equals(caps.getSemanticSessionId(),epoch.reasonerSessionId())
        ||!Objects.equals(caps.getSemanticContentFingerprint(),epoch.semanticFingerprint()))
      throw new IllegalStateException("Discovery verifier escaped its retained semantic cycle");
    // The normal issued receipt proves completed producer execution. Member filtering also
    // consumes current meanings (including excluded cohort members); account those exact captured
    // meanings with the existing collector, without inventing a completion flag or execution.
    var accounting=new SemanticExecutionEvidence.Collector();accounting.scope(scope);accounting.inherit(evidence,scope);
    for(var value:view.assets.values())if(value instanceof Observation observation)accounting.observation(observation);
    var fullEvidence=accounting.finish(scope);
    if(!fullEvidence.complete()||!SemanticExecutionEvidence.verified(fullEvidence,scope))
      throw new IllegalStateException("Captured discovery member semantics are not completely accounted");
    return new Complete(reads,members,checked,fullEvidence,originalReceipt,scope);
  }

  private static final class ReceiptScope extends ServiceContextScope {
    private final Observation selected,focus;
    ReceiptScope(ServiceContextScope scope,Observation selected,Observation focus){super(scope);this.selected=selected;this.focus=focus;}
    @Override public Observation getObserver(){return selected;}
    @Override public Long getObserverId(){return selected==null?null:selected.getId();}
    @Override public Observation getContextObservation(){return focus;}
    @Override public DigitalTwin.Transaction getCurrentTransaction(){return null;}
  }
  static final class CapturedScope extends ServiceContextScope {
    private final Projection view;
    CapturedScope(ServiceContextScope source,Projection view){super(source);this.view=view;}
    @Override public DigitalTwin getDigitalTwin(){return view.twin;}
    @Override public DigitalTwin.Transaction getCurrentTransaction(){return null;}
    @Override public Observation getObservation(long id){return view.observation(id);}
    @Override public Observation getObserver(){var id=getObserverId();return id==null?null:view.observation(id);}
    @Override public Observation getContextObservation(){return view.focus==0?null:view.observation(view.focus);}
  }
  static final class Projection {
    final KnowledgeGraph.ConsequenceReadSet reads;
    final long cohortId,focus;
    final Map<Long,RuntimeAsset> assets=new LinkedHashMap<>();
    final Map<Long,KnowledgeGraph.ConsequenceAssetSnapshot> raw=new LinkedHashMap<>();
    final Set<Long> inactive=new HashSet<>();
    final KnowledgeGraph graph;
    final DigitalTwin twin;
    Projection(KnowledgeGraph.ConsequenceReadSet reads,long cohortId,ServiceContextScope scope,KnowledgeGraph decoder) {
      this.reads=reads;this.cohortId=cohortId;this.focus=scope.getContextObservation()==null?0:scope.getContextObservation().getId();
      for(var asset:reads.assets()) {
        if(raw.putIfAbsent(asset.id(),asset)!=null)throw new IllegalStateException("Duplicate native captured identity");
        if(asset.labels().contains(GraphModel.Labels.OBSERVATION)&&Boolean.FALSE.equals(asset.properties().get(KnowledgeGraph.CURRENT_PROPERTY))){inactive.add(asset.id());continue;}
        RuntimeAsset restored;
        if(asset.labels().contains(GraphModel.Labels.OBSERVATION))restored=decoder.materializeConsequenceObservation(asset,scope);
        else if(asset.labels().contains(GraphModel.Labels.ACTIVITY))restored=activity(asset);
        else if(asset.labels().contains(GraphModel.Labels.COHORT))restored=cohort(asset,scope);
        else throw new IllegalStateException("Unsupported captured asset kind");
        if(restored==null||restored.getId()!=asset.id())throw new IllegalStateException("Native payload decoder changed asset identity");
        assets.put(asset.id(),restored);
      }
      if(!(assets.get(cohortId) instanceof Cohort))throw new IllegalStateException("Canonical cohort snapshot is missing");
      if(scope.getObserverId()!=null) {
        var observer=observation(scope.getObserverId());
        if(observer==null||!observer.getObservable().is(SemanticType.AGENT))throw new IllegalStateException("Captured observer is not an Agent");
      }
      if(focus!=0&&observation(focus)==null)throw unsupported("Observed focus is no longer current");
      graph=(KnowledgeGraph)Proxy.newProxyInstance(KnowledgeGraph.class.getClassLoader(),new Class<?>[]{KnowledgeGraph.class},(p,m,args)->{
        if(m.getName().equals("query")&&args[0] instanceof Class<?> type)return query(type);
        if(m.getName().equals("isObservationCurrent")&&args[0] instanceof Observation observation) {
          if(!raw.containsKey(observation.getId()))throw unsupported("Current status is outside captured inputs");
          return !inactive.contains(observation.getId())&&assets.get(observation.getId()) instanceof Observation;
        }
        if(m.getName().equals("getAsset")&&args[0] instanceof Long id) {
          if(inactive.contains(id))return null;
          var result=assets.get(id);if(result==null||!((Class<?>)args[2]).isInstance(result))throw unsupported("Uncaptured asset");return result;
        }
        // This counter identifies one immutable projection, never a live/native completion claim.
        if(m.getName().equals("getSemanticRevision"))return reads.assets().stream().mapToLong(a->number(a.nativeProperties(),"semanticRevision",0)).max().orElse(0);
        if(m.getName().equals("toString"))return "Closed captured discovery graph";
        throw unsupported("Read or write outside captured completion: "+m.getName());
      });
      twin=(DigitalTwin)Proxy.newProxyInstance(DigitalTwin.class.getClassLoader(),new Class<?>[]{DigitalTwin.class},(p,m,args)->{
        if(m.getName().equals("getKnowledgeGraph"))return graph;
        throw unsupported("Digital twin operation outside captured completion: "+m.getName());
      });
    }
    Observation observation(long id) {
      if(inactive.contains(id))return null;
      if(!(assets.get(id) instanceof Observation result))throw unsupported("Observation missing from native capture: "+id);
      return result;
    }
    Object query(Class<?> type) {
      return Proxy.newProxyInstance(KnowledgeGraph.Query.class.getClassLoader(),new Class<?>[]{KnowledgeGraph.Query.class},new InvocationHandler(){
        Object anchor;boolean outgoing;GraphModel.Relationship relationship;
        final List<KnowledgeGraph.Query.Criterion> predicates=new ArrayList<>();
        public Object invoke(Object proxy,Method method,Object[] args) {
          return switch(method.getName()) {
            case "source","target" -> {if(anchor!=null)throw unsupported("Multiple captured query anchors");anchor=args[0];outgoing=method.getName().equals("source");yield proxy;}
            case "along" -> {relationship=(GraphModel.Relationship)args[0];if(args.length>1&&((Object[])args[1]).length>0)throw unsupported("Uncaptured relationship predicates");yield proxy;}
            case "where" -> {if(args[1]!=KnowledgeGraph.Query.Operator.EQUALS)throw unsupported("Unsupported captured query operator");predicates.add(new KnowledgeGraph.Query.Criterion((String)args[0],(KnowledgeGraph.Query.Operator)args[1],args[2]));yield proxy;}
            case "run","peek" -> {
              var values=run(type,anchor,outgoing,relationship,predicates);
              yield method.getName().equals("peek")?values.stream().findFirst():values;
            }
            default -> throw unsupported("Unsupported captured query shape: "+method.getName());
          };
        }
      });
    }
    List<RuntimeAsset> run(Class<?> type,Object anchor,boolean outgoing,GraphModel.Relationship relationship,List<KnowledgeGraph.Query.Criterion> predicates) {
      if(!(anchor instanceof RuntimeAsset start)||relationship==null)throw unsupported("Unanchored captured query");
      var result=new LinkedHashMap<Long,RuntimeAsset>();
      if(start==RuntimeAsset.CONTEXT_ASSET||start.getId()==RuntimeAsset.CONTEXT_ASSET_ID) {
        if(!outgoing||type!=Cohort.class||relationship!=GraphModel.Relationship.HAS_CHILD||predicates.size()!=1
            ||!predicates.getFirst().field().equals(GraphModel.Cohort.OBSERVABLE_FIELD))throw unsupported("Incomplete root catalog query");
        var cohort=assets.get(cohortId);requireSelection(cohortId,relationship,false);
        boolean attached=reads.edges().stream().anyMatch(e->e.relationship()==relationship&&Objects.equals(e.target().id(),cohortId)
            &&raw.get(cohortId).elementId().equals(e.target().elementId())
            &&e.source().labels().contains(GraphModel.Labels.CONTEXT)&&Objects.equals(e.source().id(),reads.contextId()));
        if(!attached||!matches(cohort,predicates))throw unsupported("Canonical cohort is not captured in this context");
        return List.of(cohort);
      }
      requireSelection(start.getId(),relationship,outgoing);
      if(inactive.contains(start.getId()))return List.of();
      for(var edge:reads.edges())if(edge.relationship()==relationship) {
        var from=outgoing?edge.source():edge.target();var to=outgoing?edge.target():edge.source();
        if(!Objects.equals(from.id(),start.getId()))continue;
        if(raw.get(start.getId())==null||!raw.get(start.getId()).elementId().equals(from.elementId()))
          throw unsupported("Selected anchor native identity changed");
        if(!(to.id() instanceof Number number))throw unsupported("Query reached uncaptured nonnumeric endpoint");
        var asset=assets.get(number.longValue());var nativeAsset=raw.get(number.longValue());
        if(nativeAsset==null||!nativeAsset.elementId().equals(to.elementId()))throw unsupported("Selected endpoint native identity is missing or changed");
        if(inactive.contains(number.longValue()))continue;
        if(asset==null||!nativeAsset.elementId().equals(to.elementId()))throw unsupported("Selected endpoint payload is missing or changed");
        if(type.isInstance(asset)&&matches(asset,predicates))result.putIfAbsent(asset.getId(),asset);
      }
      return List.copyOf(result.values());
    }
    void requireSelection(long id,GraphModel.Relationship relationship,boolean outgoing) {
      if(reads.selections().stream().noneMatch(selection->selection.assetId()==id&&selection.relationship()==relationship
          &&selection.direction()==(outgoing?GraphModel.Relationship.Direction.OUTGOING:GraphModel.Relationship.Direction.INCOMING)))
        throw unsupported("Required complete edge neighborhood is not captured: "+relationship);
    }
    boolean matches(RuntimeAsset asset,List<KnowledgeGraph.Query.Criterion> predicates) {
      for(var predicate:predicates) {
        if(!predicate.field().equals(GraphModel.Fields.OBSERVABLE))throw unsupported("Unsupported captured property predicate");
        String actual=asset instanceof Observation o?o.getObservable().getUrn():asset instanceof Cohort c?c.getObservable().getUrn():null;
        if(!Objects.equals(actual,predicate.argument()))return false;
      }
      return true;
    }
  }
  private static IllegalStateException unsupported(String message){return new IllegalStateException(message);}

  static Activity activity(KnowledgeGraph.ConsequenceAssetSnapshot snapshot) {
    var p=snapshot.nativeProperties();var a=new ActivityImpl();
    a.setStart(number(p,GraphModel.Fields.START,0));a.setEnd(number(p,GraphModel.Fields.END,0));
    a.setObservationUrn(text(p,GraphModel.Fields.OBSERVATION_URN));a.setName(text(p,GraphModel.Fields.NAME));
    a.setServiceName(text(p,GraphModel.Fields.SERVICE_NAME));a.setServiceId(text(p,GraphModel.Fields.SERVICE_ID));
    var service=text(p,GraphModel.Fields.SERVICE_TYPE);a.setServiceType(service==null?null:org.integratedmodelling.klab.api.services.KlabService.Type.valueOf(service));
    a.setUrn(text(p,GraphModel.Fields.URN));a.setDataflow(text(p,GraphModel.Fields.DATAFLOW));
    var type=text(p,GraphModel.Fields.TYPE);a.setType(type==null?null:Activity.Type.valueOf(type));
    var outcome=text(p,GraphModel.Fields.OUTCOME);a.setOutcome(outcome==null?null:Activity.Outcome.valueOf(outcome));
    a.setCredits(number(p,GraphModel.Fields.CREDITS,0));a.setSize(number(p,GraphModel.Fields.SIZE,0));
    var times=p.get(GraphModel.Fields.SCHEDULER_TIME);a.setSchedulerTime(times==null?List.of():((Collection<?>)times).stream().map(v->((Number)v).longValue()).toList());
    a.setStackTrace(text(p,GraphModel.Fields.STACK_TRACE));a.setTriggeringActivityUrn(text(p,GraphModel.Fields.TRIGGERING_ACTIVITY_URN));
    var metadata=text(p,GraphModel.Fields.METADATA);if(metadata!=null)a.getMetadata().putAll(Utils.Json.parseObject(metadata,Map.class));
    a.setDescription(p.containsKey(GraphModel.Fields.DESCRIPTION)?text(p,GraphModel.Fields.DESCRIPTION):"No description");
    a.setId(number(p,GraphModel.Fields.ID,snapshot.id()));a.setParentId(number(p,GraphModel.Fields.PARENT_ID,-1));
    a.setTransientId(number(p,"transientId",a.getTransientId()));a.setParentTransientId(number(p,"parentTransientId",0));return a;
  }
  private static Cohort cohort(KnowledgeGraph.ConsequenceAssetSnapshot snapshot,ServiceContextScope scope) {
    var p=snapshot.nativeProperties();var c=new CohortImpl();
    c.setObservable(scope.getService(Reasoner.class).resolveObservable(text(p,GraphModel.Fields.OBSERVABLE)));
    if(c.getObservable()==null)throw unsupported("Captured cohort meaning is unavailable");
    c.setUrn(text(p,GraphModel.Fields.URN));c.setId(number(p,GraphModel.Fields.ID,snapshot.id()));
    c.setChildrenCount((int)number(p,GraphModel.Fields.CHILDREN_COUNT,0));c.setParentId(number(p,GraphModel.Fields.PARENT_ID,0));
    var geometry=text(p,GraphModel.Fields.GEOMETRY);if(geometry!=null)c.setGeometry(Geometry.create(geometry));return c;
  }
  private static long number(Map<String,Object> values,String key,long fallback){var value=values.get(key);return value==null?fallback:((Number)value).longValue();}
  private static String text(Map<String,Object> values,String key){var value=values.get(key);return value==null?null:(String)value;}
}
