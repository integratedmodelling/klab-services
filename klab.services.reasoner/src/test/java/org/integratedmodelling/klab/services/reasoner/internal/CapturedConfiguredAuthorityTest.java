package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CapturedConfiguredAuthorityTest {
  @TempDir Path directory;
  private final ObjectMapper json=JacksonConfiguration.newObjectMapper();
  private record Fixture(Path data,Path archive,Path state,WorldviewImpl source,
      Worldview.AuthorityBinding binding,Authority.ConfigurationRequest request,Map<String,String> hashes){}
  private String digest(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
  private Fixture fixture()throws Exception{
    Path data=Files.createDirectories(directory.resolve("data with space")),plugins=Files.createDirectories(data.resolve("components/plugins"));
    Path archive=plugins.resolve("snapshot.jar");
    try(var out=new JarOutputStream(Files.newOutputStream(archive))){
      for(String suffix:List.of("SnapshotProvider","SnapshotProvider$SnapshotCapabilities")){
        String name="org/integratedmodelling/klab/authority/snapshot/"+suffix+".class";out.putNextEntry(new JarEntry(name));
        try(var input=getClass().getClassLoader().getResourceAsStream(name)){assertNotNull(input);input.transferTo(out);}out.closeEntry();
      }
    }
    var descriptor=new Extensions.AuthorityDescriptor("test.snapshot",Version.create("1.0.0"),true,true,List.of(),List.of());
    var component=new Extensions.ComponentDescriptor("fixture.bundle",Version.create("1.0.0-SNAPSHOT"),"fixture",archive.toFile(),
        HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(Files.readAllBytes(archive))),null,ResourcePrivileges.PUBLIC,
        List.of(),List.of(),List.of(descriptor),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),"source",1,null,null,0);
    Files.write(data.resolve("components/catalog.json"),json.writeValueAsBytes(List.of(component)));
    var request=new Authority.ConfigurationRequest("w","UN.M49","w:Identity",Map.of("urn","test.snapshot","semanticBoundaries",Map.of("COUNTRY","w:Country")));
    var snapshot=new ConfiguredAuthoritySnapshot("fixture-v1","test.snapshot",request,"frozen-vocabulary".getBytes(StandardCharsets.UTF_8));
    Path folder=Files.createDirectories(data.resolve("captured-authority-state/configured-snapshots"));
    Path state=folder.resolve(digest("UN.M49".getBytes(StandardCharsets.UTF_8))+".json");Files.write(state,json.writeValueAsBytes(snapshot));
    var root=new KimConceptStatementImpl();root.setUrn("Identity");root.setNamespace("w");root.setAuthorityRequired("UN.M49");root.setAuthorityParameters(request.parameters());
    var ontology=new KimOntologyImpl();ontology.setUrn("w");ontology.getStatements().add(root);
    var source=new WorldviewImpl();source.setUrn("w");source.setWorldviewId("w");source.setEmpty(false);source.getOntologies().add(ontology);
    var binding=new Worldview.AuthorityBinding("UN.M49","w:Identity","w",0,1,"hash",descriptor,component.id(),component.version(),null);source.getAuthorityBindings().add(binding);
    return new Fixture(data,archive,state,source,binding,request,Map.of("UN.M49",digest(Files.readAllBytes(state))));
  }
  private CapturedAuthorityCatalog capture(Fixture f)throws Exception{
    return CapturedAuthorityCatalog.captureRecorded(f.data,f.source,Map.of("UN.M49",0L),CapturedAuthorityCatalog.captureProviderEvidence(f.data),f.hashes);
  }
  @Test void deterministicConfiguredSnapshotCertifiesContentAndOpaqueStateBytesRemainSignificant() throws Exception {
    var f=fixture(); var catalog=capture(f);
    assertTrue(catalog.certifiesDurableContent(f.source));
    var proof=catalog.dependencyProof(f.binding);
    assertEquals(org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness.Kind.SNAPSHOT,proof.kind());
    assertTrue(proof.proof().matches("[a-f0-9]{64}"));assertEquals(Long.MAX_VALUE,proof.expiresAt());

    var original=SemanticContentFingerprint.canonical(catalog.semanticContent(f.source));
    assertArrayEquals(original,SemanticContentFingerprint.canonical(capture(f).semanticContent(f.source)));
    var changed=new ConfiguredAuthoritySnapshot("fixture-v1","test.snapshot",f.request,
        "frozen-vocabulary-with-different-content".getBytes(StandardCharsets.UTF_8));
    Files.write(f.state,json.writeValueAsBytes(changed));
    var next=new Fixture(f.data,f.archive,f.state,f.source,f.binding,f.request,
        Map.of("UN.M49",digest(Files.readAllBytes(f.state))));
    assertFalse(Arrays.equals(original,SemanticContentFingerprint.canonical(capture(next).semanticContent(f.source))));
    assertArrayEquals("frozen-vocabulary-with-different-content".getBytes(StandardCharsets.UTF_8),changed.state());
  }
  @Test void zeroTtlConfiguredStateReplaysFromOwnedArchiveWithoutLiveConfigure()throws Exception{
    var f=fixture();var catalog=capture(f);assertTrue(catalog.supports(f.binding));Files.delete(f.archive);
    var interactive=new java.util.concurrent.atomic.AtomicBoolean();
    var provider=catalog.provider(f.binding,f.request,directory.resolve("owned"),interactive::get);
    assertEquals("test.snapshot",provider.getUrn());
    String id=provider.configure(AuthorityBindings.providerRequest(f.request));
    assertEquals(0,provider.getCachePolicy().identitySeconds());
    assertEquals(Authority.HierarchyStatus.DEFERRED,provider.resolveIdentity(id,"Spain").getHierarchyStatus());
    assertFalse(provider.resolveIdentity(id,"Europe").getSemanticBoundaries().contains("COUNTRY"));
    assertEquals(List.of("Europe"),provider.resolveIdentity(id,"Spain",Authority.ResolutionMode.FULL_HIERARCHY).getParentIds());
    interactive.set(true);assertEquals("Spain",provider.search("Spain",null,id).getFirst().getLabel());
    assertEquals("Spain",provider.reconcile(id,Map.of("name","Spain")).getId());
    assertFalse(Files.exists(directory.resolve("owned/interactive-authority-cache")));
    provider.releaseConfiguration(id);assertThrows(IllegalStateException.class,()->provider.resolveIdentity(id,"Spain"));
  }
  @Test void configuredSnapshotDistanceUsesFrozenStateBeforeDuringAndAfterInteractiveUse()throws Exception{
    var f=fixture();var catalog=capture(f);Files.delete(f.archive);
    var interactive=new java.util.concurrent.atomic.AtomicBoolean();
    var provider=catalog.provider(f.binding,f.request,directory.resolve("distance-owned"),interactive::get);
    assertTrue(provider.getCapabilities().isSemanticDistanceSupported());
    String id=provider.configure(AuthorityBindings.providerRequest(f.request));
    assertEquals(0,provider.getCachePolicy().identitySeconds());
    assertEquals("Spain",provider.resolveIdentity(id,"Spain").getId());
    assertEquals(Authority.DistanceStatus.COMPATIBLE,provider.semanticDistance(id,"Europe","Spain").status());
    assertEquals(2,provider.semanticDistance(id,"Europe","Spain").distance());
    interactive.set(true);
    var distance=provider.semanticDistance(id,"Europe","Spain");
    assertEquals(Authority.DistanceStatus.COMPATIBLE,distance.status());assertEquals(2,distance.distance());
    interactive.set(false);
    assertEquals(Authority.DistanceStatus.COMPATIBLE,provider.semanticDistance(id,"Europe","Spain").status());
    assertEquals(2,provider.semanticDistance(id,"Europe","Spain").distance());
    assertFalse(Files.exists(directory.resolve("distance-owned/interactive-authority-cache")));
    provider.releaseConfiguration(id);
    assertThrows(IllegalStateException.class,()->provider.semanticDistance(id,"Europe","Spain"));
  }
  @Test void configuredStateRequiresSuccessfulExactProducerProof()throws Exception{
    var f=fixture();
    assertThrows(UnsupportedOperationException.class,()->CapturedAuthorityCatalog.captureRecorded(f.data,f.source,Map.of("UN.M49",0L),CapturedAuthorityCatalog.captureProviderEvidence(f.data)));
    Files.writeString(f.state,"{}");assertThrows(Exception.class,()->capture(f));
  }
  @Test void configuredCodelistsReplayWithoutConfigureOrInteractiveFallbackAndObeyRelease()throws Exception{
    var f=fixture();var catalog=capture(f);Files.delete(f.archive);
    var provider=catalog.provider(f.binding,f.request,directory.resolve("codelist-offline"),()->false);
    var id=provider.configure(AuthorityBindings.providerRequest(f.request));
    var definitions=provider.getCodelistDefinitions(id);
    assertEquals(1,definitions.get("countries").codelist().size());
    assertEquals("UN.M49",definitions.get("countries").codelist().getAuthorityId());
    assertFalse(definitions.get("countries").acceptsProposals());
    assertEquals(definitions.keySet(),provider.getCodelists(id).keySet());
    assertThrows(IllegalStateException.class,()->provider.getCodelistDefinitions("wrong"));
    provider.releaseConfiguration(id);
    assertThrows(IllegalStateException.class,()->provider.getCodelistDefinitions(id));
    assertFalse(Files.exists(directory.resolve("codelist-offline/interactive-authority-cache")));
  }
  @Test void missingConfiguredSnapshotCannotCertifyAnUnusedBridge()throws Exception{
    var f=fixture();Files.delete(f.state);
    assertThrows(UnsupportedOperationException.class,()->CapturedAuthorityCatalog.captureRecorded(f.data,f.source,Map.of("UN.M49",0L),CapturedAuthorityCatalog.captureProviderEvidence(f.data),Map.of()));
  }
  @Test void changedConfigurationAndArtifactRemainRejected()throws Exception{
    var f=fixture();var catalog=capture(f);
    var changed=new Authority.ConfigurationRequest("w","UN.M49","w:Identity",Map.of("urn","test.snapshot","catalog","different"));
    assertThrows(UnsupportedOperationException.class,()->catalog.provider(f.binding,changed,directory.resolve("bad")));
    Files.writeString(f.archive,"changed");assertThrows(UnsupportedOperationException.class,()->capture(f));
  }

  @Test void failedSnapshotRestoreRetainsTheAcceptedProviderAndGeneration()throws Exception{
    var fixture=fixture();var current=new java.util.concurrent.atomic.AtomicReference<>(fixture);
    record State(Authority provider,String id) implements AutoCloseable {
      public void close(){provider.releaseConfiguration(id);}
    }
    try(var coordinator=new CandidatePublicationCoordinator<String,State>((input,cancellation)->{
      var f=current.get();var catalog=capture(f);
      var provider=catalog.provider(f.binding,f.request,directory.resolve(input.payload()));
      var id=provider.configure(AuthorityBindings.providerRequest(f.request));
      return new CandidatePublicationCoordinator.Result<>(new State(provider,id),CandidatePublicationCoordinator.LogicalStatus.VALID,true,List.of());
    }, input->true)){
      coordinator.submit(new CandidatePublicationCoordinator.Input<>(Map.of("worldview","good"),List.of("worldview"),"dependencies","configuration","good",CandidatePublicationCoordinator.BeanStatus.VALID,List.of()));
      assertTrue(coordinator.awaitIdle(java.time.Duration.ofSeconds(10)));
      var bad=new ConfiguredAuthoritySnapshot("fixture-v1","test.snapshot",fixture.request,"invalid-state".getBytes(StandardCharsets.UTF_8));
      Files.write(fixture.state,json.writeValueAsBytes(bad));
      current.set(new Fixture(fixture.data,fixture.archive,fixture.state,fixture.source,fixture.binding,fixture.request,Map.of("UN.M49",digest(Files.readAllBytes(fixture.state)))));
      coordinator.submit(new CandidatePublicationCoordinator.Input<>(Map.of("worldview","bad"),List.of("worldview"),"dependencies","configuration","bad",CandidatePublicationCoordinator.BeanStatus.VALID,List.of()));
      assertTrue(coordinator.awaitIdle(java.time.Duration.ofSeconds(10)));
      assertEquals(CandidatePublicationCoordinator.LogicalStatus.FAILED,coordinator.status().logicalStatus());
      assertEquals(1,coordinator.status().acceptedGeneration());assertEquals(Map.of("worldview","good"),coordinator.status().acceptedSources());
      try(var lease=coordinator.accepted().orElseThrow()){
        assertEquals("Spain",lease.state().provider.resolveIdentity(lease.state().id,"Spain").getId());
      }
    }
  }
}
