package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.reasoner.internal.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.semanticweb.owlapi.apibinding.OWLManager;

/** Actual candidate archive, real host configuration method/root checks and frozen codelist replay.
 * No Resources service, live provider endpoint or whole-worldview acceptance is claimed. */
@EnabledIfSystemProperty(named="candidate.land.jar",matches=".+")
class LandEntrySnapshotIntegrationTest {
  static final String ROOT="w:CoverIdentity", CATALOG="igbp_modis61_draft", URN="klab.authority.land";
  @TempDir Path directory;
  private final ObjectMapper json=JacksonConfiguration.newObjectMapper();
  private static class TestOWL extends OWL {
    TestOWL(){super(mock(Scope.class));manager=OWLManager.createOWLOntologyManager();}
    @Override public synchronized void registerWithReasoner(Ontology ontology){}
    @Override public synchronized void flushReasoner(){}
  }
  private static void field(Object target,String name,Object value)throws Exception {
    var field=ReasonerService.class.getDeclaredField(name);field.setAccessible(true);field.set(target,value);
  }
  private static String configure(ReasonerService host,Authority.ConfigurationRequest request,Scope scope)throws Exception {
    var method=ReasonerService.class.getDeclaredMethod("configureAuthorityBinding",Authority.ConfigurationRequest.class,Scope.class);
    method.setAccessible(true);
    try{return (String)method.invoke(host,request,scope);}
    catch(java.lang.reflect.InvocationTargetException failure){
      if(failure.getCause() instanceof Exception cause)throw cause;throw failure;
    }
  }
  private static String hash(String algorithm,byte[] bytes)throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(bytes));
  }
  @Test void priorPublishedLandBinaryStillRequiresProviderUpgrade()throws Exception {
    var archive=Path.of(System.getProperty("published.land.jar")).toRealPath();
    assertEquals("bd3dce14680a1df044f95dae3c38093e8a5d9587c3dfcf36a92e35a4db34f2e2",hash("SHA-256",Files.readAllBytes(archive)));
    try(var loader=new URLClassLoader(new java.net.URL[]{archive.toUri().toURL()},getClass().getClassLoader())) {
      var provider=(Authority)loader.loadClass("org.integratedmodelling.klab.authority.land.LandAuthority").getConstructor().newInstance();
      assertFalse(provider instanceof SnapshottingAuthority);
      var request=new Authority.ConfigurationRequest("w","LAND",ROOT,Map.of("urn",URN,"mode","ENTRY_ONLY","domain","LULC","catalogs",List.of(CATALOG)));
      assertThrows(IllegalArgumentException.class,()->new AuthorityBindings().configure(request,provider));
    }
  }
  @Test void actualUnArchiveRetainsItsEmptyCodelistsThroughOfflineSnapshotReplay()throws Exception {
    var archive=Path.of(System.getProperty("compat.un.jar")).toRealPath();
    assertEquals("5d9d59e047168dce6a081062e31a92a995b594606c98e8f15844436a4bd305bb",hash("SHA-256",Files.readAllBytes(archive)));
    var urn="klab.authority.un";var scheme="https://example.org/icc";
    var request=new Authority.ConfigurationRequest("w","UN.ICC",ROOT,Map.of("urn",urn,"catalog","ICC","endpoint","http://127.0.0.1:1/sparql"));
    var rows=json.createArrayNode();
    for(var entry:Map.of("prefLabel","Crop","notation","1","inScheme",scheme).entrySet()) {
      var row=rows.addObject();row.putObject("concept").put("type","uri").put("value","https://example.org/crop");
      row.putObject("p").put("type","uri").put("value","http://www.w3.org/2004/02/skos/core#"+entry.getKey());
      var value=row.putObject("value").put("type",entry.getKey().equals("inScheme")?"uri":"literal").put("value",entry.getValue());
      if(entry.getKey().equals("prefLabel"))value.put("xml:lang","en");
    }
    var state=json.createObjectNode();state.set("classification",json.valueToTree(Map.of("id","ICC","family","ICC","version","1","scheme",scheme,"label","Fixture ICC")));
    state.put("language","en").put("searchLimit",25);state.set("rows",rows);
    var snapshot=new ConfiguredAuthoritySnapshot("un-caliper-skos-v1",urn,request,json.writeValueAsBytes(state));
    var data=Files.createDirectories(directory.resolve("un"));var plugins=Files.createDirectories(data.resolve("components/plugins"));
    var owned=plugins.resolve("un.jar");Files.copy(archive,owned);
    var descriptor=new Extensions.AuthorityDescriptor(urn,Version.create("1.0.0"),true,true,List.of(),List.of());
    var component=new Extensions.ComponentDescriptor(urn,Version.create("1.0.0-SNAPSHOT"),"UN compatibility",owned.toFile(),
        hash("MD5",Files.readAllBytes(owned)),null,ResourcePrivileges.PUBLIC,List.of(),List.of(),List.of(descriptor),
        Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),"isolated-fixture",1,null,null,0);
    Files.write(data.resolve("components/catalog.json"),json.writeValueAsBytes(List.of(component)));
    var binding=new Worldview.AuthorityBinding("UN.ICC",ROOT,"w",0,1,"fixture",descriptor,urn,component.version(),null);
    var statement=new KimConceptStatementImpl();statement.setUrn("CoverIdentity");statement.setNamespace("w");statement.setAuthorityRequired("UN.ICC");statement.setAuthorityParameters(request.parameters());
    var ontology=new KimOntologyImpl();ontology.setUrn("w");ontology.getStatements().add(statement);
    var source=new WorldviewImpl();source.setUrn("w");source.setWorldviewId("w");source.setEmpty(false);source.getOntologies().add(ontology);source.getAuthorityBindings().add(binding);
    var folder=Files.createDirectories(data.resolve("captured-authority-state/configured-snapshots"));
    var snapshotFile=folder.resolve(hash("SHA-256","UN.ICC".getBytes(java.nio.charset.StandardCharsets.UTF_8))+".json");Files.write(snapshotFile,json.writeValueAsBytes(snapshot));
    var catalog=CapturedAuthorityCatalog.captureRecorded(data,source,Map.of("UN.ICC",0L),CapturedAuthorityCatalog.captureProviderEvidence(data),Map.of("UN.ICC",hash("SHA-256",Files.readAllBytes(snapshotFile))));
    Files.delete(owned);var replay=catalog.provider(binding,request,directory.resolve("un-offline"),()->false);
    var id=replay.configure(request);
    assertTrue(replay.getCodelistDefinitions(id).isEmpty());
    assertEquals("Crop",replay.resolveIdentity(id,"1").getLabel());
    assertFalse(Files.exists(directory.resolve("un-offline/interactive-authority-cache")));
    replay.releaseConfiguration(id);
  }
  @Test void candidateArchivePassesHostRootChecksAndReplaysEntriesAndCodelistsAfterRemoval()throws Exception {
    var archive=Path.of(System.getProperty("candidate.land.jar")).toRealPath();
    try(var loader=new URLClassLoader(new java.net.URL[]{archive.toUri().toURL()},getClass().getClassLoader());
        var producer=(SnapshottingAuthority)loader.loadClass("org.integratedmodelling.klab.authority.land.LandAuthority").getConstructor().newInstance()) {
      assertEquals(archive,Path.of(producer.getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath());
      var parameters=Map.<String,Object>of("urn",URN,"mode","ENTRY_ONLY","domain","LULC","catalogs",List.of(CATALOG),
          "codelists",Map.of(CATALOG,"w.igbp"));
      var request=new Authority.ConfigurationRequest("w","LAND",ROOT,parameters);
      var descriptor=new Extensions.AuthorityDescriptor(URN,Version.create("1.0.0"),true,true,List.of(),List.of(CATALOG));
      var data=Files.createDirectories(directory.resolve("producer"));
      var plugins=Files.createDirectories(data.resolve("components/plugins"));var owned=plugins.resolve("candidate-land.jar");Files.copy(archive,owned);
      var component=new Extensions.ComponentDescriptor(URN,Version.create("1.0.0-SNAPSHOT"),"candidate",owned.toFile(),
          hash("MD5",Files.readAllBytes(owned)),null,ResourcePrivileges.PUBLIC,List.of(),List.of(),List.of(descriptor),
          Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),"isolated-candidate",1,null,null,0);
      Files.write(data.resolve("components/catalog.json"),json.writeValueAsBytes(List.of(component)));
      var binding=new Worldview.AuthorityBinding("LAND",ROOT,"w",0,1,"fixture-source",descriptor,URN,component.version(),null);
      var statement=new KimConceptStatementImpl();statement.setUrn("CoverIdentity");statement.setNamespace("w");
      statement.setAuthorityRequired("LAND");statement.setAuthorityParameters(parameters);
      var ontology=new KimOntologyImpl();ontology.setUrn("w");ontology.getStatements().add(statement);
      var source=new WorldviewImpl();source.setUrn("w");source.setWorldviewId("w");source.setEmpty(false);
      source.getOntologies().add(ontology);source.getAuthorityBindings().add(binding);
      var evidence=CapturedAuthorityCatalog.captureProviderEvidence(data); // real annotation discovery from owned archive
      assertTrue(evidence.containsKey(URN));
      var scope=mock(Scope.class);var owl=new TestOWL();
      var registry=mock(ComponentRegistry.class);when(registry.getAuthority(binding,scope)).thenReturn(producer);
      var host=mock(ReasonerService.class,CALLS_REAL_METHODS);doReturn(registry).when(host).getComponentRegistry();
      var bindings=new AuthorityBindings();field(host,"worldview",source);field(host,"owl",owl);
      field(host,"authorityBindings",bindings);field(host,"authorityBootstrapActor",true);
      assertThrows(org.integratedmodelling.klab.api.exceptions.KlabValidationException.class,()->configure(host,request,scope));
      assertTrue(bindings.configuredSnapshots().isEmpty());
      owl.requireOntology("w").define(List.of(Axiom.ClassAssertion("CoverIdentity",EnumSet.of(SemanticType.IDENTITY))));
      var id=configure(host,request,scope);
      var six=bindings.alias("w.igbp","ClosedShrubland");
      assertNotNull(six);assertEquals("LAND",six[0]);
      var snapshot=bindings.configuredSnapshots().get("LAND");assertNotNull(snapshot);
      var folder=Files.createDirectories(data.resolve("captured-authority-state/configured-snapshots"));
      var state=folder.resolve(hash("SHA-256","LAND".getBytes(java.nio.charset.StandardCharsets.UTF_8))+".json");
      Files.write(state,json.writeValueAsBytes(snapshot));
      var hashes=Map.of("LAND",hash("SHA-256",Files.readAllBytes(state)));
      assertThrows(UnsupportedOperationException.class,()->CapturedAuthorityCatalog.captureRecorded(data,source,Map.of(),evidence,hashes));
      var catalog=CapturedAuthorityCatalog.captureRecorded(data,source,Map.of("LAND",0L),evidence,hashes);
      assertTrue(catalog.supports(binding));
      producer.releaseConfiguration(id);producer.close();Files.delete(owned);
      var replay=catalog.provider(binding,request,directory.resolve("offline"),()->false);
      var replayId=replay.configure(AuthorityBindings.providerRequest(request));
      assertTrue(replay.resolveIdentity(replayId,six[1]).getNotifications().isEmpty());
      assertEquals(snapshot.request().rootIdentity(),replay.resolveIdentity(replayId,six[1]).getBaseIdentity());
      assertEquals(six[1],replay.getCodelists(replayId).get(CATALOG).value("ClosedShrubland"));
      assertFalse(replay.search("shrub",null,replayId).isEmpty());
      assertFalse(Files.exists(directory.resolve("offline/interactive-authority-cache")));
      replay.releaseConfiguration(replayId);
      assertThrows(IllegalStateException.class,()->replay.getCodelists(replayId));
    }
  }
}
