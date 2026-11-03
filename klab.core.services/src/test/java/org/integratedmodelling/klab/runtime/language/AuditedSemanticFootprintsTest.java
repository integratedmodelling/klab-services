package org.integratedmodelling.klab.runtime.language;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuditedSemanticFootprintsTest {
  @TempDir Path temp;
  private static final String CLASSIFIER = "org.integratedmodelling.generators.library.RandomContextualizers";
  private static final String ADAPTER = "org.integratedmodelling.generators.adapters.RandomGeneratorAdapter";
  private Path fixture() throws Exception {
    Path file = temp.resolve("audited component with spaces.jar");
    try (var input = getClass().getResourceAsStream("/semantic-footprints/generators-audited.jar")) {
      assertNotNull(input); Files.copy(input, file);
    }
    return file;
  }
  private Extensions.ComponentDescriptor component(Path archive, String id, String version) {
    return new Extensions.ComponentDescriptor(id, Version.create(version), "Audited fixture", archive.toFile(),
        "advertised hash is deliberately not trusted", null, ResourcePrivileges.PUBLIC,
        List.of(),List.of(),List.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),"test",0,null,null,0);
  }
  private Extensions.ComponentDescriptor component(Path archive) {
    return component(archive,"klab.component.generators","1.0-SNAPSHOT");
  }
  private URLClassLoader loader(Path archive) throws Exception {
    return new URLClassLoader(new java.net.URL[] {archive.toUri().toURL()},getClass().getClassLoader()) {
      @Override public java.net.URL getResource(String name) {
        if(name.startsWith("org/integratedmodelling/generators/"))return findResource(name);
        return super.getResource(name);
      }
      @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
        if(!name.startsWith("org.integratedmodelling.generators.")) return super.loadClass(name,resolve);
        synchronized(getClassLoadingLock(name)) {
          var loaded=findLoadedClass(name); if(loaded==null)loaded=findClass(name);
          if(resolve)resolveClass(loaded);return loaded;
        }
      }
    };
  }

  @Test void actualAuditedClassifierRequiresClosureFootprintNotNoAuthorityAssertion() throws Exception {
    var archive=fixture();
    try (var loader=loader(archive)) {
      var type=Class.forName(CLASSIFIER,false,loader);
      var method=type.getMethod("generateConcept",Observable.class,ServiceCall.class,Scope.class);
      var proof=AuditedSemanticFootprints.forFunction(component(archive),method,"klab.generators.random.categories").orElseThrow();
      assertEquals(AuditedSemanticFootprints.Kind.CATEGORY_CLOSURE,proof.kind());
      assertEquals("cd5e7a3d3140e234c8ab6a51a624dbcdac4e073d4a466dc78bf9a5176e79475d",proof.archiveSha256());
      assertEquals(Set.of(CLASSIFIER),proof.implementationHashes().keySet());
      assertThrows(UnsupportedOperationException.class,()->proof.implementationHashes().clear());
      assertTrue(AuditedSemanticFootprints.forFunction(component(archive),method,"klab.generators.random.relationships").isEmpty());
      assertTrue(AuditedSemanticFootprints.forFunction(component(archive,"other","1.0-SNAPSHOT"),method,"klab.generators.random.categories").isEmpty());
      assertTrue(AuditedSemanticFootprints.forFunction(component(archive,"klab.component.generators","1.0.1-SNAPSHOT"),method,"klab.generators.random.categories").isEmpty());
    }
  }

  @Test void realAdapterAndHelperAreBoundOnlyForAuditedObjectsBranch() throws Exception {
    var archive=fixture();
    try (var loader=loader(archive)) {
      var type=Class.forName(ADAPTER,false,loader);
      var encoder=type.getMethod("encode",Urn.class,Storage.DoubleScanner.class,Data.Builder.class,
          Geometry.class,Observable.class,Scope.class);
      for(var resource:List.of("polygons","points","lines")) {
        var proof=AuditedSemanticFootprints.forAdapter(component(archive),type,encoder,
            "klab:random:objects:"+resource).orElseThrow();
        assertEquals(AuditedSemanticFootprints.Kind.DECLARED_OBSERVABLE,proof.kind());
        assertEquals(2,proof.implementationHashes().size());
      }
      for(var resource:List.of("klab:random:events:polygons","klab:random:data:uniform",
          "klab:random:objects:unreviewed","other:random:objects:polygons","klab:other:objects:polygons",""))
        assertTrue(AuditedSemanticFootprints.forAdapter(component(archive),type,encoder,resource).isEmpty(),resource);
      assertTrue(AuditedSemanticFootprints.forAdapter(component(archive),String.class,encoder,"klab:random:objects:polygons").isEmpty());
    }
  }

  @Test void sameNamedMethodFromAnotherArchiveCannotAcquireTheAttestation() throws Exception {
    var original=fixture();
    var different=temp.resolve("different manifest.jar");
    // Keep the same classes in a valid JAR, but change the admitted archive's other contents.
    try(var input=new java.util.zip.ZipFile(original.toFile());
        var output=new java.util.jar.JarOutputStream(Files.newOutputStream(different))) {
      for(var entry:input.stream().toList()) {
        output.putNextEntry(new java.util.jar.JarEntry(entry.getName()));
        if(!entry.isDirectory())try(var contents=input.getInputStream(entry)){contents.transferTo(output);}
        output.closeEntry();
      }
      output.putNextEntry(new java.util.jar.JarEntry("audit-extra.txt"));output.write(42);output.closeEntry();
    }
    try(var loader=loader(different)) {
      var type=Class.forName(CLASSIFIER,false,loader);
      var method=type.getMethod("generateConcept",Observable.class,ServiceCall.class,Scope.class);
      assertTrue(AuditedSemanticFootprints.forFunction(component(different),method,"klab.generators.random.categories").isEmpty());
      assertTrue(AuditedSemanticFootprints.forFunction(component(original),method,"klab.generators.random.categories").isEmpty(),
          "A selected class from another CodeSource is not the descriptor's implementation");
    }
  }

  @Test void missingAndWrongMethodsNeverCountAsAnEmptySemanticFootprint() throws Exception {
    var archive=fixture();
    assertTrue(AuditedSemanticFootprints.forFunction(component(archive),String.class.getMethod("length"),
        "klab.generators.random.categories").isEmpty());
    assertTrue(AuditedSemanticFootprints.forFunction(component(archive),null,"klab.generators.random.categories").isEmpty());
    assertTrue(AuditedSemanticFootprints.forFunction(null,String.class.getMethod("length"),"klab.generators.random.categories").isEmpty());
  }

  @Test void classAndHelperMutationIsUnknownEvenWithCorrectArchiveMetadata() throws Exception {
    var archive=fixture();
    try(var loader=loader(archive)) {
      var type=Class.forName(ADAPTER,false,loader);
      var encoder=type.getMethod("encode",Urn.class,Storage.DoubleScanner.class,Data.Builder.class,
          Geometry.class,Observable.class,Scope.class);
      var proof=AuditedSemanticFootprints.forAdapter(component(archive),type,encoder,"klab:random:objects:polygons").orElseThrow();
      var hashes=new HashMap<>(proof.implementationHashes());
      hashes.put("org.integratedmodelling.generators.utils.RandomShapes","0".repeat(64));
      assertTrue(AuditedSemanticFootprints.attest(new AuditedSemanticFootprints.Evidence(proof.componentId(),
          Version.create(proof.componentVersion()),proof.archiveSha256(),proof.contentInventorySha256(),proof.methodSignature(),hashes),proof.kind()).isEmpty());
      hashes=new HashMap<>(proof.implementationHashes());hashes.remove("org.integratedmodelling.generators.utils.RandomShapes");
      assertTrue(AuditedSemanticFootprints.attest(new AuditedSemanticFootprints.Evidence(proof.componentId(),
          Version.create(proof.componentVersion()),proof.archiveSha256(),proof.contentInventorySha256(),proof.methodSignature(),hashes),proof.kind()).isEmpty());
    }
  }
}
