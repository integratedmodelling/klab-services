package org.integratedmodelling.klab.services.reasoner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.junit.jupiter.api.Test;

class InherentProjectionTest {
  @Test
  void preservesExplicitCollectiveAndSingularInherents() {
    var reasoner = mock(ReasonerService.class, CALLS_REAL_METHODS);
    var scope = mock(ServiceScope.class);
    var resources = mock(ResourcesService.class);
    doReturn(scope).when(reasoner).serviceScope();
    when(scope.getService(ResourcesService.class)).thenReturn(resources);
    for (var collective : new boolean[] {true, false}) {
      String inherentUrn = (collective ? "each " : "") + "earth:Region";
      String definition = "earth:PhysicalEnvironment of " + inherentUrn;
      var concept = mock(Concept.class);
      when(concept.asConcept()).thenReturn(concept);
      when(concept.getUrn()).thenReturn(definition);
      var syntax = mock(KimConcept.class);
      var inherentSyntax = mock(KimConcept.class);
      when(resources.declareConcept(definition)).thenReturn(syntax);
      when(syntax.getInherent()).thenReturn(inherentSyntax);
      when(inherentSyntax.getUrn()).thenReturn(inherentUrn);
      var inherent = mock(Concept.class);
      when(inherent.isCollective()).thenReturn(collective);
      doReturn(inherent).when(reasoner).resolveConcept(inherentUrn);
      assertSame(inherent, reasoner.directInherent(concept));
      assertSame(inherent, reasoner.inherent(concept));
      assertEquals(collective, reasoner.directInherent(concept).isCollective());
    }
  }
}
