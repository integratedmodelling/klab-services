package org.integratedmodelling.klab.components;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.*;
import java.lang.annotation.Annotation;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.jar.*;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.*;
import org.integratedmodelling.klab.api.services.resources.adapters.ResourceAdapter;
import org.integratedmodelling.klab.api.services.resources.adapters.Exporter;
import org.integratedmodelling.klab.api.services.runtime.extension.*;
import org.integratedmodelling.klab.extension.*;
import org.integratedmodelling.klab.services.base.BaseService;
import org.junit.jupiter.api.Test;
import org.pf4j.*;

/** Offline fix regressions. No network services or real Maven repositories.
 * Timestamp precedence is a control documenting unchanged behavior, pending a separate policy decision.
 */
class ComponentSynchronizationRegressionTest {
  static final String ID = "regression.sync.component";
  static final Version VERSION = Version.create("1.0-SNAPSHOT");
  static final String COORDINATES = "org.example:regression.sync.component:1.0-SNAPSHOT";
  static final String CALL = "regression.sync.value";

  @Library(name="regression.sync", description="Old offline contribution")
  public static class OldLibrary {
    @KlabFunction(name="value", description="Return old marker", type=Artifact.Type.TEXT)
    public static String value() { return "old"; }
  }
  @Library(name="regression.sync", description="New offline contribution")
  public static class NewLibrary {
    @KlabFunction(name="value", description="Return new marker", type=Artifact.Type.TEXT)
    public static String value() { return "new"; }
  }
  @ResourceAdapter(name="regression-sync",version="1.0.0",type=Artifact.Type.TEXT)
  public static class TestAdapter {
    @ResourceAdapter.Encoder public static void encode() {}
    @ResourceAdapter.Initializer public static boolean initialize() { return true; }
    @ResourceAdapter.Type public static Artifact.Type type() { return Artifact.Type.TEXT; }
    @ResourceAdapter.BatchIngestor public static boolean batch() { return true; }
    @Exporter(schema="regression-sync-export",description="Offline exporter",knowledgeClass=KnowledgeClass.RESOURCE,
        mediaType="application/octet-stream")
    public static void export() {}
  }
  static class Component extends KlabComponent {
    Component(PluginWrapper wrapper) { super(wrapper); }
  }
  static void field(Object target, String name, Object value) throws Exception {
    var f=ComponentRegistry.class.getDeclaredField(name); f.setAccessible(true); f.set(target,value);
  }
  static Extensions.ComponentDescriptor descriptor(File archive, String hash, long timestamp) {
    return new Extensions.ComponentDescriptor(ID, VERSION, "Offline candidate", archive, hash,
        COORDINATES, null, List.of(), List.of(), List.of(), new HashMap<>(), new HashMap<>(),
        new HashMap<>(), new HashMap<>(), new HashMap<>(), "resources-source", timestamp,
        Extensions.ComponentImportType.DEPENDENCY, Extensions.ComponentUpdateStatus.UP_TO_DATE, timestamp);
  }
  static Path directory(String label) throws Exception {
    var root=Path.of(System.getProperty("sync.regression.root", System.getProperty("java.io.tmpdir"))).toAbsolutePath().normalize();
    Files.createDirectories(root);
    var directory=Files.createTempDirectory(root,label+"-").toAbsolutePath().normalize();
    assertTrue(directory.startsWith(root)); return directory;
  }
  static Path archive(Path directory, String name, String marker) throws Exception {
    var path=directory.resolve(name);
    try(var jar=new JarOutputStream(Files.newOutputStream(path))) {
      jar.putNextEntry(new JarEntry("revision.txt")); jar.write(marker.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      jar.closeEntry();
    }
    return path;
  }
  static String marker(Path path) throws Exception {
    try(var jar=new JarFile(path.toFile());var input=jar.getInputStream(jar.getJarEntry("revision.txt"))) {
      return new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
    }
  }
  static void stop(ComponentRegistry registry) throws Exception {
    var f=ComponentRegistry.class.getDeclaredField("scheduler"); f.setAccessible(true);
    ((ScheduledExecutorService)f.get(registry)).shutdownNow();
  }
  static Extensions.FunctionDescriptor function(ComponentRegistry registry, String name, Class<?> owner) throws Exception {
    var info=new org.integratedmodelling.common.lang.ServiceInfoImpl(); info.setName(name);
    var create=ComponentRegistry.class.getDeclaredMethod("createFunctionDescriptor",
        org.integratedmodelling.klab.api.lang.ServiceInfo.class,Class.class,java.lang.reflect.Method.class);
    create.setAccessible(true);
    return (Extensions.FunctionDescriptor)create.invoke(registry,info,owner,owner.getMethod("value"));
  }

  @Test void promotedLocalSnapshotRetainsExistingTimestampPrecedence() throws Exception {
    var directory=directory("timestamp"); var pluginDirectory=Files.createDirectories(directory.resolve("plugins"));
    var fresh=archive(pluginDirectory,"component.jar","fresh-local-revision");
    var stale=archive(directory,"stale.jar","older-remote-revision");
    var cache=mock(MavenComponentCache.class); var owner=mock(BaseService.class);
    when(owner.serviceId()).thenReturn("runtime-owner");
    var localHash=org.integratedmodelling.klab.utilities.Utils.Files.hash(fresh.toFile());
    // Exact state after successful local promotion: installed hash and timestamp equal local artifact.
    var promoted=descriptor(fresh.toFile(),localHash,2000L);
    var sourceDescriptor=descriptor(stale.toFile(),org.integratedmodelling.klab.utilities.Utils.Files.hash(stale.toFile()),3000L);
    when(cache.findLocalArtifact("org.example",ID,"1.0-SNAPSHOT","component","kar"))
        .thenReturn(Optional.of(new MavenComponentCache.LocalArtifact(fresh.toFile(),localHash,2000L)));
    var registry=new ComponentRegistry(owner,null,cache,List.of(promoted));
    field(registry,"pluginPath",pluginDirectory.toFile());
    var source=mock(ResourcesService.class); var capabilities=mock(ResourcesService.Capabilities.class);
    when(source.serviceId()).thenReturn("resources-source"); when(source.capabilities(any())).thenReturn(capabilities);
    when(capabilities.getComponents()).thenReturn(List.of(sourceDescriptor));
    var schema=new ResourceTransport.Schema(); schema.setSchemaId("jar.export");
    schema.getMediaTypes().add("application/java-archive"); schema.setKnowledgeClass(KnowledgeClass.COMPONENT);
    when(capabilities.getExportSchemata()).thenReturn(Map.of("jar.export",List.of(schema)));
    when(source.exportAsset(anyString(),eq(KnowledgeClass.COMPONENT),anyString(),any(),any()))
        .thenAnswer(i->Files.newInputStream(stale));
    var scope=mock(UserScope.class);
    when(scope.findService(eq(ResourcesService.class),any())).thenReturn(Optional.of(source));
    try {
      var refresh=ComponentRegistry.class.getDeclaredMethod("refreshDependencyComponent",Extensions.ComponentDescriptor.class,
          org.integratedmodelling.klab.api.scope.Scope.class); refresh.setAccessible(true);
      // No plugin manager: allow actual selection/download/staging, deliberately prevent activation.
      var result=refresh.invoke(registry,promoted,scope);
      var pending=directory.resolve("pending-updates");
      List<Path> staged;
      if(Files.isDirectory(pending)) {
        try(var files=Files.list(pending)) { staged=files.filter(p->p.toString().endsWith(".jar")).toList(); }
      } else staged=List.of();
      assertTrue(staged.size()<=1);
      for(var candidate:staged) assertEquals("older-remote-revision",marker(candidate));
      assertEquals("fresh-local-revision",marker(fresh));
      System.out.println("TIMESTAMP_OBSERVATION result="+result+" local=2000 remote=3000 stagedCount="+staged.size());
      assertEquals(1,staged.size(),"Timestamp precedence is deliberately unchanged by this fix");
    } finally { stop(registry); }
  }

  @Test void retainedDescriptorIsRejectedAndReadersSeeCompleteRemovedStateDuringPublication() throws Exception {
    var directory=directory("publication"); var owner=mock(BaseService.class);
    when(owner.serviceId()).thenReturn("registry-owner");
    var registry=spy(new ComponentRegistry(owner,null,null,List.of()));
    field(registry,"catalogFile",directory.resolve("catalog.json").toFile());
    var oldArchive=archive(directory,"old.jar","old"); var newArchive=archive(directory,"new.jar","new");
    var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
    doAnswer(i->{
      KlabComponent component=i.getArgument(0);
      Map<Class<? extends Annotation>,BiConsumer<Annotation,Class<?>>> handlers=i.getArgument(1);
      var type=component.getWrapper().getPluginPath().equals(oldArchive)?OldLibrary.class:NewLibrary.class;
      handlers.get(Library.class).accept(type.getAnnotation(Library.class),type);
      if(type==NewLibrary.class) { entered.countDown(); assertTrue(release.await(5,TimeUnit.SECONDS)); }
      return null;
    }).when(registry).scanPackage(any(KlabComponent.class),anyMap());
    java.util.function.Function<Path,Component> component=path->{
      var descriptor=mock(PluginDescriptor.class); when(descriptor.getPluginId()).thenReturn(ID);
      when(descriptor.getVersion()).thenReturn("1.0-SNAPSHOT");
      var wrapper=mock(PluginWrapper.class); when(wrapper.getDescriptor()).thenReturn(descriptor);
      when(wrapper.getPluginPath()).thenReturn(path); return new Component(wrapper);
    };
    try(var executor=Executors.newSingleThreadExecutor()) {
      registry.registerComponent(component.apply(oldArchive),null,oldArchive.toFile());
      var retained=registry.getFunctionDescriptor(CALL,VERSION).getFirst();
      var oldMethod=registry.implementation(retained).method;
      assertEquals("old",oldMethod.invoke(null));
      var replacement=executor.submit(()->registry.registerComponent(component.apply(newArchive),null,newArchive.toFile()));
      try {
        assertTrue(entered.await(5,TimeUnit.SECONDS),"Replacement did not reach controlled publication boundary");
        // A separate reader must complete while discovery holds the writer monitor.
        var reader=CompletableFuture.runAsync(()->{
          assertNull(registry.getFunctionDescriptor(CALL,VERSION));
          assertNull(registry.getComponent(ID,VERSION));
          assertTrue(registry.getInstalledComponents(null).stream().noneMatch(c->ID.equals(c.id())));
          assertThrows(org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException.class,
              ()->registry.implementation(retained));
        });
        reader.get(1,TimeUnit.SECONDS);
      } finally { release.countDown(); }
      replacement.get(5,TimeUnit.SECONDS);
      var current=registry.getFunctionDescriptor(CALL,VERSION).getFirst();
      assertEquals("new",registry.implementation(current).method.invoke(null));
      assertThrows(org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException.class,
          ()->registry.implementation(retained),"Completed replacement must reject a retired descriptor too");
      assertEquals("old",oldMethod.invoke(null),"Already returned implementations are outside retirement lookup semantics");
      var transported=new Extensions.FunctionDescriptor(); transported.serviceInfo=current.serviceInfo;
      assertSame(registry.implementation(current),registry.implementation(transported),
          "Existing name fallback remains available for an unknown transported descriptor");
    } finally { release.countDown(); stop(registry); }
  }

  @Test void removingOneRegistryDoesNotEraseAnotherRegistrysSameNameFallback() throws Exception {
    var directory=directory("two-registries"); var owner=mock(BaseService.class);
    when(owner.serviceId()).thenReturn("fixture-owner");
    var helper=new ComponentRegistry(owner,null,null,List.of());
    var old=function(helper,"regression.shared.value",OldLibrary.class);
    var current=function(helper,"regression.shared.value",NewLibrary.class);
    var a=descriptor(null,null,1000); a.services().put("regression.shared.value",List.of(old));
    var b=descriptor(null,null,2000); b.services().put("regression.shared.value",List.of(current));
    var first=new ComponentRegistry(owner,null,null,List.of(a));
    var second=new ComponentRegistry(owner,null,null,List.of(b));
    field(first,"catalogFile",directory.resolve("first.json").toFile());
    try {
      assertTrue(first.unloadComponent(ID,VERSION));
      assertThrows(org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException.class,()->first.implementation(old));
      var transported=new Extensions.FunctionDescriptor(); transported.serviceInfo=current.serviceInfo;
      assertSame(second.implementation(current),second.implementation(transported));
      assertEquals("new",second.implementation(transported).method.invoke(null));
    } finally { stop(first); stop(second); stop(helper); }
  }

  @Test void actorConstructionDescriptorIsRetiredAlongWithVerbs() throws Exception {
    var directory=directory("actor-retirement"); var owner=mock(BaseService.class);
    when(owner.serviceId()).thenReturn("fixture-owner");
    var helper=new ComponentRegistry(owner,null,null,List.of());
    var adapter=function(helper,"regression.actor.create",OldLibrary.class);
    var actor=new Extensions.ActorDescriptor(); actor.urn="regression.actor"; actor.adapter=adapter;
    var component=descriptor(null,null,1000); component.actors().put(actor.urn,List.of(actor));
    var registry=new ComponentRegistry(owner,null,null,List.of(component));
    field(registry,"catalogFile",directory.resolve("catalog.json").toFile());
    try {
      assertNotNull(registry.implementation(adapter));
      assertTrue(registry.unloadComponent(ID,VERSION));
      assertThrows(org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException.class,()->registry.implementation(adapter));
    } finally { stop(registry); stop(helper); }
  }

  @Test void publishedInventoryDoesNotShareMutableContributionCollections() throws Exception {
    var owner=mock(BaseService.class); var original=descriptor(null,null,1000);
    var function=new Extensions.FunctionDescriptor();
    original.services().put("regression.inventory",new ArrayList<>(List.of(function)));
    var actor=new Extensions.ActorDescriptor(); original.actors().put("regression.actor",new ArrayList<>(List.of(actor)));
    var registry=new ComponentRegistry(owner,null,null,List.of(original));
    try {
      var held=registry.getInstalledComponents(null).iterator().next();
      original.services().get("regression.inventory").clear(); original.actors().clear();
      assertEquals(List.of(function),held.services().get("regression.inventory"));
      assertEquals(List.of(actor),held.actors().get("regression.actor"));
      assertThrows(UnsupportedOperationException.class,()->held.services().clear());
      assertThrows(UnsupportedOperationException.class,()->held.actors().get("regression.actor").clear());
    } finally { stop(registry); }
  }

  @Test void failedReplacementKeepsCompleteRemovedViewAndRejectsRetiredDescriptor() throws Exception {
    var directory=directory("failed-replacement"); var owner=mock(BaseService.class);
    when(owner.serviceId()).thenReturn("fixture-owner");
    var registry=spy(new ComponentRegistry(owner,null,null,List.of()));
    field(registry,"catalogFile",directory.resolve("catalog.json").toFile());
    var oldArchive=archive(directory,"old.jar","old"); var replacement=archive(directory,"new.jar","new");
    java.util.function.Function<Path,Component> component=path->{
      var descriptor=mock(PluginDescriptor.class); when(descriptor.getPluginId()).thenReturn(ID);
      when(descriptor.getVersion()).thenReturn("1.0-SNAPSHOT");
      var wrapper=mock(PluginWrapper.class); when(wrapper.getDescriptor()).thenReturn(descriptor);
      when(wrapper.getPluginPath()).thenReturn(path); return new Component(wrapper);
    };
    doAnswer(i->{
      KlabComponent current=i.getArgument(0);
      if(current.getWrapper().getPluginPath().equals(replacement)) throw new IllegalStateException("Fixture discovery failure");
      Map<Class<? extends Annotation>,BiConsumer<Annotation,Class<?>>> handlers=i.getArgument(1);
      handlers.get(Library.class).accept(OldLibrary.class.getAnnotation(Library.class),OldLibrary.class);
      return null;
    }).when(registry).scanPackage(any(KlabComponent.class),anyMap());
    try {
      registry.registerComponent(component.apply(oldArchive),null,oldArchive.toFile());
      var retained=registry.getFunctionDescriptor(CALL,VERSION).getFirst();
      assertThrows(IllegalStateException.class,()->registry.registerComponent(component.apply(replacement),null,replacement.toFile()));
      assertNull(registry.getComponent(ID,VERSION)); assertNull(registry.getFunctionDescriptor(CALL,VERSION));
      assertTrue(registry.getInstalledComponents(null).stream().noneMatch(c->ID.equals(c.id())));
      assertThrows(org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException.class,()->registry.implementation(retained));
    } finally { stop(registry); }
  }

  @Test void adapterRetirementCoversRetainedDescriptorsWithoutRootingDiscardedExportHelpers() throws Exception {
    var owner=mock(BaseService.class);
    var capabilities=mock(org.integratedmodelling.klab.api.services.KlabService.ServiceCapabilities.class);
    when(owner.capabilities(any())).thenReturn(capabilities);
    when(capabilities.getServiceId()).thenReturn("fixture-owner");
    when(capabilities.getType()).thenReturn(org.integratedmodelling.klab.api.services.KlabService.Type.RESOURCES);
    var registry=new ComponentRegistry(owner,null,null,List.of());
    var field=ComponentRegistry.class.getDeclaredField("functionImplementations"); field.setAccessible(true);
    var identities=(Map<?,?>)field.get(null); int before=identities.size();
    try {
      var adapter=registry.new AdapterImpl(TestAdapter.class,TestAdapter.class.getAnnotation(ResourceAdapter.class),ID,VERSION);
      var descriptors=new ArrayList<Extensions.FunctionDescriptor>(); descriptors.add(adapter.getEncoder());
      descriptors.addAll(adapter.getBatchAdapters());
      for(var name:List.of("initializer","typeAttributor")) {
        var descriptor=adapter.getClass().getDeclaredField(name); descriptor.setAccessible(true);
        descriptors.add((Extensions.FunctionDescriptor)descriptor.get(adapter));
      }
      assertEquals(4,descriptors.size());
      assertEquals(before+4,identities.size(),"Discarded exporter helper must not enter the strong identity index");
      for(var descriptor:descriptors) assertNotNull(registry.implementation(descriptor));
      var retire=ComponentRegistry.class.getDeclaredMethod("removeAdapterImplementations",
          org.integratedmodelling.klab.api.services.resources.adapters.Adapter.class); retire.setAccessible(true);
      retire.invoke(registry,adapter);
      assertEquals(before,identities.size());
      for(var descriptor:descriptors)
        assertThrows(org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException.class,()->registry.implementation(descriptor));
    } finally { stop(registry); }
  }
}
