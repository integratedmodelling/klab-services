package org.integratedmodelling.klab.services.resources.storage;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.services.reasoner.objects.OntologyProjectPolicy;

class OntologyProjectPolicyTest {
 @TempDir Path directory;
 @SuppressWarnings("unchecked") static Project.Manifest manifest(WorkspaceManager manager)throws Exception {
  var projects=WorkspaceManager.class.getDeclaredField("projectDescriptors");projects.setAccessible(true);var descriptor=((Map<String,Object>)projects.get(manager)).get("imod");var field=descriptor.getClass().getDeclaredField("manifest");field.setAccessible(true);return (Project.Manifest)field.get(descriptor);
 }
 @Test void adoptsButDoesNotDefineWorldviewCannotContributeOntologyOrPublishItsConcepts()throws Exception {
  String source=WorkspaceDomainIngestionTest.ROOT;var manager=WorkspaceDomainIngestionTest.manager(directory,Map.of("imod",source));when(manifest(manager).getDefinedWorldview()).thenReturn(null);
  var document=manager.getOntologies(false).getFirst();assertTrue(document.isInactive());var issue=document.getNotifications().stream().filter(n->n.getMessage().contains(OntologyProjectPolicy.CODE)).findFirst().orElseThrow();
  assertEquals("imod",issue.getLexicalContext().getProjectUrn());assertEquals("imod",issue.getLexicalContext().getDocumentUrn());assertTrue(manager.getOntologies(true).isEmpty());assertEquals(source,Files.readString(directory.resolve("imod.kwv")));
 }
 @Test void allCommentedBodyStillReceivesProjectPolicyErrorInSingleAndBulkReaders()throws Exception {
  var source="ontology imod in domain root version 1.0;\n// abstract subject Forest;";var manager=WorkspaceDomainIngestionTest.manager(directory,Map.of("imod",source));when(manifest(manager).getDefinedWorldview()).thenReturn(" ");
  var bulk=manager.getOntologies(false).getFirst();var single=WorkspaceDomainIngestionTest.read(manager,source,"imod");
  for(var document:List.of(bulk,single))assertEquals(1,document.getNotifications().stream().filter(n->n.getMessage().contains(OntologyProjectPolicy.CODE)).count());
 }
 @Test void duplicateDeclaredUrnsAreRejectedRatherThanChoosingTheLastFile()throws Exception {
  String first="ontology imod in domain root version 1.0;",second=first+"\n// different physical source";
  var manager=WorkspaceDomainIngestionTest.manager(directory,Map.of("first",first,"second",second));var loaded=manager.getOntologies(false);
  assertEquals(1,loaded.size());assertTrue(loaded.getFirst().isInactive());assertTrue(loaded.getFirst().getNotifications().stream().anyMatch(n->n.getMessage().contains("DUPLICATE_ONTOLOGY_URN")));
  assertEquals(first,Files.readString(directory.resolve("first.kwv")));assertEquals(second,Files.readString(directory.resolve("second.kwv")));
 }
 @Test void definedWorldviewKeepsDependentOntologiesAndExistingDomainValidation()throws Exception {
  var manager=WorkspaceDomainIngestionTest.manager(directory,Map.of("imod",WorkspaceDomainIngestionTest.ROOT,"biology","ontology biology using imod in domain imod:Biology version 1.0;"));
  assertEquals(List.of("imod","biology"),manager.getOntologies(true).stream().map(KimOntology::getUrn).toList());assertTrue(manager.getOntologies(true).stream().allMatch(o->o.getNotifications().isEmpty()));
 }
 @SuppressWarnings("unchecked") @Test void multipleProjectsMayDefineTheSameWorldviewWithoutLosingTheirOntologies()throws Exception {
  var manager=WorkspaceDomainIngestionTest.manager(directory,Map.of("imod",WorkspaceDomainIngestionTest.ROOT));
  var field=WorkspaceManager.class.getDeclaredField("projectDescriptors");field.setAccessible(true);var descriptors=(Map<String,Object>)field.get(manager);
  var type=descriptors.get("imod").getClass();var ctor=type.getDeclaredConstructor(WorkspaceManager.class);ctor.setAccessible(true);var second=ctor.newInstance(manager);
  var file=directory.resolve("biology.kwv");Files.writeString(file,"ontology biology using imod in domain imod:Biology version 1.0;");
  var storage=mock(org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage.class);when(storage.listResources(org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage.ResourceType.ONTOLOGY)).thenReturn(List.of(file.toUri().toURL()));
  var manifest=mock(Project.Manifest.class);when(manifest.getDefinedWorldview()).thenReturn("imod");
  for(var entry:Map.of("name","extension","manifest",manifest,"storage",storage).entrySet()){var f=type.getDeclaredField(entry.getKey());f.setAccessible(true);f.set(second,entry.getValue());}
  descriptors.put("extension",second);
  var loaded=manager.getOntologies(true);assertEquals(List.of("imod","biology"),loaded.stream().map(KimOntology::getUrn).toList());assertEquals(List.of("imod","extension"),loaded.stream().map(KimOntology::getProjectName).toList());assertTrue(loaded.stream().allMatch(o->o.getNotifications().isEmpty()));
 }
 @SuppressWarnings("unchecked") @Test void unsupportedOrdinaryImportsAndWorldviewUrnCollisionCannotInvalidateTheWorldview()throws Exception {
  for(boolean collision:List.of(false,true))for(boolean ordinaryFirst:List.of(false,true)) {
   var folder=Files.createDirectory(directory.resolve("case-"+collision+"-"+ordinaryFirst));
   var manager=WorkspaceDomainIngestionTest.manager(folder,Map.of("imod",WorkspaceDomainIngestionTest.ROOT));
   var field=WorkspaceManager.class.getDeclaredField("projectDescriptors");field.setAccessible(true);var original=(Map<String,Object>)field.get(manager);var root=original.get("imod");
   var type=root.getClass();var ctor=type.getDeclaredConstructor(WorkspaceManager.class);ctor.setAccessible(true);var ordinary=ctor.newInstance(manager);
   String source="ontology "+(collision?"imod":"private")+" using absent in domain root version 1.0;";
   var file=folder.resolve("ordinary.kwv");Files.writeString(file,source);
   var storage=mock(org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage.class);when(storage.listResources(org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage.ResourceType.ONTOLOGY)).thenReturn(List.of(file.toUri().toURL()));
   var manifest=mock(Project.Manifest.class);
   for(var entry:Map.of("name","demo","manifest",manifest,"storage",storage).entrySet()){var f=type.getDeclaredField(entry.getKey());f.setAccessible(true);f.set(ordinary,entry.getValue());}
   var ordered=new LinkedHashMap<String,Object>();if(ordinaryFirst)ordered.put("demo",ordinary);ordered.put("imod",root);ordered.put("demo",ordinary);field.set(manager,ordered);
   var worldview=manager.getOntologies(true);assertEquals(1,worldview.size());assertEquals(WorkspaceDomainIngestionTest.ROOT,worldview.getFirst().getSourceCode());assertTrue(worldview.getFirst().getNotifications().isEmpty());assertEquals(source,Files.readString(file));
   if(!collision)assertTrue(manager.getOntologies(false).stream().filter(o->o.getUrn().equals("private")).findFirst().orElseThrow().getNotifications().stream().anyMatch(n->n.getMessage().contains(OntologyProjectPolicy.CODE)));
  }
 }
}
