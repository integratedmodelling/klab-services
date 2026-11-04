package org.integratedmodelling.klab.services.resources;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.resources.ModelCandidateRequest;
import org.integratedmodelling.klab.services.resources.storage.WorkspaceManager;
import org.junit.jupiter.api.Test;
class ModelCandidateSupplierTest {
 @Test void sourceSupplyEnforcesProjectReadsWithoutSemanticOrGraphParticipation() throws Exception {
  var provider=mock(ResourcesProvider.class,CALLS_REAL_METHODS);var manager=mock(WorkspaceManager.class);var user=mock(UserScope.class);
  var field=ResourcesProvider.class.getDeclaredField("workspaceManager");field.setAccessible(true);field.set(provider,manager);
  doReturn("supplier").when(provider).serviceId();doReturn(null).when(provider).getUrl();
  var sources=new ArrayList<KimNamespace>();
  for(var project:List.of("allowed","denied")) {
   var namespace=new KimNamespaceImpl();namespace.setUrn(project);namespace.setProjectName(project);namespace.setScope(KlabStatement.Scope.PUBLIC);namespace.setSourceCode("namespace "+project);
   var model=new KimModelImpl();model.setUrn(project+".model");model.setNamespace(project);model.setScope(KlabStatement.Scope.PUBLIC);namespace.getStatements().add(model);sources.add(namespace);
   doReturn(project.equals("allowed")).when(provider).canProjectOperation(project,CRUDOperation.READ,user);
  }
  when(manager.list(KimNamespace.class)).thenReturn(sources);
  var response=provider.modelCandidates(ModelCandidateRequest.from(null),user);
  assertEquals(List.of("allowed.model"),response.getResults().stream().map(r->r.getResourceUrn()).toList());
  verify(provider,never()).checkSemanticServices(any());
  verify(provider,never()).captureModelQueryView(any());
 }
}
