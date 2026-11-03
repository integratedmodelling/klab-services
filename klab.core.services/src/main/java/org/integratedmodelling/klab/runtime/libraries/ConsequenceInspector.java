package org.integratedmodelling.klab.runtime.libraries;

import java.util.*;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;

/** Narrow read-only assertions for actual committed, guarded consequence role effects. */
public final class ConsequenceInspector {
  private ConsequenceInspector() {}
  public static boolean role(ContextScope scope,Observation source,Observation recipient,String expectedRole) {
    if(source==null||recipient==null||source.getId()<=0||recipient.getId()<=0)return false;
    var reasoner=scope.getService(Reasoner.class);var role=reasoner.resolveConcept(expectedRole);
    if(role==null||!role.is(SemanticType.ROLE))throw new IllegalArgumentException("Consequence inspector requires an exact role semantic literal");
    return role(scope,source.getId(),recipient.getId(),role,reasoner);
  }
  private static boolean role(ContextScope scope,long sourceId,long recipientId,Concept role,Reasoner reasoner) {
    var graph=scope.getDigitalTwin().getKnowledgeGraph();
    var source=graph.getAsset(sourceId,scope,Observation.class);var recipient=graph.getAsset(recipientId,scope,Observation.class);
    if(source==null||recipient==null||source.isEmpty()||recipient.isEmpty()||recipient.getObservable()==null
        ||recipient.getObservable().getSemantics().isCollective()
        ||!SemanticType.isEnumerableSubstantial(recipient.getObservable().getSemantics().getType()))return false;
    for(var link:graph.getLinks(recipient,GraphModel.Relationship.Direction.INCOMING,scope,GraphModel.Relationship.CLASSIFIED)) {
      if(!(link.source() instanceof Activity activity)||activity.getId()<=0||activity.getType()!=Activity.Type.CLASSIFICATION
          ||activity.getOutcome()!=Activity.Outcome.SUCCESS||link.target().getId()!=recipientId)continue;
      var properties=link.properties();
      if(!(properties.get("sourceId") instanceof Number sourceNumber)||sourceNumber.longValue()!=sourceId
          ||!role.getUrn().equals(properties.get("role"))||!(properties.get("ruleId") instanceof String ruleId)||ruleId.isBlank()
          ||!(properties.get("authority") instanceof String authority)||authority.isBlank())continue;
      var raw=activity.getMetadata().get("klab.consequence.roleAttributions");if(!(raw instanceof List<?> rows))continue;
      for(var value:rows)if(value instanceof Map<?,?> row
          &&row.get("sourceId") instanceof Number recordedSource&&recordedSource.longValue()==sourceId
          &&row.get("recipientId") instanceof Number recordedRecipient&&recordedRecipient.longValue()==recipientId
          &&role.getUrn().equals(row.get("role"))&&ruleId.equals(row.get("ruleId"))&&authority.equals(row.get("authority"))
          &&row.get("before") instanceof String before&&row.get("after") instanceof String after&&!before.equals(after)
          &&before.equals(properties.get("before"))&&after.equals(properties.get("after")))
        return reasoner.hasRole(recipient.getObservable(),role);
    }
    return false;
  }
  /** Bounded polling only for the asynchronous committed effect; never resolves or mutates. */
  public static boolean awaitRole(ContextScope scope,Observation source,Observation recipient,String role,long timeoutMillis)
      throws InterruptedException {
    if(timeoutMillis<1||timeoutMillis>60_000)throw new IllegalArgumentException("Consequence assertion wait must be between 1 and 60000 ms");
    if(Thread.currentThread().isInterrupted()||scope.isInterrupted())throw new InterruptedException("Consequence assertion cancelled");
    if(source==null||recipient==null||source.getId()<=0||recipient.getId()<=0)return false;
    var reasoner=scope.getService(Reasoner.class);var expected=reasoner.resolveConcept(role);
    if(expected==null||!expected.is(SemanticType.ROLE))throw new IllegalArgumentException("Consequence inspector requires an exact role semantic literal");
    long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
    do {
      if(Thread.currentThread().isInterrupted()||scope.isInterrupted())throw new InterruptedException("Consequence assertion cancelled");
      if(role(scope,source.getId(),recipient.getId(),expected,reasoner))return true;
      long remaining=deadline-System.nanoTime();if(remaining<=0)return false;
      Thread.sleep(Math.min(100,Math.max(1,java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(remaining))));
    } while(true);
  }
  public static boolean target(ContextScope scope,Observation recipient,String expectedTarget) {
    if(recipient==null||recipient.getId()<=0)return false;
    var reasoner=scope.getService(Reasoner.class);var expected=reasoner.resolveObservable(expectedTarget);
    if(expected==null)throw new IllegalArgumentException("Consequence inspector requires available target semantics");
    for(var observation:scope.getDigitalTwin().getKnowledgeGraph().query(Observation.class,scope)
        .source(recipient).along(GraphModel.Relationship.HAS_CHILD).run(scope))
      if(!observation.isEmpty()&&observation.getId()>0&&observation.getObservable()!=null
          &&observation.getResolvedCoverage()>0&&reasoner.is(observation.getObservable(),expected))return true;
    return false;
  }
  /** Wait for an already committed target under its actual bearer; never submits a model. */
  public static boolean awaitTarget(ContextScope scope,Observation recipient,String target,long timeoutMillis)throws InterruptedException {
    if(timeoutMillis<1||timeoutMillis>60_000)throw new IllegalArgumentException("Consequence assertion wait must be between 1 and 60000 ms");
    long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
    do {
      if(Thread.currentThread().isInterrupted()||scope.isInterrupted())throw new InterruptedException("Consequence assertion cancelled");
      if(target(scope,recipient,target))return true;
      long remaining=deadline-System.nanoTime();if(remaining<=0)return false;
      Thread.sleep(Math.min(100,Math.max(1,java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(remaining))));
    } while(true);
  }
}
