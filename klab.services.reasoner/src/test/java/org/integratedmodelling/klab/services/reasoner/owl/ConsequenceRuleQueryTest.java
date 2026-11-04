package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.integratedmodelling.klab.services.reasoner.owl.ClassifierNativeAdmissionTest.field;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.reasoner.internal.AuthorityBindings;
import org.junit.jupiter.api.Test;

class ConsequenceRuleQueryTest {
  static class Fixture extends ClassifierNativeAdmissionTest.Fixture {
    final WorldviewImpl source=new WorldviewImpl();
    final ConsequenceRuleResponse.Epoch epoch=new ConsequenceRuleResponse.Epoch("native-admission-fixture",3,"semantic-content-v2:"+"a".repeat(64),"native-admission-fixture-owner");
    Fixture() throws Exception {
      declare("RoleRoot",SemanticType.ROLE,SemanticType.PREDICATE);declare("RoleChild",SemanticType.ROLE,SemanticType.PREDICATE);sub("RoleChild","RoleRoot");
      var statement=new KimConceptStatementImpl();statement.setUrn("RoleRoot");statement.setNamespace("audit");statement.setType(EnumSet.of(SemanticType.ROLE,SemanticType.PREDICATE));
      var ontologySource=new KimOntologyImpl();ontologySource.setUrn("audit");ontologySource.setSourceCode("ontology audit in domain root version 1.0; role RoleRoot implies audit:Input;");ontologySource.getStatements().add(statement);
      var target=register(leaf("Input"));
      statement.getConsequenceRules().add(new KimConsequenceRule(KimConsequenceRule.Kind.IMPLIES,target,KimConsequenceRule.Recipient.implicit(),null,null,
          new KimConceptStatement.DeclarationClause("impliesClause","audit:Input",ontologySource.getSourceCode().indexOf("audit:Input"),"audit:Input".length())));
      source.setUrn("audit");source.setWorldviewId("audit");source.setOntologies(List.of(ontologySource));
      field(reasoner,ReasonerService.class,"worldview",source);field(reasoner,ReasonerService.class,"authorityBindings",new AuthorityBindings());
      field(reasoner,ReasonerService.class,"consequenceRuleIndex",ConsequenceRuleIndex.capture(source,owl::getConcept,k->owl.getConcept(k.getUrn())));
      doReturn(capability(3)).when(reasoner).capabilities(any());clearInvocations(resources);
    }
    Reasoner.Capabilities capability(long revision) {
      var cap=mock(Reasoner.Capabilities.class);when(cap.getServiceId()).thenReturn(epoch.serviceId());when(cap.getKnowledgeRevision()).thenReturn(revision);
      when(cap.getSemanticContentFingerprint()).thenReturn(epoch.semanticFingerprint());when(cap.getSemanticSessionId()).thenReturn(epoch.semanticSessionId());return cap;
    }
    ConsequenceRuleQuery query(ConsequenceRuleResponse.Epoch expected){return new ConsequenceRuleQuery(List.of(concept("RoleChild")),List.of(),Set.of(KimConsequenceRule.Kind.IMPLIES),expected);}
  }
  @Test void completeCandidateEnumerationHasBoundEpochAndNoResourcesCallback() throws Exception {
    try(var f=new Fixture()) {
      var response=f.reasoner.consequenceRules(f.query(f.epoch));
      assertEquals(ConsequenceRuleResponse.Status.COMPLETE,response.status(),response.reason());assertEquals(f.epoch,response.epoch());
      assertEquals(1,response.rules().size());assertEquals("audit:RoleRoot",response.rules().getFirst().declarationUrn());
      assertNotNull(response.witness());verifyNoInteractions(f.resources);
    }
  }
  @Test void expectedEpochAndMidQueryChangesReturnNoPartialCandidates() throws Exception {
    try(var f=new Fixture()) {
      var stale=new ConsequenceRuleResponse.Epoch(f.epoch.serviceId(),4,f.epoch.semanticFingerprint(),f.epoch.semanticSessionId());
      var response=f.reasoner.consequenceRules(f.query(stale));assertEquals(ConsequenceRuleResponse.Status.STALE,response.status());assertTrue(response.rules().isEmpty());
      var reads=new AtomicInteger();doAnswer(i->f.capability(reads.incrementAndGet()==1?3:4)).when(f.reasoner).capabilities(any());
      response=f.reasoner.consequenceRules(f.query(f.epoch));assertEquals(ConsequenceRuleResponse.Status.STALE,response.status());assertTrue(response.rules().isEmpty());
      verifyNoInteractions(f.resources);
    }
  }
  @Test void missingSourceCapabilityIsNotSuccessfulEmptyDetection() throws Exception {
    try(var f=new Fixture()) {
      field(f.reasoner,ReasonerService.class,"consequenceRuleIndex",null);
      var response=f.reasoner.consequenceRules(f.query(f.epoch));assertEquals(ConsequenceRuleResponse.Status.UNAVAILABLE,response.status());assertTrue(response.rules().isEmpty());
      verifyNoInteractions(f.resources);
    }
  }
}
