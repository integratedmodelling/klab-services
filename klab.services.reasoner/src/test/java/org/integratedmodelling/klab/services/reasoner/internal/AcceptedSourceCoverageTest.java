package org.integratedmodelling.klab.services.reasoner.internal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Duration;import java.util.*;
import org.junit.jupiter.api.Test;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
class AcceptedSourceCoverageTest {
 @Test void acceptedActorCopiesStaticCoverageWithoutPromotingExecutableEvidence() throws Exception {
  for(boolean staticCoverage:List.of(true,false)){
   var factory=mock(SemanticEnvironmentFactory.class);var state=mock(SemanticEnvironment.class);
   when(state.actor()).thenReturn(mock(ReasonerService.class));
   when(state.semanticContentFingerprint()).thenReturn("accepted-recipe");
   when(factory.capture(any())).thenAnswer(i->{Worldview w=i.getArgument(0);return new CandidatePublicationCoordinator.Input<>(SemanticEnvironmentFactory.sources(w),SemanticEnvironmentFactory.sourceOrder(w),"dep",SemanticEnvironmentFactory.CONFIGURATION_REVISION,w,CandidatePublicationCoordinator.BeanStatus.VALID,List.of());});
   when(factory.build(any(),any())).thenReturn(new CandidatePublicationCoordinator.Result<>(state,CandidatePublicationCoordinator.LogicalStatus.VALID,true,List.of()));
   var ontology=new KimOntologyImpl();ontology.setUrn("w");ontology.setSourceCode("accepted");
   var world=new WorldviewImpl();world.setUrn("w");world.setWorldviewId("w");world.setEmpty(false);world.setOntologies(new ArrayList<>(List.of(ontology)));
   try(var runtime=new AcceptedSemanticRuntime(factory);var validators=mockConstruction(DocumentSemanticValidator.class,(v,c)->{when(v.validate(any())).thenReturn(List.of());when(v.isSourceSemanticsComplete()).thenReturn(staticCoverage);when(v.isSemanticObligationsComplete()).thenReturn(false);})){
    runtime.submit(world);assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
    var ns=new KimNamespaceImpl();ns.setUrn("fixture");ns.setSourceCode("fixture");
    var request=SemanticValidationRequest.of(ns,"1");
    var response=runtime.validate(request,SemanticValidationResponse.forRequest(request));
    assertTrue(response.valid(),response.getReason());assertEquals(staticCoverage,response.isSourceSemanticsComplete());
    assertFalse(response.isSemanticObligationsComplete());assertNotNull(response.getSemanticWitness());
    assertEquals(SemanticDependencyWitness.Status.UNKNOWN,response.getSemanticWitness().status());
    assertEquals(1,validators.constructed().size());
   }
  }
 }
}
