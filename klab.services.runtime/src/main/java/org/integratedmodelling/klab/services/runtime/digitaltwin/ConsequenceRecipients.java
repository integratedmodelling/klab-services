package org.integratedmodelling.klab.services.runtime.digitaltwin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/**
 * Select accessible committed role recipients. Selection is not attribution authority: callers
 * must validate applicability, semantic proof and the durable source/recipient read set at commit.
 */
public final class ConsequenceRecipients {
  private ConsequenceRecipients() {}
  public enum Selector { DEFAULT, SOURCE, TARGET, EXPRESSION }
  public enum Status { COMPLETE, DEFERRED, FAILED }
  public interface Evidence {}
  public record Selection(Status status,List<Observation> recipients,String reason,Evidence evidence) {
    public Selection {
      Objects.requireNonNull(status);Objects.requireNonNull(reason);recipients=List.copyOf(recipients);
      if(status!=Status.COMPLETE&&!recipients.isEmpty())
        throw new IllegalArgumentException("Incomplete discovery cannot authorize partial recipients");
    }
    public Selection(Status status,List<Observation> recipients,String reason) {this(status,recipients,reason,null);}
    public static Selection complete(List<Observation> recipients) {
      return new Selection(Status.COMPLETE,recipients,"Recipient discovery completed");
    }
  }
  @FunctionalInterface public interface Resolver {
    /** COMPLETE with no members requires successful empty discovery, never missing/failed work. */
    Selection resolveEach(KimConcept substantial,ServiceContextScope scope) throws Exception;
  }

  public static Selection select(Observation trigger,Selector selector,KimConcept expression,
      ServiceContextScope scope,Resolver resolver) throws Exception {
    if(scope.isInterrupted()||Thread.currentThread().isInterrupted())
      throw new InterruptedException("Consequence recipient discovery cancelled");
    if(trigger==null||trigger.getId()<=0)
      return deferred("Role conferral requires a committed source observation");
    var source=scope.getObservation(trigger.getId());
    if(source==null||source.isEmpty()||source.getObservable()==null
        ||source.getObservable().getSemantics().isCollective())
      return deferred("The committed conferral source is no longer accessible or individual");
    boolean relationship=source.getObservable().is(SemanticType.RELATIONSHIP);
    boolean process=source.getObservable().is(SemanticType.PROCESS);
    if(!relationship&&!process)return deferred("Role conferral source must be a process or relationship");
    final List<Observation> discovered;Evidence evidence=null;
    switch(selector) {
      case EXPRESSION -> {
        if(expression==null)return deferred("Explicit recipient semantics are missing");
        var result=Objects.requireNonNull(resolver.resolveEach(expression,scope));
        if(result.status()!=Status.COMPLETE)return result;
        discovered=result.recipients();evidence=result.evidence();
      }
      case SOURCE,TARGET -> {
        if(!relationship)return deferred("Endpoint selectors require a relationship");
        if(source.getObservable().is(SemanticType.BIDIRECTIONAL))
          return deferred("An unoriented bond has no distinguished source or target endpoint");
        var endpoints=endpoints(source,selector,scope);
        if(endpoints.size()!=1)return deferred("The selected relationship endpoint is missing or ambiguous");
        discovered=List.copyOf(endpoints.values());
      }
      case DEFAULT -> {
        if(relationship) {
          if(source.getObservable().is(SemanticType.BIDIRECTIONAL)) {
            discovered=List.copyOf(scope.getRelationshipParticipants(source));
            if(discovered.size()!=2)return deferred("Bond conferral requires both committed participants");
          } else {
            var from=endpoints(source,Selector.SOURCE,scope);var to=endpoints(source,Selector.TARGET,scope);
            if(from.size()!=1||to.size()!=1)return deferred("Relationship conferral endpoints are missing or ambiguous");
            // Typed endpoints retain reflexive source=target evidence. The generic scope
            // participant query deduplicates it and cannot distinguish it from missing data.
            discovered=List.of(from.values().iterator().next(),to.values().iterator().next());
          }
        } else {
          var parent=scope.getParentOf(source);
          if(!(parent instanceof Observation bearer))return deferred("The process has no accessible substantial bearer");
          discovered=List.of(bearer);
        }
      }
      default -> throw new IllegalArgumentException("Unknown conferral recipient selector");
    }
    var recipients=new LinkedHashMap<Long,Observation>();
    for(var candidate:discovered) {
      if(scope.isInterrupted()||Thread.currentThread().isInterrupted())
        throw new InterruptedException("Consequence recipient discovery cancelled");
      var current=candidate==null||candidate.getId()<=0?null:scope.getObservation(candidate.getId());
      if(!substantial(current))return deferred("A recipient is unavailable, collective or not substantial");
      recipients.putIfAbsent(current.getId(),evidence==null?current:candidate);
    }
    return new Selection(Status.COMPLETE,new ArrayList<>(recipients.values()),"Recipient discovery completed",evidence);
  }

  private static LinkedHashMap<Long,Observation> endpoints(Observation source,Selector selector,ServiceContextScope scope) {
    var edge=selector==Selector.SOURCE?GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE:GraphModel.Relationship.HAS_RELATIONSHIP_TARGET;
    var result=new LinkedHashMap<Long,Observation>();
    for(var link:scope.getDigitalTwin().getKnowledgeGraph().getLinks(source,GraphModel.Relationship.Direction.OUTGOING,scope,edge))
      if(link.source().getId()==source.getId()&&link.target() instanceof Observation endpoint)result.put(endpoint.getId(),endpoint);
    return result;
  }

  private static boolean substantial(Observation observation) {
    if(observation==null||observation.isEmpty()||observation.getObservable()==null
        ||observation.getObservable().getSemantics().isCollective())return false;
    var value=observation.getObservable();
    return value.is(SemanticType.SUBJECT)||value.is(SemanticType.AGENT)
        ||value.is(SemanticType.EVENT)||value.is(SemanticType.RELATIONSHIP);
  }
  private static Selection deferred(String reason) { return new Selection(Status.DEFERRED,List.of(),reason); }
}
