package org.integratedmodelling.common.commandline;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.integratedmodelling.klab.api.cli.MarkdownDocument;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.junit.jupiter.api.Test;

class ReasonInfoTest {
  @Test void routesWholeExpressionThroughObservableInfoAndSupportsBothSwitches() {
    var scope = mock(UserScope.class);
    var reasoner = mock(Reasoner.class);
    var resources = mock(ResourcesService.class);
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    when(scope.getService(ResourcesService.class)).thenReturn(resources);
    var urn = "presence of audit:Tree";
    when(reasoner.info(urn, KnowledgeClass.OBSERVABLE, String.class, scope)).thenReturn("# Semantic");
    when(resources.info(urn, KnowledgeClass.OBSERVABLE, String.class, scope)).thenReturn("# Syntax");
    var cli = new KlabCommandLine(() -> scope);
    assertEquals(new MarkdownDocument("# Semantic"), cli.submit("reason info " + urn));
    assertEquals(new MarkdownDocument("# Syntax"), cli.submit("reason info -s " + urn));
    assertEquals(new MarkdownDocument("# Syntax"), cli.submit("reason info --syntax " + urn));
    verify(reasoner).info(urn, KnowledgeClass.OBSERVABLE, String.class, scope);
    verify(resources, times(2)).info(urn, KnowledgeClass.OBSERVABLE, String.class, scope);
  }

  @Test void missingUrnAndMissingResultAreVisibleErrors() {
    var scope = mock(UserScope.class);
    when(scope.getService(Reasoner.class)).thenReturn(mock(Reasoner.class));
    var cli = new KlabCommandLine(() -> scope);
    assertInstanceOf(Throwable.class, cli.submit("reason info"));
    assertInstanceOf(Throwable.class, cli.submit("reason info audit:Unknown"));
  }
}
