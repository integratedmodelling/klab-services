package org.integratedmodelling.klab.services.reasoner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.EnumSet;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.services.reasoner.owl.QuantitativeContainmentSupport;
import org.junit.jupiter.api.Test;

class QuantitativeContainmentMatchingTest {
  private ConceptImpl concept(String name) {
    var c=new ConceptImpl();c.setUrn("test:"+name);c.setNamespace("test");c.setName(name);
    c.setType(EnumSet.of(SemanticType.QUALITY,SemanticType.VOLUME,SemanticType.EXTENSIVE));return c;
  }
  @Test void syntacticStrategyMatchingCannotTreatWholeMeasurementOrReversedPairAsPartialQuantity() {
    var reasoner=mock(ReasonerService.class);
    var partial=concept("Partial");var pattern=concept("Volume");
    partial.getMetadata().put(QuantitativeContainmentSupport.PORTION,"test:Oxygen");
    partial.getMetadata().put(QuantitativeContainmentSupport.WHOLE,"test:Air");
    doReturn(true).when(reasoner).is(partial,pattern);
    var matcher=new SyntacticMatcher(reasoner);
    assertFalse(matcher.doMatch(partial,pattern));
    pattern.getMetadata().put(QuantitativeContainmentSupport.PORTION,"test:Air");
    pattern.getMetadata().put(QuantitativeContainmentSupport.WHOLE,"test:Oxygen");
    assertFalse(matcher.doMatch(partial,pattern));
    pattern.getMetadata().put(QuantitativeContainmentSupport.PORTION,"test:Oxygen");
    pattern.getMetadata().put(QuantitativeContainmentSupport.WHOLE,"test:Air");
    assertTrue(matcher.doMatch(partial,pattern));
  }

  @Test void syntacticMatchingSeesQuantitativeHeadInsideOrdinaryWrapper() {
    var reasoner=mock(ReasonerService.class);
    var scope=mock(org.integratedmodelling.klab.api.scope.ServiceScope.class);
    var resources=mock(org.integratedmodelling.klab.api.services.ResourcesService.class);
    doReturn(scope).when(reasoner).serviceScope();
    doReturn(resources).when(scope).getService(org.integratedmodelling.klab.api.services.ResourcesService.class);
    var partialSyntax=new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl();
    partialSyntax.setName("test:Volume");partialSyntax.setType(EnumSet.of(SemanticType.QUALITY,SemanticType.VOLUME,SemanticType.EXTENSIVE,SemanticType.QUANTIFIABLE,SemanticType.OBSERVABLE));
    var portion=new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl();portion.setName("test:Oxygen");portion.setType(EnumSet.of(SemanticType.IDENTITY,SemanticType.PREDICATE));
    var whole=new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl();whole.setName("test:Air");whole.setType(EnumSet.of(SemanticType.IDENTITY,SemanticType.PREDICATE));
    partialSyntax.setPortionIdentity(portion);partialSyntax.setWholeIdentity(whole);partialSyntax.finalizeDefinition();
    var wrapper=new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl();wrapper.setObservable(partialSyntax);
    wrapper.setType(partialSyntax.getType());
    var region=new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl();region.setName("test:Region");region.setType(EnumSet.of(SemanticType.SUBJECT,SemanticType.OBSERVABLE,SemanticType.COUNTABLE));
    wrapper.setInherent(region);wrapper.finalizeDefinition();
    var candidate=concept("Candidate");candidate.setUrn(wrapper.getUrn());
    var pattern=concept("Pattern");pattern.setUrn(partialSyntax.getUrn());
    for(var c:java.util.List.of(candidate,pattern)) {
      c.getMetadata().put(QuantitativeContainmentSupport.PORTION,"test:Oxygen");
      c.getMetadata().put(QuantitativeContainmentSupport.WHOLE,"test:Air");
    }
    var candidateObservable=new org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl();candidateObservable.setSemantics(wrapper);
    var patternObservable=new org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl();patternObservable.setSemantics(partialSyntax);
    doReturn(candidateObservable).when(resources).declareObservable(candidate.getUrn());
    doReturn(patternObservable).when(resources).declareObservable(pattern.getUrn());
    assertTrue(new SyntacticMatcher(reasoner).doMatch(candidate,pattern));
  }
}
