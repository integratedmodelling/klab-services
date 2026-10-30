package org.integratedmodelling.klab.services.resources.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.util.*;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.knowledge.organization.*;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.services.resources.*;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;
import org.integratedmodelling.klab.resources.FileProjectStorage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** IDs used here are produced by the real Resources loader, never fabricated on old payloads. */
public class AuthoringSourceStorageTest {
 @TempDir Path directory;
 static final String SOURCE=RejectedOntologyAuthoringTest.SOURCE;
 static final class Case {
  final RejectedOntologyAuthoringTest.Fixture fixture;final Path other;final String project;
  Case(Path folder,boolean identical,String project,String issuer)throws Exception {
   org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
   this.project=project;fixture=new RejectedOntologyAuthoringTest.Fixture(folder,false,false,SOURCE);
   other=fixture.source.resolveSibling("nested/actual.kwv");Files.createDirectories(other.getParent());Files.writeString(other,identical?SOURCE:SOURCE+"\n// selected second source");
   if(!project.equals("demo")){
    @SuppressWarnings("unchecked") var descriptors=(Map<String,Object>)RejectedOntologyAuthoringTest.get(fixture.manager,"projectDescriptors");
    descriptors.remove("demo");descriptors.put(project,fixture.ordinary);
    RejectedOntologyAuthoringTest.set(fixture.ordinary,"name",project);
    RejectedOntologyAuthoringTest.set(fixture.ordinary,"storage",new FileProjectStorage(fixture.source.getParent().getParent().toFile(),project,null));
    RejectedOntologyAuthoringTest.set(fixture.manager,"projectLocks",new HashMap<>(Map.of(project,WorkspaceManager.projectLockOwner(fixture.user))));
   }
   var service=(ResourcesProvider)RejectedOntologyAuthoringTest.get(fixture.manager,"service");when(service.serviceId()).thenReturn(issuer);when(service.canEditProject(project,fixture.user)).thenReturn(true);
   RejectedOntologyAuthoringTest.set(fixture.manager,"_ontologyOrder",null);fixture.manager.getOntologies(false);
  }
  Project view(){return fixture.manager.createProjectData(project,"workspace");}
  KimOntology selected(){return view().getOntologies().stream().filter(d->AuthoringSource.id(d).endsWith("actual.kwv")).findFirst().orElseThrow();}
  KimOntology sibling(){return view().getOntologies().stream().filter(d->AuthoringSource.id(d).equals("src/imod.kwv")).findFirst().orElseThrow();}
  List<ResourceSet> save(KimOntology before,String text){return fixture.manager.updateDocument(project,AuthoringSource.selector(before),ProjectStorage.ResourceType.ONTOLOGY,text,false,fixture.user);}
  void canonicalUnchanged(){assertEquals(WorkspaceDomainIngestionTest.ROOT,fixture.manager.getOntology("imod").getSourceCode());assertEquals(1,fixture.manager.getOntologies(true).size());}
 }
 public static Project producedProject(Path directory,boolean identical,String project,String issuer)throws Exception{return new Case(directory,identical,project,issuer).view();}
 static boolean error(List<ResourceSet> changes){return changes.stream().flatMap(c->c.getNotifications().stream()).anyMatch(n->n.getLevel()==org.integratedmodelling.klab.api.services.runtime.Notification.Level.Error);}
 static KimOntologyImpl edit(KimOntology original,String text){var document=new KimOntologyImpl();document.setUrn(original.getUrn());document.setProjectName(original.getProjectName());document.setServiceId(original.getServiceId());document.setSourceCode(text);AuthoringSource.copy(original,document);return document;}

 @Test void resourcesIssuesStableDistinctIdsEvenForIdenticalBytesAndJsonRoundtrip()throws Exception {
  var c=new Case(directory,true,"demo","issuer");var a=c.selected();var b=c.sibling();
  assertEquals(a.getSourceCode(),b.getSourceCode());assertNotEquals(AuthoringSource.id(a),AuthoringSource.id(b));
  assertNotEquals(AuthoringSource.key(a),AuthoringSource.key(b));assertEquals("issuer",a.getServiceId());
  assertSame(a,c.fixture.manager.getOntology(AuthoringSource.selector(a)));
  var mapper=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
  var transported=mapper.readValue(mapper.writeValueAsBytes(c.view()),Project.class);
  assertEquals(c.view().getOntologies().stream().map(AuthoringSource::selector).toList(),transported.getOntologies().stream().map(AuthoringSource::selector).toList());
  RejectedOntologyAuthoringTest.set(c.fixture.manager,"_ontologyOrder",null);c.fixture.manager.getOntologies(false);
  assertEquals(AuthoringSource.selector(a),AuthoringSource.selector(c.selected()));c.canonicalUnchanged();
 }
 @Test void explicitSaveAndDeferredSaveSelectActualFileAndKeepOtherQueueAndCanonical()throws Exception {
  var c=new Case(directory,false,"demo","issuer");var before=c.selected();var neighbor=Files.readAllBytes(c.fixture.source);
  var queued=mock(Class.forName(WorkspaceManager.class.getName()+"$DeferredDocument"));
  @SuppressWarnings("unchecked")var queue=(Map<String,Object>)RejectedOntologyAuthoringTest.get(c.fixture.manager,"deferredDocuments");queue.put("unrelated",queued);
  var text=SOURCE+"\n// save in noncanonical file";var changes=c.save(before,text);assertFalse(error(changes));assertEquals(text,Files.readString(c.other));assertArrayEquals(neighbor,Files.readAllBytes(c.fixture.source));
  assertEquals(2,changes.stream().mapToInt(r->r.getOntologies().size()).sum());assertTrue(c.selected().getNotifications().stream().anyMatch(n->n.getMessage().startsWith("DUPLICATE_ONTOLOGY_URN:")));
  var deferred=edit(c.selected(),text+"\n// deferred");assertFalse(error(c.fixture.manager.saveDeferred(deferred,c.fixture.user)));
  assertEquals(deferred.getSourceCode(),Files.readString(c.other));assertSame(queued,queue.get("unrelated"));c.canonicalUnchanged();
 }
 @Test void publicSubmitForwardsOriginalSourceIdentityRatherThanSemanticUrn()throws Exception {
  var c=new Case(directory,false,"demo","issuer");var before=c.selected();
  var provider=mock(ResourcesProvider.class,CALLS_REAL_METHODS);
  var field=ResourcesProvider.class.getDeclaredField("workspaceManager");field.setAccessible(true);field.set(provider,c.fixture.manager);
  doNothing().when(provider).requireProjectOperation(anyString(),any(),any());
  doReturn(null).when(provider).retrieve(anyString(),any(),any());
  doAnswer(call->c.fixture.manager.updateDocument(call.getArgument(0),call.getArgument(1),call.getArgument(2),call.getArgument(3),call.getArgument(4),call.getArgument(5))).when(provider).updateDocument(anyString(),anyString(),any(),anyString(),anyBoolean(),any());
  var payload=edit(before,SOURCE+"\n// public submission");assertFalse(error(provider.submit(payload,ResourcesService.SubmissionMode.UPDATE,c.fixture.user)));
  verify(provider).updateDocument(eq("demo"),eq(AuthoringSource.selector(before)),eq(ProjectStorage.ResourceType.ONTOLOGY),eq(payload.getSourceCode()),eq(false),eq(c.fixture.user));
  assertEquals(payload.getSourceCode(),Files.readString(c.other));assertEquals(SOURCE,Files.readString(c.fixture.source));
 }
 @Test void logicalRenameKeepsPhysicalOwnerAndClearsOnlyResolvedDuplicateDiagnostics()throws Exception {
  var c=new Case(directory,false,"demo","issuer");var a=c.selected();var key=AuthoringSource.key(a);var uri=AuthoringSource.modelUri(a);var neighbor=Files.readAllBytes(c.fixture.source);
  var renamed="ontology corrected in domain root version 1.0;\n// own physical file";var changes=c.save(a,renamed);assertFalse(error(changes));
  var after=c.selected();assertEquals("corrected",after.getUrn());assertEquals(key,AuthoringSource.key(after));assertEquals(uri,AuthoringSource.modelUri(after));assertEquals(renamed,Files.readString(c.other));assertArrayEquals(neighbor,Files.readAllBytes(c.fixture.source));
  assertTrue(c.sibling().getNotifications().stream().noneMatch(n->n.getMessage().startsWith("DUPLICATE_ONTOLOGY_URN:")));
  assertEquals(2,changes.stream().mapToInt(r->r.getOntologies().size()).sum());assertTrue(changes.stream().flatMap(r->r.getOntologies().stream()).allMatch(r->r.getOperation()==CRUDOperation.UPDATE && AuthoringSource.id(r)!=null));
  assertTrue(error(c.save(a,SOURCE+"\n// stale rename ack")));c.canonicalUnchanged();
 }
 @Test void selectedDeleteStagesOnlyItsFileAndDoesNotDeleteSameUrnSibling()throws Exception {
  var c=new Case(directory,true,"demo","issuer");var a=c.selected();
  try(var git=org.eclipse.jgit.api.Git.init().setDirectory(c.fixture.source.getParent().getParent().toFile()).call()){
   git.add().addFilepattern(".").call();git.commit().setMessage("fixture").setAuthor("fixture","fixture@localhost").setCommitter("fixture","fixture@localhost").call();
   var changes=c.fixture.manager.deleteDocument("demo",ProjectStorage.ResourceType.ONTOLOGY,AuthoringSource.selector(a),c.fixture.user);
   assertFalse(error(changes));assertFalse(Files.exists(c.other));assertTrue(Files.exists(c.fixture.source));
   assertEquals(Set.of("src/nested/actual.kwv"),git.status().call().getRemoved());
   assertEquals(1,c.view().getOntologies().size());assertTrue(c.sibling().getNotifications().stream().noneMatch(n->n.getMessage().startsWith("DUPLICATE_ONTOLOGY_URN:")));c.canonicalUnchanged();
  }
 }
 @Test void staleForeignForgedTraversalAndLegacyAmbiguityFailBeforeWriting()throws Exception {
  var c=new Case(directory,false,"demo","issuer");var a=c.selected();var originalA=Files.readAllBytes(c.other);var originalB=Files.readAllBytes(c.fixture.source);
  var bad=List.of("../outside.kwv","..\\outside.kwv","/outside.kwv","C:\\outside.kwv","\\\\server\\share\\outside.kwv","file:/outside.kwv","https://invalid/outside.kwv","%2e%2e%2foutside.kwv","src/a.kwv:stream","src/a.kwv?x","src/a.kwv#x","src/\\u0000.kwv","src/\\n.kwv");
  for(var path:bad){var forged=edit(a,SOURCE+"\n// must not write");forged.getMetadata().put(AuthoringSource.ID,path);assertTrue(error(c.save(forged,forged.getSourceCode())),path);}
  for(var coordinate:List.of("project","service","urn","version")){
   var forged=edit(a,SOURCE+"\n// must not write");
   if(coordinate.equals("project"))forged.setProjectName("foreign");if(coordinate.equals("service"))forged.setServiceId("foreign");if(coordinate.equals("urn"))forged.setUrn("foreign");if(coordinate.equals("version"))forged.getMetadata().put(AuthoringSource.VERSION,"stale");
   assertTrue(error(c.save(forged,forged.getSourceCode())),coordinate);
  }
  assertTrue(error(c.fixture.manager.updateDocument("demo","imod",ProjectStorage.ResourceType.ONTOLOGY,SOURCE+"\n// ambiguous",false,c.fixture.user)));
  assertTrue(error(c.fixture.manager.deleteDocument("demo",ProjectStorage.ResourceType.ONTOLOGY,"imod",c.fixture.user)));
  assertArrayEquals(originalA,Files.readAllBytes(c.other));assertArrayEquals(originalB,Files.readAllBytes(c.fixture.source));
  for(var blank:List.of(""," ")){var malformed=edit(a,SOURCE);malformed.getMetadata().put(AuthoringSource.ID,blank);assertThrows(IllegalArgumentException.class,()->AuthoringSource.selector(malformed));}
  Files.writeString(c.other,SOURCE+"\n// external edit");assertTrue(error(c.save(a,SOURCE+"\n// stale overwrite")));assertEquals(SOURCE+"\n// external edit",Files.readString(c.other));c.canonicalUnchanged();
 }
 @Test void sourceLessCreationWorksAndCreatedOrdinaryOntologyReceivesIdentityBeforePublication()throws Exception {
  var c=new Case(directory,false,"demo","issuer");
  var empty=c.fixture.manager.createEmptyDocument("demo",ProjectStorage.ResourceType.ONTOLOGY,"fresh",c.fixture.user);assertFalse(error(empty));
  var fresh=c.fixture.manager.getOntology("demo","fresh");assertNotNull(fresh);assertNotNull(AuthoringSource.id(fresh));assertNotNull(c.fixture.manager.getOntology(AuthoringSource.selector(fresh)));
  var newDocument=new KimOntologyImpl();newDocument.setUrn("another");newDocument.setProjectName("demo");newDocument.setSourceCode("ontology another in domain root version 1.0;");
  assertFalse(error(c.fixture.manager.createDocument(newDocument,c.view(),c.fixture.user)));assertNotNull(AuthoringSource.id(c.fixture.manager.getOntology("demo","another")));
  assertTrue(error(c.fixture.manager.createDocument(edit(fresh,"ontology selected in domain root version 1.0;"),c.view(),c.fixture.user)));assertFalse(Files.exists(c.fixture.source.resolveSibling("selected.kwv")));c.canonicalUnchanged();
 }
}
