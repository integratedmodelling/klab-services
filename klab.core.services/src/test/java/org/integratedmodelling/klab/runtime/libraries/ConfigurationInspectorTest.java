package org.integratedmodelling.klab.runtime.libraries;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.data.impl.LinkImpl;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.*;
import org.integratedmodelling.klab.api.provenance.impl.ActivityImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.Test;
class ConfigurationInspectorTest {
 static class Fixture {
  final ContextScope scope=mock(ContextScope.class);final KnowledgeGraph graph=mock(KnowledgeGraph.class);final Reasoner reasoner=mock(Reasoner.class);
  final ObservationImpl cfg=new ObservationImpl(),ingredient=new ObservationImpl();final Cohort owner=mock(Cohort.class);final Observable meaning=mock(Observable.class);
  final ActivityImpl detected=(ActivityImpl)Activity.of(Activity.Type.DETECTION);final LinkImpl detection=new LinkImpl(detected,cfg,GraphModel.Relationship.DETECTED);
  Fixture() {
   var twin=mock(DigitalTwin.class);when(scope.getDigitalTwin()).thenReturn(twin);when(twin.getKnowledgeGraph()).thenReturn(graph);when(scope.getService(Reasoner.class)).thenReturn(reasoner);
   when(meaning.getUrn()).thenReturn("test:Network");when(meaning.is(SemanticType.CONFIGURATION)).thenReturn(true);when(reasoner.resolveObservable("test:Network")).thenReturn(meaning);
   when(scope.getId()).thenReturn("context");cfg.setId(10);cfg.setObservable(meaning);cfg.setGeometry(Geometry.UNIVERSAL);cfg.setParentId(30);
   ingredient.setId(20);ingredient.setObservable(typed("test:Link",SemanticType.RELATIONSHIP));when(owner.getId()).thenReturn(30L);when(owner.getObservable()).thenReturn(meaning);
   revision(10,Geometry.UNIVERSAL.encode(),List.of(20));perspective("context",0,0);
   cfg.getMetadata().put("klab.configuration.explanation",Utils.Json.asString(Map.of("outcome","RESOLVED")));cfg.getMetadata().put("klab.configuration.scientificInitialization","configuration-scientific-initialization-v1");
   detected.setId(40);detected.setOutcome(Activity.Outcome.SUCCESS);detection.properties().put("configurationRevision",1L);
   var caps=mock(Reasoner.Capabilities.class);when(caps.getServiceId()).thenReturn("reasoner");when(caps.getKnowledgeRevision()).thenReturn(1L);when(caps.getSemanticContentFingerprint()).thenReturn("semantic-content-v2:"+"a".repeat(64));when(reasoner.capabilities(scope)).thenReturn(caps);
   var request=new SemanticDependencyRequest("reasoner",1,caps.getSemanticContentFingerprint(),List.of("test:SourceModel","test:Network"));
   var proof=new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,SemanticDependencyWitness.Status.COMPLETE,"reasoner",1,caps.getSemanticContentFingerprint(),request.inputHash(),List.of(),"complete");
   detected.getMetadata().put(SemanticEvidenceBinding.DATA_KEY,Utils.Json.asString(new SemanticEvidenceBinding(SemanticEvidenceBinding.VERSION,true,request.obligations(),proof)));
   when(reasoner.semanticDependencies(any(),eq(scope))).thenReturn(proof);when(graph.getAsset(10L,scope,Observation.class)).thenReturn(cfg);
   when(graph.getAsset(20L,scope,Observation.class)).thenReturn(ingredient);
   when(graph.getLinks(cfg,GraphModel.Relationship.Direction.INCOMING,scope,GraphModel.Relationship.HAS_MEMBER)).thenReturn(List.of(new LinkImpl(owner,cfg,GraphModel.Relationship.HAS_MEMBER)));
   when(graph.getLinks(cfg,GraphModel.Relationship.Direction.OUTGOING,scope,GraphModel.Relationship.EMERGED_FROM)).thenReturn(List.of(new LinkImpl(cfg,ingredient,GraphModel.Relationship.EMERGED_FROM)));
   when(graph.getLinks(cfg,GraphModel.Relationship.Direction.INCOMING,scope,GraphModel.Relationship.DETECTED)).thenReturn(List.of(detection));
  }
  Observable typed(String urn,SemanticType type) {
   var observable=mock(Observable.class);var concept=mock(Concept.class);when(observable.getSemantics()).thenReturn(concept);
   when(concept.getType()).thenReturn(EnumSet.of(type));when(observable.getUrn()).thenReturn(urn);when(observable.is(type)).thenReturn(true);return observable;
  }
  void revision(long id,String support,List<Integer> participants) {
   cfg.getMetadata().put("klab.configuration.current",Utils.Json.asString(Map.of("observationId",id,"number",1,"triggeringCommitId",9,"support",support,"participants",participants)));
  }
  void perspective(String context,long observer,long focus) {
   cfg.getMetadata().put("klab.configuration.perspective",Utils.Json.asString(Map.of("contextId",context,"observerId",observer,"focusId",focus)));
  }
  ObservationImpl dependent() {
   var bearer=new ObservationImpl();bearer.setId(50);bearer.setObservable(typed("test:Bearer",SemanticType.SUBJECT));
   when(graph.getAsset(50L,scope,Observation.class)).thenReturn(bearer);cfg.setParentId(50);
   cfg.getMetadata().put("klab.configuration.applicationContext",50L);ingredient.setObservable(typed("test:Quality",SemanticType.QUALITY));
   when(graph.getLinks(cfg,GraphModel.Relationship.Direction.INCOMING,scope,GraphModel.Relationship.HAS_MEMBER)).thenReturn(List.of());
   when(graph.getLinks(cfg,GraphModel.Relationship.Direction.INCOMING,scope,GraphModel.Relationship.HAS_CHILD)).thenReturn(List.of(new LinkImpl(bearer,cfg,GraphModel.Relationship.HAS_CHILD)));
   when(graph.getLinks(ingredient,GraphModel.Relationship.Direction.INCOMING,scope,GraphModel.Relationship.HAS_CHILD)).thenReturn(List.of(new LinkImpl(bearer,ingredient,GraphModel.Relationship.HAS_CHILD)));
   return bearer;
  }
  boolean check(boolean modeled){return ConfigurationInspector.check(scope,cfg,"test:Network",1,modeled);}
 }
 @Test void requiresOwnCohortExactIngredientRevisionAndRealSuccessfulDetectionAccounting() {
  var f=new Fixture();assertTrue(f.check(true));f.detection.properties().put("configurationRevision",2L);assertFalse(f.check(true));verify(f.scope,never()).observation(any());
 }
 @Test void wrongCohortOrParticipantPayloadCannotMasqueradeAsConfiguration() {
  var f=new Fixture();when(f.owner.getObservable()).thenReturn(mock(Observable.class));assertFalse(f.check(false));
  when(f.owner.getObservable()).thenReturn(f.meaning);f.revision(10,Geometry.UNIVERSAL.encode(),List.of(99));assertFalse(f.check(false));
 }
 @Test void noModelOrMissingScientificInitializationDoesNotClaimModeledCompletion() {
  var f=new Fixture();f.cfg.getMetadata().put("klab.configuration.explanation",Utils.Json.asString(Map.of("outcome","NO_MODEL")));assertTrue(f.check(false));assertFalse(f.check(true));
  f.cfg.getMetadata().put("klab.configuration.explanation",Utils.Json.asString(Map.of("outcome","RESOLVED")));f.cfg.getMetadata().remove("klab.configuration.scientificInitialization");assertFalse(f.check(true));
 }
 @Test void unknownScientificEvidenceAndInvalidBoundsRemainFailClosedReadOnly() {
  var f=new Fixture();f.detected.getMetadata().remove(SemanticEvidenceBinding.DATA_KEY);assertFalse(f.check(false));
  assertThrows(IllegalArgumentException.class,()->ConfigurationInspector.await(f.scope,"test:Network",1,false,60001));when(f.scope.isInterrupted()).thenReturn(true);
  assertThrows(InterruptedException.class,()->ConfigurationInspector.await(f.scope,"test:Network",1,false,1));verify(f.scope,never()).observation(any());
 }
 @Test void semanticGenerationDriftDuringWitnessLookupCannotPassTheAssertion() {
  var f=new Fixture();var caps=f.reasoner.capabilities(f.scope);
  var request=new SemanticDependencyRequest("reasoner",1,caps.getSemanticContentFingerprint(),List.of("test:SourceModel","test:Network"));
  var proof=new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,SemanticDependencyWitness.Status.COMPLETE,"reasoner",1,caps.getSemanticContentFingerprint(),request.inputHash(),List.of(),"complete");
  doAnswer(call->{when(caps.getKnowledgeRevision()).thenReturn(2L);return proof;}).when(f.reasoner).semanticDependencies(any(),eq(f.scope));
  assertFalse(f.check(true));
 }
 @Test void actualDependentPlacementRequiresTypedCommonBearerAndExactApplicationContext() {
  var f=new Fixture();var bearer=f.dependent();assertTrue(f.check(true));
  f.cfg.getMetadata().put("klab.configuration.applicationContext",51L);assertFalse(f.check(false));
  f.cfg.getMetadata().put("klab.configuration.applicationContext",50L);f.ingredient.setObservable(f.typed("test:Subject",SemanticType.SUBJECT));assertFalse(f.check(false));
  f.ingredient.setObservable(f.typed("test:Quality",SemanticType.QUALITY));
  when(f.graph.getLinks(f.ingredient,GraphModel.Relationship.Direction.INCOMING,f.scope,GraphModel.Relationship.HAS_CHILD)).thenReturn(List.of());assertFalse(f.check(false));
  when(f.graph.getLinks(f.ingredient,GraphModel.Relationship.Direction.INCOMING,f.scope,GraphModel.Relationship.HAS_CHILD)).thenReturn(List.of(new LinkImpl(bearer,f.ingredient,GraphModel.Relationship.HAS_CHILD)));
  f.cfg.setParentId(99);assertFalse(f.check(false));
 }
 @Test void revisionMustNameActualObservationAndExactCurrentSupport() {
  var f=new Fixture();f.revision(99,Geometry.UNIVERSAL.encode(),List.of(20));assertFalse(f.check(false));
  f.revision(10,"different-support",List.of(20));assertFalse(f.check(false));
  f.revision(10,Geometry.UNIVERSAL.encode(),List.of(20));assertTrue(f.check(false));
 }
 @Test void callerPerspectiveMustMatchCanonicalReceiptWithoutGuessingFocusFromBearer() {
  var f=new Fixture();f.dependent();assertTrue(f.check(true)); // root request focus is zero; bearer is 50.
  f.perspective("foreign",0,0);assertFalse(f.check(false));f.perspective("context",7,0);assertFalse(f.check(false));
  f.perspective("context",0,50);assertFalse(f.check(false));
  f.perspective("context",0,0);var actualFocus=new ObservationImpl();actualFocus.setId(60);when(f.scope.getContextObservation()).thenReturn(actualFocus);assertFalse(f.check(false));
  f.perspective("context",0,60);assertTrue(f.check(false));
  var agent=new ObservationImpl();agent.setId(7);agent.setObservable(f.typed("test:Agent",SemanticType.AGENT));
  when(f.scope.getObserverId()).thenReturn(7L);when(f.scope.getObserver()).thenReturn(agent);assertFalse(f.check(false));
  f.perspective("context",7,60);assertTrue(f.check(false));
  agent.setObservable(f.typed("test:Thing",SemanticType.SUBJECT));assertFalse(f.check(false));
 }
 @Test void rootPlacementNeedsOwnTypedCohortAndExactFocusApplicationContext() {
  var f=new Fixture();var focus=new ObservationImpl();focus.setId(60);when(f.scope.getContextObservation()).thenReturn(focus);
  f.perspective("context",0,60);assertFalse(f.check(false));
  f.cfg.getMetadata().put("klab.configuration.applicationContext",60L);assertTrue(f.check(true));
  f.ingredient.setObservable(f.typed("test:Quality",SemanticType.QUALITY));assertFalse(f.check(false));
 }

}
