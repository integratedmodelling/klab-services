package org.integratedmodelling.klab.services.runtime.digitaltwin;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.runtime.consequence.ConsequencePatternMatcher;
import org.integratedmodelling.klab.utilities.Utils;
import static org.integratedmodelling.klab.services.runtime.digitaltwin.ConfigurationConsequenceRuntime.*;

/** Serialization/persistence boundary only; these constructed values are NOT issued execution proof. */
class ConfigurationAuditSerializationTest {
 static final String FP="semantic-content-v2:"+"a".repeat(64);
 static final ConsequenceRuleResponse.Epoch EPOCH=new ConsequenceRuleResponse.Epoch("reasoner",1,FP,"session");
 static final SemanticDependencyRequest REQUEST=new SemanticDependencyRequest("reasoner",1,FP,List.of("test:Pair"));
 static final SemanticDependencyWitness WITNESS=new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,SemanticDependencyWitness.Status.COMPLETE,"reasoner",1,FP,REQUEST.inputHash(),List.of(),null);
 static final ConsequencePatternMatcher.Placement PLACEMENT=ConsequencePatternMatcher.Placement.context("context");
 static KnowledgeGraph.ConsequenceReadSet reads(String prior) {
  var props=Map.<String,Object>of("metadata",Utils.Json.asString(Map.of("klab.configuration.authorities",prior,"privateValue","PRIVATE-NATIVE-CANARY")),"configurationHistory",List.of(Utils.Json.asString(Map.of("authorities",prior))));
  var assets=List.of(new KnowledgeGraph.ConsequenceAssetSnapshot(41,"n41",List.of("Observation"),props,"1",null),new KnowledgeGraph.ConsequenceAssetSnapshot(42,"n42",List.of("Observation"),props,"1",null));
  return new KnowledgeGraph.ConsequenceReadSet("context",assets,List.of(new KnowledgeGraph.ConsequenceEdgeSelection(41,GraphModel.Relationship.EMERGED_FROM,GraphModel.Relationship.Direction.OUTGOING)),List.of());
 }
 static SourceReceipt source(KnowledgeGraph.ConsequenceReadSet reads) {
  var match=new ConsequencePatternMatcher.Match("rule","context",0,EPOCH,PLACEMENT,null,List.of(41L),Map.of("ingredient",List.of(41L)),"1",reads);
  var family=new ConsequenceRuleResponse(ConsequenceRuleResponse.Status.COMPLETE,EPOCH,List.of(),WITNESS,"serialization fixture");
  return new SourceReceipt(EPOCH,WITNESS,"{}","{}",match,Utils.Json.asString(family),REQUEST,WITNESS);
 }
 static AffectsReceipt affects(KnowledgeGraph.ConsequenceReadSet reads,String prior) {
  var plan=new RebuildAuthority(41,Utils.Json.asString(new DefaultConfigurationBackend.PriorAuthorityReference(DefaultConfigurationBackend.digest(prior),List.of(PLACEMENT))),DefaultConfigurationBackend.digest("explanation"),Utils.Json.asString(new ConsequenceRuleResponse(ConsequenceRuleResponse.Status.COMPLETE,EPOCH,List.of(),WITNESS,"fixture")),List.of());
  return new AffectsReceipt(41,Utils.Json.asString(plan),REQUEST,WITNESS,reads);
 }
 @Test void sourceAuditKeepsRecipeAndWitnessButNotOpaqueNativePayload()throws Exception {
  var reads=reads("old-authority");var receipt=source(reads);String json=Utils.Json.asString(receipt);
  var node=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper().readTree(json);
  assertFalse(node.path("match").has("reads"));assertFalse(json.contains("PRIVATE-NATIVE-CANARY"));
  assertEquals("rule",node.path("match").path("ruleId").asText());assertEquals("context",node.path("match").path("placement").path("contextId").asText());
  assertTrue(node.hasNonNull("sourceResponseJson"));assertTrue(node.hasNonNull("witness"));assertSame(reads,receipt.match().reads());
 }
 @Test void affectsAuditRetainsFullPrivateReadSetAndPriorHashWithoutInliningIt()throws Exception {
  var reads=reads("old-authority");var receipt=affects(reads,"old-authority");String json=Utils.Json.asString(receipt);
  var node=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper().readTree(json);
  assertFalse(node.has("dependencyReads"));assertFalse(json.contains("PRIVATE-NATIVE-CANARY"));assertSame(reads,receipt.dependencyReads());
  assertTrue(receipt.dependencyReads().assets().getFirst().nativeProperties().get("metadata").toString().contains("PRIVATE-NATIVE-CANARY"));
  var restored=Utils.Json.parseObject(node.path("modelPlanJson").asText(),RebuildAuthority.class);
  assertEquals(DefaultConfigurationBackend.digest("old-authority"),Utils.Json.parseObject(restored.priorAuthorities(),DefaultConfigurationBackend.PriorAuthorityReference.class).contentHash());
 }
 @Test void repeatedTwoConfigurationAuditDoesNotRecursivelyEmbedEarlierAudit() {
  String previous="[]";var lengths=new ArrayList<Integer>();
  for(int i=0;i<4;i++){var captured=reads(previous);previous=Utils.Json.asString(List.of(source(captured),affects(captured,previous)));lengths.add(previous.length());}
  System.out.println("CONFIGURATION_AUDIT_SIZE_REGRESSION "+lengths);
  assertTrue(lengths.getLast()<20000,"Audit sizes: "+lengths);
 }
 @Test void historicalPlacementAndSourceFamilyRemainReadable()throws Exception {
  var reads=reads("old");String json=Utils.Json.asString(List.of(source(reads)));
  assertEquals(List.of(PLACEMENT),DefaultConfigurationBackend.priorPlacements(json));
  var node=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper().readTree(json).get(0);
  assertEquals(EPOCH,Utils.Json.parseObject(node.path("sourceResponseJson").asText(),ConsequenceRuleResponse.class).epoch());
  assertEquals(List.of(PLACEMENT),DefaultConfigurationBackend.priorPlacements(Utils.Json.asString(List.of(affects(reads,json)))));
 }
 @Test void auditReferencesHashFullPayloadSupportStructureAndSelectionButAreNotNativeProof()throws Exception {
  var original=reads("old-authority");var receipt=affects(original,"old-authority");var reference=receipt.dependencyReadSetReference();
  assertEquals(1,reference.version());assertEquals(original.contextId(),reference.contextId());assertEquals(original.assetIds(),reference.assetIds());assertEquals(original.selections(),reference.selections());
  var mapper=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
  var bytes=mapper.writeValueAsBytes(original);assertTrue(new String(bytes,java.nio.charset.StandardCharsets.UTF_8).contains("PRIVATE-NATIVE-CANARY"));
  assertEquals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)),reference.sha256());
  assertEquals(reference,source(original).matchReadSetReference());assertSame(original,receipt.dependencyReads());
  assertNotEquals(reference.sha256(),affects(reads("changed-authority"),"old-authority").dependencyReadSetReference().sha256());
  var a=original.assets().getFirst();var changedSupport=new KnowledgeGraph.ConsequenceAssetSnapshot(a.id(),a.elementId(),a.labels(),a.nativeProperties(),"S2",a.perceivedGeometry());
  var supportReads=new KnowledgeGraph.ConsequenceReadSet(original.contextId(),List.of(changedSupport,original.assets().get(1)),original.selections(),original.edges());
  assertNotEquals(reference.sha256(),affects(supportReads,"old-authority").dependencyReadSetReference().sha256());
  var selectionReads=new KnowledgeGraph.ConsequenceReadSet(original.contextId(),original.assets(),List.of(new KnowledgeGraph.ConsequenceEdgeSelection(41,GraphModel.Relationship.AFFECTS,GraphModel.Relationship.Direction.INCOMING)),original.edges());
  assertNotEquals(reference.sha256(),affects(selectionReads,"old-authority").dependencyReadSetReference().sha256());
  var edge=new KnowledgeGraph.ConsequenceEdgeSnapshot("edge",new KnowledgeGraph.ConsequenceAssetRef("n41",41L,List.of("Observation")),new KnowledgeGraph.ConsequenceAssetRef("n42",42L,List.of("Observation")),GraphModel.Relationship.EMERGED_FROM,Map.of("revision",1L));
  var withEdge=new KnowledgeGraph.ConsequenceReadSet(original.contextId(),original.assets(),original.selections(),List.of(edge));
  var withChangedEdge=new KnowledgeGraph.ConsequenceReadSet(original.contextId(),original.assets(),original.selections(),List.of(new KnowledgeGraph.ConsequenceEdgeSnapshot(edge.elementId(),edge.source(),edge.target(),edge.relationship(),Map.of("revision",2L))));
  assertNotEquals(reference.sha256(),affects(withEdge,"old-authority").dependencyReadSetReference().sha256());
  assertNotEquals(affects(withEdge,"old-authority").dependencyReadSetReference().sha256(),affects(withChangedEdge,"old-authority").dependencyReadSetReference().sha256());
  assertThrows(Exception.class,()->mapper.readValue(Utils.Json.asString(reference),KnowledgeGraph.ConsequenceReadSet.class));
  assertThrows(RuntimeException.class,()->Utils.Json.parseObject(Utils.Json.asString(receipt),AffectsReceipt.class));
  assertSame(original,receipt.dependencyReads());
 }

}
