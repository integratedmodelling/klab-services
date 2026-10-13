package org.integratedmodelling.klab.services.resources.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;
import org.integratedmodelling.klab.services.resources.lang.WorldviewValidationScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class WorkspaceDomainIngestionTest {
  @TempDir Path directory;
  public static final String ROOT = "ontology imod in domain root version 1.0; "
      + "abstract domain Physical is core odo:PhysicalDomain; abstract domain Knowledge is core odo:KnowledgeDomain; "
      + "domain Life is imod:Physical; domain Biology \"Biological explanations grounded in physical Life.\" is imod:Knowledge about imod:Life;";
  private static final String LOCAL = "ontology biology using imod in domain biology:Biology version 1.0; "
      + "domain Biology is imod:Knowledge about imod:Life;";

  public static void set(Object target, String name, Object value) throws Exception {
    var field=WorkspaceManager.class.getDeclaredField(name);field.setAccessible(true);field.set(target,value);
  }
  public static WorkspaceManager manager(Path directory, Map<String,String> sources) throws Exception {
    var manager=mock(WorkspaceManager.class,CALLS_REAL_METHODS);
    var service=mock(ResourcesProvider.class);when(service.serviceId()).thenReturn("fixture");
    set(manager,"service",service);set(manager,"scope",mock(Scope.class));
    set(manager,"ontologyParser",new WorkspaceManager.OntologyParser());set(manager,"documentURLs",new HashMap<>());
    set(manager,"_ontologyMap",new HashMap<>());set(manager,"languageValidationScope",new WorldviewValidationScope());
    var storage=mock(ProjectStorage.class);var urls=new ArrayList<java.net.URL>();
    for(var entry:sources.entrySet()){var file=directory.resolve(entry.getKey()+".kwv");Files.writeString(file,entry.getValue());urls.add(file.toUri().toURL());}
    when(storage.listResources(ProjectStorage.ResourceType.ONTOLOGY)).thenReturn(urls);
    var manifest=mock(Project.Manifest.class);when(manifest.getDefinedWorldview()).thenReturn("imod");
    var descriptorType=Class.forName(WorkspaceManager.class.getName()+"$ProjectDescriptor");
    var ctor=descriptorType.getDeclaredConstructor(WorkspaceManager.class);ctor.setAccessible(true);var descriptor=ctor.newInstance(manager);
    for(var entry:Map.of("name","imod","storage",storage,"manifest",manifest).entrySet()){
      var field=descriptorType.getDeclaredField(entry.getKey());field.setAccessible(true);field.set(descriptor,entry.getValue());
    }
    set(manager,"projectDescriptors",new HashMap<>(Map.of("imod",descriptor)));
    return manager;
  }
  public static KimOntology read(WorkspaceManager manager,String source,String urn) throws Exception {
    var method=WorkspaceManager.class.getDeclaredMethod("readOntology",String.class,long.class,String.class,String.class);
    method.setAccessible(true);return (KimOntology)method.invoke(manager,source,0L,"imod",urn);
  }
  public static WorldviewValidationScope languageScope(WorkspaceManager manager) throws Exception {
    var field=WorkspaceManager.class.getDeclaredField("languageValidationScope");field.setAccessible(true);return (WorldviewValidationScope)field.get(manager);
  }
  @Test void singleDocumentReaderAlreadyExcludesItsOldSnapshot() throws Exception {
    var manager=manager(directory,Map.of("imod",ROOT));manager.getOntologies(false);
    var warm=read(manager,"ontology biology using imod in domain imod:Physical version 1.0; domain Biology is imod:Knowledge about imod:Life;","biology");
    languageScope(manager).addNamespace(warm);
    var edited=read(manager,LOCAL,"biology");
    var issue=edited.getNotifications().stream().filter(n->n.getMessage().contains("before its declaration: biology:Biology")).findFirst().orElseThrow();
    assertEquals(LOCAL.indexOf("biology:Biology"),issue.getLexicalContext().getOffsetInDocument());
    assertEquals("biology:Biology".length(),issue.getLexicalContext().getLength());
  }
  @Test void bulkReaderResetsPriorScopeAndRejectsLocalForwardDomain() throws Exception {
    var manager=manager(directory,Map.of("imod",ROOT,"biology",LOCAL));
    var previous=manager(directory,Map.of("imod",ROOT));previous.getOntologies(false);
    var warm=read(previous,"ontology biology using imod in domain imod:Physical version 1.0; domain Biology is imod:Knowledge about imod:Life;","biology");
    languageScope(previous).addNamespace(warm);set(manager,"languageValidationScope",languageScope(previous));
    var loaded=manager.getOntologies(false);var biology=loaded.stream().filter(o->o.getUrn().equals("biology")).findFirst().orElseThrow();
    assertTrue(biology.getNotifications().stream().anyMatch(n->n.getMessage().contains("before its declaration: biology:Biology")));
  }
  @Test void bulkLoaderOrdersAndResolvesNamedRootDomainFromActualDependencies() throws Exception {
    var source="ontology biology using imod in domain imod:Biology version 1.0;";
    var manager=manager(directory,Map.of("imod",ROOT,"biology",source));var loaded=manager.getOntologies(false);
    assertEquals(List.of("imod","biology"),loaded.stream().map(KimOntology::getUrn).toList());
    assertTrue(loaded.stream().allMatch(o->o.getNotifications().isEmpty()));
    assertEquals("imod:Biology",loaded.getLast().getDomain().getUrn());
  }
  @Test void bulkLoaderRejectsMissingImportAndUnknownNamedDomain() throws Exception {
    var missing=manager(directory,Map.of("biology","ontology biology using absent in domain absent:Biology version 1.0;"));
    assertTrue(missing.getOntologies(false).isEmpty());
    var unknown=manager(directory,Map.of("imod",ROOT,"biology","ontology biology using imod in domain imod:Missing version 1.0;"));
    var loaded=unknown.getOntologies(false);
    assertTrue(loaded.getLast().getNotifications().stream().anyMatch(n->n.getMessage().contains("imod:Missing")||n.getMessage().contains("unknown")));
  }
}
