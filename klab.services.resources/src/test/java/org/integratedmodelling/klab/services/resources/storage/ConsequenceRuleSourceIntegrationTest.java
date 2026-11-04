package org.integratedmodelling.klab.services.resources.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.services.reasoner.internal.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real parser/adapters, asynchronous source acceptance and public accepted-actor query route. */
class ConsequenceRuleSourceIntegrationTest {
  @TempDir Path directory;
  private static final String SOURCE="""
      ontology imod in domain root version 1.0;
      abstract thing Subject is core odo:Subject;
      abstract process Process is core odo:Process;
      abstract relationship Relationship is core odo:Relationship;
      abstract role Role is core odo:Role;
      abstract configuration Configuration is core odo:Configuration;
      abstract length Length is core odo:Length;
      thing Native is imod:Subject;
      thing Recipient is imod:Subject;
      thing Context is imod:Subject;
      length Quality is imod:Length;
      length OtherQuality is imod:Length;
      length ThirdQuality is imod:Length;
      role Conferred is imod:Role;
      role OtherRole is imod:Role;
      process Worker is imod:Process confers imod:Conferred;
      abstract relationship Connection is imod:Relationship confers imod:Conferred to source,
          imod:OtherRole /* recipient source */ to target, imod:Conferred to imod:Recipient within imod:Context;
      role Trigger is imod:Role implies imod:Quality of imod:Native for imod:Recipient within imod:Context, imod:OtherQuality;
      role TriggerAlias equals imod:Trigger;
      configuration Pattern is imod:Configuration emerges from
          (imod:Quality and (imod:OtherQuality or imod:ThirdQuality)) within imod:Context;
      configuration Shorthand is imod:Configuration /* shorthand source */ of
          imod:Quality;
      configuration Parent has children (Child is imod:Configuration emerges from imod:OtherQuality);
      """;

  private Worldview parsed(String label,String source) throws Exception {
    var manager=WorkspaceDomainIngestionTest.manager(Files.createDirectories(directory.resolve(label)),Map.of("imod",source));
    var ontologies=new ArrayList<>(manager.getOntologies(false));assertEquals(1,ontologies.size());
    assertTrue(ontologies.getFirst().getNotifications().stream().noneMatch(n->n.getLevel().severity>=Notification.Level.Error.severity),ontologies.getFirst().getNotifications().toString());
    var worldview=new WorldviewImpl();worldview.setUrn("imod");worldview.setWorldviewId("imod");worldview.setEmpty(false);worldview.setOntologies(ontologies);return worldview;
  }
  private AcceptedSemanticRuntime runtime(String label) throws Exception {
    byte[] core;try(var input=getClass().getResourceAsStream("/knowledge/odo.owl")) {assertNotNull(input);core=input.readAllBytes();}
    return new AcceptedSemanticRuntime(new SemanticEnvironmentFactory(directory.resolve(label),Map.of("odo.owl",core)),"parsed-consequence-fixture");
  }
  private void accept(AcceptedSemanticRuntime runtime,Worldview source) throws Exception {
    runtime.submit(source);assertTrue(runtime.awaitIdle(Duration.ofSeconds(30)),"Bounded source acceptance timed out");
    assertEquals(CandidatePublicationCoordinator.BeanStatus.VALID,runtime.status().beanStatus(),runtime.status().diagnostics().toString());
    assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,runtime.status().logicalStatus(),runtime.status().diagnostics().toString());
    assertTrue(runtime.status().acceptedGeneration()>0);
  }
  private Concept concept(AcceptedSemanticRuntime runtime,String name) {
    return runtime.invoke("resolveConcept",new Class<?>[]{String.class},new Object[]{"imod:"+name});
  }
  private ConsequenceRuleResponse query(AcceptedSemanticRuntime runtime,List<Concept> owners,List<Concept> ingredients,
      Set<KimConsequenceRule.Kind> kinds,ConsequenceRuleResponse.Epoch epoch) {
    return runtime.invoke("consequenceRules",new Class<?>[]{ConsequenceRuleQuery.class},
        new Object[]{new ConsequenceRuleQuery(owners,ingredients,kinds,epoch)});
  }
  @Test void freshlyParsedRulesReachTheAcceptedIndexWithSourceAliasesAndGenerationFences() throws Exception {
    var source=parsed("first",SOURCE);
    try(var runtime=runtime("accepted")) {
      accept(runtime,source);
      var response=query(runtime,List.of(concept(runtime,"Worker"),concept(runtime,"Connection"),concept(runtime,"Trigger")),
          List.of(concept(runtime,"Quality"),concept(runtime,"OtherQuality")),null,null);
      assertEquals(ConsequenceRuleResponse.Status.COMPLETE,response.status(),response.reason());assertTrue(response.epoch().valid());assertNotNull(response.witness());
      var counts=response.rules().stream().collect(java.util.stream.Collectors.groupingBy(e->e.rule().kind(),java.util.stream.Collectors.counting()));
      assertEquals(4L,counts.get(KimConsequenceRule.Kind.CONFERS));assertEquals(2L,counts.get(KimConsequenceRule.Kind.IMPLIES));assertEquals(3L,counts.get(KimConsequenceRule.Kind.EMERGES_FROM));
      var ontology=source.getOntologies().getFirst();String code=ontology.getSourceCode();
      for(var entry:response.rules()) {
        var span=entry.rule().source();assertEquals(code.substring(span.offset(),span.offset()+span.length()),span.source());
        assertEquals(SemanticValidationRequest.sourceHash(code),entry.sourceHash());assertEquals("imod",entry.sourceOntology());
        assertEquals(Long.toString(ontology.getLastUpdateTimestamp()),entry.sourceVersion());
      }
      var aliases=query(runtime,List.of(concept(runtime,"TriggerAlias")),List.of(),Set.of(KimConsequenceRule.Kind.IMPLIES),response.epoch());
      assertEquals(ConsequenceRuleResponse.Status.COMPLETE,aliases.status(),aliases.reason());
      assertEquals(Set.of("imod:Trigger"),aliases.rules().stream().map(ConsequenceRuleResponse.Entry::declarationUrn).collect(java.util.stream.Collectors.toSet()));
      assertEquals(2,aliases.rules().stream().map(ConsequenceRuleResponse.Entry::ruleId).distinct().count());
      var scoped=aliases.rules().stream().filter(e->e.declarationUrn().equals("imod:Trigger")).findFirst().orElseThrow().rule();
      assertEquals("imod:Native",scoped.target().getInherent().getUrn());assertEquals("imod:Recipient",scoped.secondaryInherency().getUrn());assertEquals("imod:Context",scoped.within().getUrn());
      var recipients=response.rules().stream().filter(e->e.declarationUrn().equals("imod:Connection")).map(e->e.rule().recipient().kind()).collect(java.util.stream.Collectors.toSet());
      assertEquals(Set.of(KimConsequenceRule.RecipientKind.SOURCE,KimConsequenceRule.RecipientKind.TARGET,KimConsequenceRule.RecipientKind.EXPRESSION),recipients);
      var pattern=response.rules().stream().filter(e->e.declarationUrn().equals("imod:Pattern")).findFirst().orElseThrow().rule();
      assertTrue(pattern.target().getUrn().contains("and"));assertTrue(pattern.target().getUrn().contains("or"));assertEquals("imod:Context",pattern.within().getUrn());
      var previousEpoch=response.epoch();var oldTrigger=concept(runtime,"Trigger");
      accept(runtime,parsed("second",SOURCE.replace("within imod:Context, imod:OtherQuality;","within imod:Context, imod:ThirdQuality;")));
      var stale=query(runtime,List.of(oldTrigger),List.of(),Set.of(KimConsequenceRule.Kind.IMPLIES),previousEpoch);
      assertEquals(ConsequenceRuleResponse.Status.STALE,stale.status());assertTrue(stale.rules().isEmpty());
      var current=query(runtime,List.of(concept(runtime,"Trigger")),List.of(),Set.of(KimConsequenceRule.Kind.IMPLIES),null);
      assertEquals(ConsequenceRuleResponse.Status.COMPLETE,current.status(),current.reason());assertTrue(current.epoch().knowledgeRevision()>previousEpoch.knowledgeRevision());
      assertEquals("imod:ThirdQuality",current.rules().stream().filter(e->e.rule().target().getUrn().equals("imod:ThirdQuality")).findFirst().orElseThrow().rule().target().getUrn());
    }
  }
  @Test void aPartiallyNormalizedSameKindClauseIsUnavailableRatherThanACompleteSubset() throws Exception {
    var source=parsed("partial",SOURCE.replace("role Trigger is imod:Role implies imod:Quality of imod:Native for imod:Recipient within imod:Context, imod:OtherQuality;",
        "role Trigger is imod:Role implies imod:Quality for imod:Recipient within imod:Context, imod:OtherQuality for imod:Recipient within imod:Context;"));
    var declaration=source.getOntologies().getFirst().getStatements().stream().filter(s->s.getUrn().equals("Trigger")).findFirst().orElseThrow();
    assertEquals(2,declaration.getConsequenceRules().size());assertTrue(declaration.getImpliedObservables().isEmpty(),"Scoped clauses cannot rely on a legacy flat list");
    declaration.getConsequenceRules().removeLast();
    try(var runtime=runtime("partial-accepted")) {
      accept(runtime,source);
      var response=query(runtime,List.of(concept(runtime,"Trigger")),List.of(),Set.of(KimConsequenceRule.Kind.IMPLIES),null);
      assertEquals(ConsequenceRuleResponse.Status.UNAVAILABLE,response.status());assertTrue(response.rules().isEmpty());
      assertNotNull(response.reason());
    }
  }
}
