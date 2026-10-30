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
class LandEntryHierarchySnapshotIntegrationTest {
  static final String ROOT="land:LandCover", CATALOG="corine_entry_hierarchy_example", URN="klab.authority.land";
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
  @Test void candidateArchivePassesHostRootChecksAndReplaysEntriesAndCodelistsAfterRemoval()throws Exception {
    var archive=Path.of(System.getProperty("candidate.land.jar")).toRealPath();
    try(var loader=new URLClassLoader(new java.net.URL[]{archive.toUri().toURL()},getClass().getClassLoader());
        var producer=(SnapshottingAuthority)loader.loadClass("org.integratedmodelling.klab.authority.land.LandAuthority").getConstructor().newInstance()) {
      assertEquals(archive,Path.of(producer.getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath());
      var parameters=Map.<String,Object>of("urn",URN,"mode","ENTRY_ONLY","domain","LULC","catalogs",List.of(CATALOG),
          "codelists",Map.of(CATALOG,"land.corine"));
      var request=new Authority.ConfigurationRequest("w","LAND",ROOT,parameters);
      var descriptor=new Extensions.AuthorityDescriptor(URN,Version.create("1.0.0"),true,true,List.of(),List.of(CATALOG));
      var data=Files.createDirectories(directory.resolve("producer"));
      var plugins=Files.createDirectories(data.resolve("components/plugins"));var owned=plugins.resolve("candidate-land.jar");Files.copy(archive,owned);
      var component=new Extensions.ComponentDescriptor(URN,Version.create("1.0.0-SNAPSHOT"),"candidate",owned.toFile(),
          hash("MD5",Files.readAllBytes(owned)),null,ResourcePrivileges.PUBLIC,List.of(),List.of(),List.of(descriptor),
          Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),"isolated-candidate",1,null,null,0);
      Files.write(data.resolve("components/catalog.json"),json.writeValueAsBytes(List.of(component)));
      var binding=new Worldview.AuthorityBinding("LAND",ROOT,"w",0,1,"fixture-source",descriptor,URN,component.version(),null);
      var statement=new KimConceptStatementImpl();statement.setUrn("LandCover");statement.setNamespace("land");
      statement.setAuthorityRequired("LAND");statement.setAuthorityParameters(parameters);
      var ontology=new KimOntologyImpl();ontology.setUrn("land");ontology.getStatements().add(statement);
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
      owl.requireOntology("land").define(List.of(Axiom.ClassAssertion("LandCover",EnumSet.of(SemanticType.IDENTITY))));
      var id=configure(host,request,scope);
      var six=bindings.alias("land.corine","BroadLeavedForest");
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
      assertEquals(six[1],replay.getCodelists(replayId).get(CATALOG).value("BroadLeavedForest"));
      assertFalse(replay.search("forest",null,replayId).isEmpty());
      var parent = replay.reconcile(replayId,Map.of("code","31")).getId();
      var grandparent = replay.reconcile(replayId,Map.of("code","3")).getId();
      assertEquals(List.of(parent),replay.resolveIdentity(replayId,six[1]).getParentIds());
      assertEquals(Authority.Subsumption.SUBSUMES,replay.subsumption(replayId,grandparent,six[1]));
      assertEquals(Authority.Subsumption.UNKNOWN,replay.subsumption(replayId,six[1],parent));
      assertEquals(1,replay.semanticDistance(replayId,grandparent,six[1]).distance());
      assertEquals(Authority.DistanceStatus.UNKNOWN,replay.semanticDistance(replayId,six[1],parent).status());
      assertFalse(Files.exists(directory.resolve("offline/interactive-authority-cache")));
      // Exercise the same frozen wrapper through the real resolver and public reasoner dispatch.
      var offlineBindings = new AuthorityBindings();
      offlineBindings.configure(request,replay);
      var offlineResolver = new AuthorityIdentityResolver(owl,offlineBindings);
      field(host,"authorityIdentityResolver",offlineResolver);
      field(host,"subsumption",com.github.benmanes.caffeine.cache.Caffeine.newBuilder().build());
      doReturn(0L).when(host).knowledgeRevision();
      var leafConcept=offlineResolver.resolve("land.corine","BroadLeavedForest");
      var parentConcept=offlineResolver.resolve("land.corine","Forest");
      assertTrue(host.is(leafConcept,parentConcept));
      assertFalse(host.is(parentConcept,leafConcept));
      assertTrue(host.is(leafConcept.collective(),parentConcept.collective()));
      assertFalse(host.is(parentConcept.collective(),leafConcept.collective()));
      assertEquals(1,host.authoritySemanticDistance(parentConcept,leafConcept).distance());
      replay.releaseConfiguration(replayId);
      assertThrows(IllegalStateException.class,()->replay.getCodelists(replayId));
    }
  }
}
