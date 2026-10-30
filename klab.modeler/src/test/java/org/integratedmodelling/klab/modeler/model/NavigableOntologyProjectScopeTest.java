package org.integratedmodelling.klab.modeler.model;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.knowledge.organization.impl.*;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.reasoner.objects.OntologyProjectPolicy;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.junit.jupiter.api.*;
public class NavigableOntologyProjectScopeTest {
 public static ProjectImpl project(String name,boolean worldview,String source){
  var document=new KimOntologyImpl();document.setUrn("imod");document.setProjectName(name);document.setSourceCode(source);
  if(!worldview){document.setInactive(true);document.getNotifications().add(OntologyProjectPolicy.diagnostic(document));}
  var project=new ProjectImpl();project.setUrn(name);var manifest=new ProjectImpl.ManifestImpl();manifest.setWorldview("imod");if(worldview)manifest.setDefinedWorldview("imod");project.setManifest(manifest);project.setOntologies(List.of(document));return project;
 }
 public static NavigableWorkspace workspace(){var workspace=new WorkspaceImpl();workspace.setUrn("workspace");workspace.setProjects(new ArrayList<>(List.of(project("canonical",true,"canonical source"),project("demo",false,"old rejected source"))));return new NavigableWorkspace(workspace);}
 public static ResourceSet.Resource change(CRUDOperation operation){var change=new ResourceSet.Resource();change.setOperation(operation);change.setProjectUrn("demo");change.setResourceUrn("imod");change.setKnowledgeClass(KnowledgeClass.ONTOLOGY);change.setServiceId("resources");return change;}
 private static KimOntology ontology(NavigableWorkspace root,String project){return root.findAsset(project,NavigableProject.class,KnowledgeClass.PROJECT).findAsset("imod",KimOntology.class,KnowledgeClass.ONTOLOGY);}
 @Test void updateAndDeleteUseOwningProjectAndQualifiedLookup() {
  var root=workspace();var resources=mock(ResourcesService.class);when(resources.serviceId()).thenReturn("resources");var user=mock(UserScope.class);when(user.findService(eq(ResourcesService.class),any())).thenReturn(Optional.of(resources));
  var updated=project("demo",false,"updated rejected source").getOntologies().getFirst();when(resources.retrieve("demo/imod",KimOntology.class,user)).thenReturn(updated);
  var changes=new ResourceSet();changes.getOntologies().add(change(CRUDOperation.UPDATE));root.mergeChanges(changes,user);
  assertEquals("updated rejected source",ontology(root,"demo").getSourceCode());assertEquals("canonical source",ontology(root,"canonical").getSourceCode());
  verify(resources).retrieve("demo/imod",KimOntology.class,user);verify(resources,never()).retrieve("imod",KimOntology.class,user);
  changes.getOntologies().clear();changes.getOntologies().add(change(CRUDOperation.DELETE));root.mergeChanges(changes,user);
  assertNull(ontology(root,"demo"));assertEquals("canonical source",ontology(root,"canonical").getSourceCode());
 }
 @Test void ambiguousOrMissingProjectCannotReplaceCanonicalRow(){
  var root=workspace();var resources=mock(ResourcesService.class);when(resources.serviceId()).thenReturn("resources");var user=mock(UserScope.class);when(user.findService(eq(ResourcesService.class),any())).thenReturn(Optional.of(resources));
  var changes=new ResourceSet();changes.getOntologies().add(change(CRUDOperation.UPDATE));assertDoesNotThrow(()->root.mergeChanges(changes,user));
  assertEquals("canonical source",ontology(root,"canonical").getSourceCode());assertEquals("old rejected source",ontology(root,"demo").getSourceCode());
  changes.getOntologies().getFirst().setProjectUrn("missing");assertDoesNotThrow(()->root.mergeChanges(changes,user));assertEquals("canonical source",ontology(root,"canonical").getSourceCode());
 }
 @Test void projectReceiverAppliesOwnUpdateAndDeleteButRejectsForeignProject(){
  var root=new NavigableProject(project("demo",false,"old source"),null);var resources=mock(ResourcesService.class);when(resources.serviceId()).thenReturn("resources");var user=mock(UserScope.class);when(user.findService(eq(ResourcesService.class),any())).thenReturn(Optional.of(resources));
  var updated=project("demo",false,"updated source").getOntologies().getFirst();when(resources.retrieve("demo/imod",KimOntology.class,user)).thenReturn(updated);
  var changes=new ResourceSet();changes.getOntologies().add(change(CRUDOperation.UPDATE));root.mergeChanges(changes,user);assertEquals("updated source",root.findAsset("imod",KimOntology.class,KnowledgeClass.ONTOLOGY).getSourceCode());
  changes.getOntologies().getFirst().setProjectUrn("foreign");root.mergeChanges(changes,user);assertEquals("updated source",root.findAsset("imod",KimOntology.class,KnowledgeClass.ONTOLOGY).getSourceCode());
  changes.getOntologies().getFirst().setProjectUrn("demo");changes.getOntologies().getFirst().setOperation(CRUDOperation.DELETE);root.mergeChanges(changes,user);assertNull(root.findAsset("imod",KimOntology.class,KnowledgeClass.ONTOLOGY));
 }
}
