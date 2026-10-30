package org.integratedmodelling.klab.components;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.annotation.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.integratedmodelling.klab.extension.KlabComponent;
import org.integratedmodelling.klab.services.base.BaseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.*;

class ComponentRegistryOwnedArchiveTest {
  @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
  public @interface Marker {}
  @Marker public static class OwnedContribution {}
  @Marker public static class HostOnlyContribution {}
  public static class Component extends KlabComponent {
    public Component(PluginWrapper wrapper) { super(wrapper); }
  }
  @TempDir Path directory;

  @Test void developmentDiscoveryUsesItsClassRootWithoutScanningHostContributions() throws Exception {
    var root=Files.createDirectories(directory.resolve("development"));
    var classes=Files.createDirectories(root.resolve("target/classes"));
    String name=OwnedContribution.class.getName().replace('.','/')+".class";
    var target=classes.resolve(name);Files.createDirectories(target.getParent());
    try(var source=getClass().getClassLoader().getResourceAsStream(name)) {
      assertNotNull(source);Files.copy(source,target);
    }
    var descriptor=mock(PluginDescriptor.class);
    when(descriptor.getPluginId()).thenReturn("test.development");when(descriptor.getVersion()).thenReturn("1.0.0");
    var wrapper=mock(PluginWrapper.class);when(wrapper.getDescriptor()).thenReturn(descriptor);
    when(wrapper.getPluginPath()).thenReturn(root);
    try(var loader=new PluginClassLoader(mock(PluginManager.class),descriptor,getClass().getClassLoader())) {
      loader.addFile(classes.toFile());when(wrapper.getPluginClassLoader()).thenReturn(loader);
      var registry=new ComponentRegistry(mock(BaseService.class),null,null,List.of());
      var found=new ArrayList<String>();
      registry.scanPackage(new Component(wrapper),Map.of(Marker.class,(marker,type)->found.add(type.getName())));
      assertEquals(List.of(OwnedContribution.class.getName()),found);
    }
    Files.delete(target);
  }

  @Test void repeatedDiscoveryScansOnlyOwnedArchiveAndReleasesIdenticalCopies() throws Exception {
    byte[] archive;
    var bytes=new java.io.ByteArrayOutputStream();
    try(var jar=new JarOutputStream(bytes)) {
      String name=OwnedContribution.class.getName().replace('.','/')+".class";
      jar.putNextEntry(new JarEntry(name));
      try(var source=getClass().getClassLoader().getResourceAsStream(name)) {
        assertNotNull(source);source.transferTo(jar);
      }
      jar.closeEntry();
    }
    archive=bytes.toByteArray();
    for(int cycle=0;cycle<6;cycle++) {
      var folder=Files.createDirectories(directory.resolve("cycle-"+cycle));
      var original=folder.resolve("original.jar");var owned=folder.resolve("owned.jar");
      Files.write(original,archive);Files.write(owned,archive);
      var descriptor=mock(PluginDescriptor.class);
      when(descriptor.getPluginId()).thenReturn("test.owned");when(descriptor.getVersion()).thenReturn("1.0.0");
      var wrapper=mock(PluginWrapper.class);when(wrapper.getDescriptor()).thenReturn(descriptor);
      when(wrapper.getPluginPath()).thenReturn(owned);
      try(var loader=new PluginClassLoader(mock(PluginManager.class),descriptor,getClass().getClassLoader())) {
        loader.addFile(original.toFile());loader.addFile(owned.toFile());
        when(wrapper.getPluginClassLoader()).thenReturn(loader);
        var registry=new ComponentRegistry(mock(BaseService.class),null,null,List.of());
        var found=new ArrayList<String>();
        registry.scanPackage(new Component(wrapper),Map.of(Marker.class,(marker,type)->found.add(type.getName())));
        assertEquals(List.of(OwnedContribution.class.getName()),found,
            "Discovery must not admit a same-package contribution from the host classpath");
      } finally {
        // These must succeed immediately on Windows too, after explicit loader/scan closure.
        Files.delete(owned);Files.delete(original);
      }
    }
  }
}
