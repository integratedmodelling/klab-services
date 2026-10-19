package org.integratedmodelling.klab.services.resources.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.lang.reflect.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.organization.*;
import org.integratedmodelling.klab.api.knowledge.organization.impl.*;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.reasoner.objects.OntologyProjectPolicy;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.resources.*;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

public class RejectedOntologyAuthoringTest {
 @TempDir Path directory;
 @BeforeAll static void initialize(){org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();}
 static void set(Object target,String name,Object value)throws Exception{
  var type=target instanceof WorkspaceManager?WorkspaceManager.class:target.getClass();var f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);
 }
 static Object get(WorkspaceManager manager,String name)throws Exception{var f=WorkspaceManager.class.getDeclaredField(name);f.setAccessible(true);return f.get(manager);}
 static final String SOURCE="ontology imod in domain root version 1.0;\n// rejected authoring source";
 static final class Fixture {
  final WorkspaceManager manager; final UserScope user=mock(UserScope.class);final ProjectImpl.ManifestImpl manifest=new ProjectImpl.ManifestImpl();
  final Path source; final ProjectImpl externalProject; final KimOntology canonical; final Object ordinary;
  @SuppressWarnings("unchecked") Fixture(Path folder,boolean external,boolean ordinaryFirst,String text)throws Exception{
   Files.createDirectories(folder);manager=WorkspaceDomainIngestionTest.manager(folder,Map.of("imod",WorkspaceDomainIngestionTest.ROOT));
   set(manager,"projects",new LinkedHashMap<>());set(manager,"loading",new AtomicBoolean());
   set(manager,"deferredDocuments",new LinkedHashMap<>());set(manager,"syntaxOnly",ThreadLocal.withInitial(()->false));
   set(manager,"resourcesKbox",mock(ResourcesKBox.class));
   doReturn(List.of()).when(manager).getNamespaces();doReturn(List.of()).when(manager).getBehaviors();doReturn(List.of()).when(manager).getStrategyDocuments();
   var workspace=new WorkspaceImpl();workspace.setUrn("workspace");doReturn(workspace).when(manager).getWorkspace("workspace");
   var descriptors=(Map<String,Object>)get(manager,"projectDescriptors");var root=descriptors.get("imod");var type=root.getClass();
   var constructor=type.getDeclaredConstructor(WorkspaceManager.class);constructor.setAccessible(true);ordinary=constructor.newInstance(manager);
   set(ordinary,"name","demo");set(ordinary,"manifest",manifest);set(ordinary,"workspace","workspace");
   manifest.setWorldview("imod");
   source=folder.resolve("demo/src/imod.kwv");Files.createDirectories(source.getParent());Files.writeString(source,text);
   if(external){
    var ontology=new KimOntologyImpl();ontology.setUrn("imod");ontology.setProjectName("demo");ontology.setSourceCode(text);
    var location=new org.integratedmodelling.klab.api.services.runtime.impl.NotificationImpl.LexicalContextImpl();location.setOffsetInDocument(12);location.setLength(5);
    ontology.getNotifications().add(Notification.error(new IllegalStateException("retained external diagnostic"),location));
    externalProject=new ProjectImpl();externalProject.setUrn("demo");externalProject.setManifest(manifest);externalProject.setOntologies(List.of(ontology));
    set(ordinary,"externalProject",externalProject);((Map<String,Project>)get(manager,"projects")).put("demo",externalProject);
   }else{externalProject=null;set(ordinary,"storage",new FileProjectStorage(source.getParent().getParent().toFile(),"demo",null));}
   var ordered=new LinkedHashMap<String,Object>();if(ordinaryFirst)ordered.put("demo",ordinary);ordered.put("imod",root);ordered.put("demo",ordinary);set(manager,"projectDescriptors",ordered);
   var identity=mock(UserIdentity.class);when(identity.getId()).thenReturn("owner");when(user.getIdentity()).thenReturn(identity);
   set(manager,"projectLocks",new HashMap<>(Map.of("demo",WorkspaceManager.projectLockOwner(user))));
   var service=(ResourcesProvider)get(manager,"service");when(service.canEditProject("demo",user)).thenReturn(true);
   canonical=manager.getOntologies(true).getFirst();
  }
  Project project(){return externalProject==null?manager.createProjectData("demo","workspace"):manager.getProject("demo");}
  void unchangedCanonical(){assertSame(canonical,manager.getOntology("imod"));assertEquals(WorkspaceDomainIngestionTest.ROOT,canonical.getSourceCode());assertTrue(canonical.getNotifications().isEmpty());assertEquals(List.of(canonical),manager.getOntologies(true));}
 }
 public static Project retainedProject(Path folder,boolean external,boolean ordinaryFirst)throws Exception{return new Fixture(folder,external,ordinaryFirst,SOURCE).project();}

 @Test void localAndExternalCollisionRemainInProjectViewsInBothDiscoveryOrders()throws Exception{
  for(boolean external:List.of(false,true))for(boolean first:List.of(false,true)){
   var fixture=new Fixture(directory.resolve("case-"+external+"-"+first),external,first,SOURCE);var project=fixture.project();
   assertNotNull(project);assertEquals(1,project.getOntologies().size());var document=project.getOntologies().getFirst();
   assertEquals("demo",document.getProjectName());assertEquals(SOURCE,document.getSourceCode());assertTrue(document.isInactive());
   var diagnostic=document.getNotifications().stream().filter(n->n.getMessage().contains(OntologyProjectPolicy.CODE)).findFirst().orElseThrow();
   assertEquals("demo",diagnostic.getLexicalContext().getProjectUrn());assertEquals("imod",diagnostic.getLexicalContext().getDocumentUrn());
   assertSame(document,fixture.manager.getOntology("demo","imod"));assertSame(document,fixture.manager.retrieve("workspace/demo/imod",KimOntology.class));
   assertNull(fixture.manager.getOntology("other","imod"));assertNull(fixture.manager.retrieve("wrong/demo/imod",KimOntology.class));fixture.unchangedCanonical();
   if(external){assertNotSame(project,fixture.externalProject);assertFalse(fixture.externalProject.getOntologies().getFirst().isInactive());}
  }
 }
 @Test void retainedExternalAndParserDiagnosticsKeepTheirSourceContextAndStack()throws Exception{
  var external=new Fixture(directory.resolve("external"),true,false,SOURCE);var document=external.project().getOntologies().getFirst();
  var original=external.externalProject.getOntologies().getFirst().getNotifications().iterator().next();
  var retained=document.getNotifications().stream().filter(n->n.getMessage().equals(original.getMessage())).findFirst().orElseThrow();
  assertEquals(original.getStackTrace(),retained.getStackTrace());assertEquals(12,retained.getLexicalContext().getOffsetInDocument());assertEquals(5,retained.getLexicalContext().getLength());assertEquals("demo",retained.getLexicalContext().getProjectUrn());
  var malformed=SOURCE+"\n@";var local=new Fixture(directory.resolve("parser"),false,false,malformed);var parsed=local.project().getOntologies().getFirst();
  var parserIssues=parsed.getNotifications().stream().filter(n->!n.getMessage().contains(OntologyProjectPolicy.CODE)).toList();assertFalse(parserIssues.isEmpty());
  assertTrue(parserIssues.stream().allMatch(n->n.getLexicalContext()!=null && "demo".equals(n.getLexicalContext().getProjectUrn()) && n.getLexicalContext().getOffsetInDocument()>0));local.unchangedCanonical();
 }
 @Test void localSaveAndRenameStayInAuthoringProjectAndPublishOnlyProjectDiagnostics()throws Exception{
  var fixture=new Fixture(directory,false,false,SOURCE);fixture.project();var corrected=SOURCE+"\n// saved";
  var changes=fixture.manager.updateDocument("demo","imod",ProjectStorage.ResourceType.ONTOLOGY,corrected,false,fixture.user);
  assertEquals(corrected,Files.readString(fixture.source));fixture.unchangedCanonical();
  assertEquals(corrected,fixture.manager.getProject("demo").getOntologies().getFirst().getSourceCode());
  assertFalse(changes.isEmpty());assertTrue(changes.stream().allMatch(change->"workspace".equals(change.getWorkspace())));
  assertTrue(changes.stream().flatMap(change->change.getOntologies().stream()).allMatch(change->"demo".equals(change.getProjectUrn()) && change.getNotifications().stream().anyMatch(n->n.getMessage().contains(OntologyProjectPolicy.CODE))));
  var renamed="ontology renamed in domain root version 1.0;";fixture.manager.updateDocument("demo","imod",ProjectStorage.ResourceType.ONTOLOGY,renamed,false,fixture.user);
  assertNull(fixture.manager.getOntology("demo","imod"));assertEquals(renamed,fixture.manager.getOntology("demo","renamed").getSourceCode());fixture.unchangedCanonical();
 }
 @Test void deletingRejectedCollisionCannotDeleteCanonicalOntology()throws Exception{
  var fixture=new Fixture(directory,false,true,SOURCE);fixture.project();Files.delete(fixture.source);
  var changes=fixture.manager.handleFileChange("demo",List.of(Triple.of(ProjectStorage.ResourceType.ONTOLOGY,CRUDOperation.DELETE,fixture.source.toUri().toURL())));
  fixture.unchangedCanonical();assertNull(fixture.manager.getOntology("demo","imod"));assertTrue(fixture.manager.getProject("demo").getOntologies().isEmpty());
  assertEquals(CRUDOperation.DELETE,changes.getFirst().getOntologies().getFirst().getOperation());assertEquals("demo",changes.getFirst().getOntologies().getFirst().getProjectUrn());
 }
 @Test void duplicateRejectedSourcesKeepDiagnosticsAndRefuseArbitrarySave()throws Exception{
  var fixture=new Fixture(directory,false,false,SOURCE);var second=fixture.source.resolveSibling("other.kwv");var malformed=SOURCE+"\n@";Files.writeString(second,malformed);
  set(fixture.manager,"_ontologyOrder",null);fixture.manager.getOntologies(false);var project=fixture.project();assertEquals(2,project.getOntologies().size());
  assertTrue(project.getOntologies().stream().allMatch(d->d.getNotifications().stream().anyMatch(n->n.getMessage().startsWith("DUPLICATE_ONTOLOGY_URN:"))));
  assertTrue(project.getOntologies().stream().anyMatch(d->d.getNotifications().stream().anyMatch(n->!n.getMessage().contains(OntologyProjectPolicy.CODE) && !n.getMessage().startsWith("DUPLICATE_ONTOLOGY_URN:"))));
  assertNull(fixture.manager.getOntology("demo","imod"));var changes=fixture.manager.updateDocument("demo","imod",ProjectStorage.ResourceType.ONTOLOGY,SOURCE+"changed",false,fixture.user);
  assertTrue(changes.getFirst().getNotifications().stream().anyMatch(n->n.getMessage().contains("DUPLICATE_ONTOLOGY_URN")));assertEquals(SOURCE,Files.readString(fixture.source));assertEquals(malformed,Files.readString(second));
  assertEquals(WorkspaceDomainIngestionTest.ROOT,fixture.manager.getOntology("imod").getSourceCode());
 }
 @Test void eligibleDuplicateKeepsEarlierAndIncomingBoundParserErrorsInBothOrders()throws Exception{
  for(boolean invalidFirst:List.of(false,true)){
   var folder=Files.createDirectory(directory.resolve("eligible-"+invalidFirst));var malformed="ontology imod in domain root version 1.0;\n@";
   var sources=new LinkedHashMap<String,String>();if(invalidFirst)sources.put("bad",malformed);sources.put("good",WorkspaceDomainIngestionTest.ROOT);sources.put("bad",malformed);
   var manager=WorkspaceDomainIngestionTest.manager(folder,sources);var document=manager.getOntologies(true).getFirst();
   assertTrue(document.isInactive());assertTrue(document.getNotifications().stream().anyMatch(n->n.getMessage().startsWith("DUPLICATE_ONTOLOGY_URN:")));
   assertTrue(document.getNotifications().stream().anyMatch(n->!n.getMessage().startsWith("DUPLICATE_ONTOLOGY_URN:") && n.getLexicalContext()!=null && n.getLexicalContext().getOffsetInDocument()>0 && "imod".equals(n.getLexicalContext().getProjectUrn())));
  }
 }
 @SuppressWarnings("unchecked") @Test void rejectedDeferredSaveCannotFlushQueuedNamespaceOrReplaceCanonicalBaseline()throws Exception{
  var fixture=new Fixture(directory,false,false,SOURCE);fixture.project();
  var namespace=new KimNamespaceImpl();namespace.setUrn("demo.models");namespace.setProjectName("demo");namespace.setSourceCode("namespace demo.models;");
  var type=Class.forName(WorkspaceManager.class.getName()+"$DeferredDocument");var constructor=type.getDeclaredConstructors()[0];constructor.setAccessible(true);
  var queued=constructor.newInstance(WorkspaceManager.projectLockOwner(fixture.user),"demo","workspace",ProjectStorage.ResourceType.MODEL_NAMESPACE,fixture.source.resolveSibling("models.kim").toUri().toURL(),namespace,namespace);
  var queue=(Map<String,Object>)get(fixture.manager,"deferredDocuments");queue.put("namespace-pending",queued);
  var updated=new KimOntologyImpl();updated.setUrn("imod");updated.setProjectName("demo");updated.setSourceCode(SOURCE+"\n// deferred authoring save");
  var changes=fixture.manager.saveDeferred(updated,fixture.user);
  assertSame(queued,queue.get("namespace-pending"));assertEquals(1,queue.size());verify(fixture.manager,never()).validateDeferred(anyString(),any(UserScope.class));
  assertEquals(updated.getSourceCode(),Files.readString(fixture.source));fixture.unchangedCanonical();
  assertTrue(changes.stream().flatMap(change->change.getOntologies().stream()).flatMap(change->change.getNotifications().stream()).anyMatch(n->n.getMessage().contains(OntologyProjectPolicy.CODE)));
 }
 @Test void publicDeleteUsesRetainedFileAndNeverEntersCanonicalDependencyPath()throws Exception{
  var fixture=new Fixture(directory,false,false,SOURCE);fixture.project();
  var world=new org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl();world.setOntologies(new ArrayList<>(fixture.manager.getOntologies(true)));set(fixture.manager,"_worldview",world);
  var changes=fixture.manager.deleteDocument("demo",ProjectStorage.ResourceType.ONTOLOGY,"imod",fixture.user);
  assertFalse(Files.exists(fixture.source));fixture.unchangedCanonical();assertEquals(List.of(fixture.canonical),world.getOntologies());assertNull(fixture.manager.getOntology("demo","imod"));
  verify(fixture.manager,never()).dependents(anyString());assertTrue(changes.stream().allMatch(change->"workspace".equals(change.getWorkspace())));
  assertTrue(changes.stream().flatMap(change->change.getOntologies().stream()).allMatch(change->change.getOperation()==CRUDOperation.DELETE && "demo".equals(change.getProjectUrn())));
 }
 @Test void mismatchedFilenameSaveRenameDeferredAndDeleteUseOnlyTheRetainedPhysicalFile()throws Exception{
  var fixture=new Fixture(directory,false,false,SOURCE);var actual=fixture.source.resolveSibling("actual.kwv");Files.writeString(actual,SOURCE);
  var other="ontology other in domain root version 1.0;\n// unrelated source";Files.writeString(fixture.source,other);set(fixture.manager,"_ontologyOrder",null);fixture.manager.getOntologies(false);fixture.project();var canonicalAfterReload=fixture.manager.getOntology("imod");
  var edited=SOURCE+"\n// edit actual source";fixture.manager.updateDocument("demo","imod",ProjectStorage.ResourceType.ONTOLOGY,edited,false,fixture.user);
  assertEquals(edited,Files.readString(actual));assertEquals(other,Files.readString(fixture.source));assertSame(canonicalAfterReload,fixture.manager.getOntology("imod"));
  var blocked=fixture.manager.updateDocument("demo","imod",ProjectStorage.ResourceType.ONTOLOGY,other,true,fixture.user);
  assertTrue(blocked.getFirst().getNotifications().stream().anyMatch(n->n.getMessage().contains("DUPLICATE_ONTOLOGY_URN")));assertEquals(edited,Files.readString(actual));assertEquals(other,Files.readString(fixture.source));
  var renamed="ontology renamed in domain root version 1.0;\n// logical rename";fixture.manager.updateDocument("demo","imod",ProjectStorage.ResourceType.ONTOLOGY,renamed,false,fixture.user);
  assertEquals(renamed,Files.readString(actual));assertEquals(other,Files.readString(fixture.source));assertNull(fixture.manager.getOntology("demo","imod"));assertEquals(renamed,fixture.manager.getOntology("demo","renamed").getSourceCode());
  var deferred=new KimOntologyImpl();deferred.setUrn("renamed");deferred.setProjectName("demo");deferred.setSourceCode(renamed+"\n// deferred save");fixture.manager.saveDeferred(deferred,fixture.user);
  assertEquals(deferred.getSourceCode(),Files.readString(actual));assertEquals(other,Files.readString(fixture.source));
  fixture.manager.deleteDocument("demo",ProjectStorage.ResourceType.ONTOLOGY,"renamed",fixture.user);assertFalse(Files.exists(actual));assertEquals(other,Files.readString(fixture.source));assertNull(fixture.manager.getOntology("demo","renamed"));assertSame(canonicalAfterReload,fixture.manager.getOntology("imod"));
 }
 @Test void stalePhysicalContentRejectsSaveAndDeleteBeforeAnyMutation()throws Exception{
  var fixture=new Fixture(directory,false,false,SOURCE);fixture.project();var outsideEdit="ontology other in domain root version 1.0;\n// external edit";Files.writeString(fixture.source,outsideEdit);
  for(var changes:List.of(fixture.manager.updateDocument("demo","imod",ProjectStorage.ResourceType.ONTOLOGY,SOURCE+"changed",false,fixture.user),fixture.manager.deleteDocument("demo",ProjectStorage.ResourceType.ONTOLOGY,"imod",fixture.user)))
   assertTrue(changes.getFirst().getNotifications().stream().anyMatch(n->n.getMessage().contains("ONTOLOGY_SOURCE_CHANGED")));
  assertEquals(outsideEdit,Files.readString(fixture.source));fixture.unchangedCanonical();
 }
 @Test void publicCreatesAndAmbiguousDeleteCannotSelectOrDuplicateAnExistingSource()throws Exception{
  var fixture=new Fixture(directory,false,false,SOURCE);var actual=fixture.source.resolveSibling("actual.kwv");Files.move(fixture.source,actual);set(fixture.manager,"_ontologyOrder",null);fixture.manager.getOntologies(false);var project=fixture.project();
  var payload=new KimOntologyImpl();payload.setUrn("imod");payload.setProjectName("demo");payload.setSourceCode(SOURCE);
  assertFalse(fixture.manager.createDocument(payload,project,fixture.user).getFirst().getNotifications().isEmpty());
  assertFalse(fixture.manager.createEmptyDocument("demo",ProjectStorage.ResourceType.ONTOLOGY,"imod",fixture.user).getFirst().getNotifications().isEmpty());assertFalse(Files.exists(fixture.source));assertEquals(SOURCE,Files.readString(actual));
  Files.writeString(fixture.source,SOURCE);set(fixture.manager,"_ontologyOrder",null);fixture.manager.getOntologies(false);
  var changes=fixture.manager.deleteDocument("demo",ProjectStorage.ResourceType.ONTOLOGY,"imod",fixture.user);
  assertTrue(changes.getFirst().getNotifications().stream().anyMatch(n->n.getMessage().contains("DUPLICATE_ONTOLOGY_URN")));assertEquals(SOURCE,Files.readString(actual));assertEquals(SOURCE,Files.readString(fixture.source));assertEquals(WorkspaceDomainIngestionTest.ROOT,fixture.manager.getOntology("imod").getSourceCode());
 }
 @Test void malformedSourceSaveAtNoncanonicalFilenameKeepsTheRetainedDeclarationIdentity()throws Exception{
  var fixture=new Fixture(directory,false,false,SOURCE);var actual=fixture.source.resolveSibling("actual.kwv");Files.move(fixture.source,actual);set(fixture.manager,"_ontologyOrder",null);fixture.manager.getOntologies(false);fixture.project();var canonicalAfterReload=fixture.manager.getOntology("imod");
  var malformed="!!! broken source";fixture.manager.updateDocument("demo","imod",ProjectStorage.ResourceType.ONTOLOGY,malformed,false,fixture.user);
  assertEquals(malformed,Files.readString(actual));assertEquals("imod",fixture.manager.getOntology("demo","imod").getUrn());assertEquals(malformed,fixture.manager.getOntology("demo","imod").getSourceCode());assertFalse(Files.exists(fixture.source));assertSame(canonicalAfterReload,fixture.manager.getOntology("imod"));
 }
}
