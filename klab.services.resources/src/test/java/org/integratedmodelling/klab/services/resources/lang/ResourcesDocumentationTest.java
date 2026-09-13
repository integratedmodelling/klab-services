package org.integratedmodelling.klab.services.resources.lang;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptStatementImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;
import org.integratedmodelling.klab.services.resources.storage.WorkspaceManager;
import org.junit.jupiter.api.Test;

class ResourcesDocumentationTest {
  @Test void bothInfoClassesDocumentSyntaxAndEnclosingDeclarationWithoutChangingTypedRetrieval() throws Exception {
    var service = mock(ResourcesProvider.class, CALLS_REAL_METHODS);
    var workspace = mock(WorkspaceManager.class);
    var field = ResourcesProvider.class.getDeclaredField("workspaceManager");
    field.setAccessible(true);
    field.set(service, workspace);
    var scope = mock(UserScope.class);
    var concept = new KimConceptImpl();
    concept.setName("audit:Child");
    var observable = new KimObservableImpl();
    observable.setSemantics(concept);
    var parent = new KimConceptStatementImpl();
    parent.setNamespace("audit"); parent.setUrn("Parent");
    var child = new KimConceptStatementImpl();
    child.setNamespace("audit"); child.setUrn("Child");
    child.setDocstring("A documented child.");
    when(workspace.conceptDeclarationPath("audit:Child")).thenReturn(List.of(parent, child));
    doReturn(concept).when(service).retrieve("audit:Child", KimConcept.class, scope);
    doReturn(observable).when(service).retrieve("audit:Child", KimObservable.class, scope);
    for (var cls : List.of(KnowledgeClass.CONCEPT, KnowledgeClass.OBSERVABLE)) {
      var markdown = service.info("audit:Child", cls, String.class, scope);
      assertTrue(markdown.contains("Syntactic documentation"));
      assertTrue(markdown.contains("Enclosing parent declaration: ` audit:Parent `"));
      assertTrue(markdown.contains("A documented child."));
    }
    assertSame(observable, service.info("audit:Child", KnowledgeClass.OBSERVABLE, KimObservable.class, scope));
  }
}
