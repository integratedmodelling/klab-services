package org.integratedmodelling.klab.runtime;

import java.util.*;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.utilities.Utils;

/** Finite observer matching branches, independent of the observer's historical taxonomy.
 * Missing tracing is conservative; it never establishes absence of semantic use. */
public final class ObserverSemanticUse {
  public static final String KEY="im:observer-semantic-use-v1";
  private ObserverSemanticUse() {}
  public record Owner(String service,long revision,String fingerprint,String session) {
    public static Owner of(Reasoner.Capabilities value) {
      return value==null?null:new Owner(value.getServiceId(),value.getKnowledgeRevision(),
          value.getSemanticContentFingerprint(),value.getSemanticSessionId());
    }
  }
  public record Input(long id,String urn,String meaning,String types,String geometry) {}
  public record Summary(String version,String scope,Input observer,Owner owner,
      boolean valid,List<String> terms) {}
  public record Requirements(boolean tracked,boolean valid,List<String> terms) {}
  private static final Map<DigitalTwin.Transaction,Use> ROOTS=Collections.synchronizedMap(new WeakHashMap<>());
  private static final ThreadLocal<Use> LOCAL=new ThreadLocal<>();

  private static Input input(Observation observer) {
    if(observer==null)return null;
    var observable=observer.getObservable();
    var concept=observable==null?null:observable.getSemantics();
    return new Input(observer.getId(),observer.getUrn(),concept==null?null:concept.getUrn(),
        concept==null||concept.getType()==null?null:new TreeSet<>(concept.getType()).toString(),
        observer.getGeometry()==null?null:Geometry.forTransport(observer.getGeometry()).encode());
  }
  private static DigitalTwin.Transaction root(ContextScope scope) {
    var transaction=scope.getCurrentTransaction();
    var seen=Collections.newSetFromMap(new IdentityHashMap<DigitalTwin.Transaction,Boolean>());
    if(transaction!=null)while(transaction.getParent()!=null) {
      if(!seen.add(transaction)||seen.size()>256)throw new IllegalStateException("Invalid observer transaction ancestry");
      transaction=transaction.getParent();
    }
    return transaction;
  }
  private static final class Use {
    final String scope; final Input observer; final Owner owner;
    final Set<String> terms=new TreeSet<>(); boolean valid=true, accounted=true;
    Use(ContextScope scope,Owner owner) {
      this.scope=scope.getId();var selected=scope.getObserver();this.observer=input(selected);this.owner=owner;
      valid=scope.getId()!=null && !scope.getId().isBlank() && (selected==null || selected.getId()>0 && !selected.isEmpty()
          && selected.getUrn()!=null && !selected.getUrn().isBlank() && selected.getObservable()!=null
          && selected.getObservable().is(org.integratedmodelling.klab.api.knowledge.SemanticType.AGENT)
          && !selected.getObservable().is(org.integratedmodelling.klab.api.knowledge.SemanticType.ABSTRACT)
          && selected.getObservable().getSemantics()!=null
          && !selected.getObservable().getSemantics().isCollective());
    }
    boolean matches(ContextScope scope) {
      var actual=input(scope.getObserver());
      return Objects.equals(this.scope,scope.getId())&&Objects.equals(observer,actual);
    }
    synchronized void require(Observation observer) {
      if(!Objects.equals(this.observer,input(observer)))
        throw new IllegalStateException("Observer matching input changed during execution");
    }
    void semantic(ContextScope scope,org.integratedmodelling.klab.api.knowledge.Concept... compared) {
      boolean match=matches(scope);
      synchronized(this) {
        if(!match){valid=false;return;}
        if(observer!=null && observer.meaning()!=null)terms.add(observer.meaning());
        for(var operand:compared) {
          if(operand==null || operand.getUrn()==null || operand.getUrn().isBlank())valid=false;
          else terms.add(operand.getUrn());
        }
      }
    }
    Requirements requirements(ContextScope scope) {
      boolean match=matches(scope);
      synchronized(this) {
        if(!match)valid=false;
        var result=new TreeSet<>(terms);
        if(!accounted && observer!=null && observer.meaning()!=null)result.add(observer.meaning());
        return new Requirements(true,valid,List.copyOf(result));
      }
    }
  }

  /** Start before native planning/binding. The commit guard reads the already-loaded bean only. */
  public static void start(ContextScope scope) {
    if(scope.getObserver()==null)return;
    var root=root(scope); if(root==null)throw new IllegalStateException("Observer use needs a native attempt");
    var use=new Use(scope,null);
    synchronized(ROOTS) { if(ROOTS.containsKey(root))return; ROOTS.put(root,use); }
    var observer=scope.getObserver();
    root.beforeCommit(()->use.require(observer));
  }
  public static final class Capture implements AutoCloseable {
    private final Use previous=LOCAL.get(), use; private final Observation observer;
    private Capture(ContextScope scope,Owner owner) { use=new Use(scope,owner);observer=scope.getObserver();LOCAL.set(use); }
    public String summary(ContextScope scope,Owner current) {
      var requirements=use.requirements(scope);
      return Utils.Json.asString(new Summary(KEY,use.scope,use.observer,use.owner,
          requirements.valid()&&Objects.equals(use.owner,current),requirements.terms()));
    }
    @Override public void close() { if(previous==null)LOCAL.remove();else LOCAL.set(previous);use.require(observer); }
  }
  public static Capture capture(ContextScope scope,Owner owner) { return new Capture(scope,owner); }
  public static Capture capture(ContextScope scope) { return capture(scope,null); }

  /** Called only on the branch which actually compares the selected Agent's meaning. */
  public static void semanticComparison(ContextScope scope,org.integratedmodelling.klab.api.knowledge.Concept... compared) {
    var local=LOCAL.get(); if(local!=null)local.semantic(scope,compared);
    var root=root(scope);var use=root==null?null:ROOTS.get(root);if(use!=null&&use!=local)use.semantic(scope,compared);
  }
  public static Requirements requirements(ContextScope scope) {
    var local=LOCAL.get();var root=root(scope);var use=root==null?null:ROOTS.get(root);
    if(local==null&&use==null)return new Requirements(false,true,List.of());
    var terms=new TreeSet<String>();boolean valid=true;
    for(var current:Arrays.asList(local,use))if(current!=null) {
      var result=current.requirements(scope);valid &= result.valid();terms.addAll(result.terms());
    }
    return new Requirements(true,valid,List.copyOf(terms));
  }
  public static void merge(Object encoded,ContextScope scope,Owner owner) {
    var root=root(scope);var use=root==null?null:ROOTS.get(root);if(use==null)return;
    var observer=input(scope.getObserver());
    synchronized(use) {
      if(!(encoded instanceof String text)){use.accounted=false;return;}
      final Summary summary;
      try {summary=Utils.Json.parseObject(text,Summary.class);}
      catch(RuntimeException malformed){use.valid=false;return;}
      if(summary==null||!KEY.equals(summary.version())||!summary.valid()
          ||!Objects.equals(summary.owner(),owner)||!Objects.equals(summary.scope(),scope.getId())
          ||!Objects.equals(summary.observer(),observer)) {use.valid=false;return;}
      use.terms.addAll(summary.terms());
    }
  }
  /** Authenticated Resolver result; source/model leases continue to guard these branch decisions. */
  public static void stamp(Dataflow dataflow,String summary) {
    var seen=Collections.newSetFromMap(new IdentityHashMap<Actuator,Boolean>());
    var queue=new ArrayDeque<Actuator>(dataflow.getComputation());
    while(!queue.isEmpty()) {
      var actuator=queue.removeFirst();if(!seen.add(actuator))continue;
      actuator.getData().put(KEY,summary);queue.addAll(actuator.getChildren());
      if(actuator.getCollectiveSelectionDependency()!=null)queue.addAll(actuator.getCollectiveSelectionDependency().getComputation());
      for(var dependency:actuator.getClassifierDependencies())
        for(var occurrence:dependency.occurrences())queue.add(occurrence.classifier());
    }
  }
}
