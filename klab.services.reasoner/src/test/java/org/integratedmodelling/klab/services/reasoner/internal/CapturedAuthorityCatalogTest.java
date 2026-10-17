package org.integratedmodelling.klab.services.reasoner.internal;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.jar.*;
import com.fasterxml.jackson.databind.*;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.services.Authority;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
class CapturedAuthorityCatalogTest {
  @Test void discoveryDescriptorsAreDetachedFromCapturedAuthorityState() throws Exception {
    var catalog = captured(fixture());
    var descriptor = catalog.components().getFirst();
    assertEquals("klab.authority.taxa", descriptor.id());
    var hash = descriptor.fileHash();
    descriptor.authorities().clear();
    assertFalse(catalog.components().getFirst().authorities().isEmpty());
    assertEquals(hash, catalog.components().getFirst().fileHash());
  }
  @TempDir Path directory;
  private final ObjectMapper json = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
  private Authority.ConfigurationRequest request() { return new Authority.ConfigurationRequest("w","TAXA","w:Identity",Map.of("urn","klab.authority.taxa")); }
  private String segment(String prefix,String value) { return prefix+"_"+value+"_"+CachedAuthority.digest(value).substring(0,16); }
  private record Fixture(Worldview.AuthorityBinding binding, Path data, byte[] artifact, Path responses) {}
  private Fixture fixture() throws Exception {
    var data=Files.createDirectories(directory.resolve("source"));var plugins=Files.createDirectories(data.resolve("components/plugins"));
    var jar=plugins.resolve("klab.authority.taxa-1.0.0-SNAPSHOT-component.jar");
    try(var output=new JarOutputStream(Files.newOutputStream(jar))) {
      for(var name:List.of("TaxaAuthority","TaxaAuthority$FixtureCapabilities")) {
        String resource="org/integratedmodelling/klab/authority/taxa/"+name+".class";
        output.putNextEntry(new JarEntry(resource));try(var input=getClass().getClassLoader().getResourceAsStream(resource)){assertNotNull(input);input.transferTo(output);}output.closeEntry();
      }
    }
    byte[] artifact=Files.readAllBytes(jar);
    var descriptor=json.createObjectNode();descriptor.put("id","klab.authority.taxa");
    descriptor.putObject("version").put("major",1).put("minor",0).put("build",0).put("modifier","SNAPSHOT").putArray("constraints");
    descriptor.put("sourceArchive",jar.toString());descriptor.put("fileHash",HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(artifact)));
    descriptor.putObject("usageRights").put("public",true);
    for(var name:List.of("libraries","adapters","exporters","importers")) descriptor.putArray(name);
    for(var name:List.of("services","annotations","actors")) descriptor.putObject(name);
    var provider=descriptor.putArray("authorities").addObject();provider.put("urn","klab.authority.taxa");
    provider.putObject("version").put("major",1).put("minor",0).put("build",0).putNull("modifier").putArray("constraints");
    provider.put("embeddable",true).put("searchable",true);provider.putArray("subAuthorities").add("SPECIES");provider.putArray("codelists");
    Files.write(data.resolve("components/catalog.json"),json.writeValueAsBytes(List.of(descriptor)));
    var mapper=JacksonConfiguration.newObjectMapper();
    var component=mapper.treeToValue(descriptor,org.integratedmodelling.klab.api.services.runtime.extension.Extensions.ComponentDescriptor.class);
    var binding=new Worldview.AuthorityBinding("TAXA","w:Identity","w",0,10,"hash",component.authorities().getFirst(),component.id(),component.version(),null);
    var policy=new Authority.CachePolicy("fixture-1",Long.MAX_VALUE,86400,86400);
    String implementation="org.integratedmodelling.klab.authority.taxa.TaxaAuthority:1.0.0:"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(artifact));
    String fingerprint=CachedAuthority.digest(json.writeValueAsString(List.of("authority-cache-v2","w","TAXA","w:Identity",request().parameters(),"klab.authority.taxa",implementation,policy)));
    var responses=Files.createDirectories(data.resolve("authority-cache").resolve(segment("wv","w")).resolve(segment("configuration","TAXA")).resolve(fingerprint));
    return new Fixture(binding,data,artifact,responses);
  }
  private CapturedAuthorityCatalog captured(Fixture fixture) throws Exception {
    var statement=new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptStatementImpl();
    statement.setUrn("Identity");statement.setNamespace("w");statement.setAuthorityRequired("TAXA");statement.setAuthorityParameters(request().parameters());
    var ontology=new org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl();ontology.setUrn("w");ontology.getStatements().add(statement);
    var source=new org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl();source.setUrn("w");source.setWorldviewId("w");source.setEmpty(false);
    source.setOntologies(new ArrayList<>(List.of(ontology)));source.setAuthorityBindings(new ArrayList<>(List.of(fixture.binding())));
    return CapturedAuthorityCatalog.captureRecorded(fixture.data(),source,Map.of("TAXA",0L),CapturedAuthorityCatalog.captureProviderEvidence(fixture.data()));
  }
  private void response(Fixture fixture, String view, String id, long expires) throws Exception {
    var envelope=json.createObjectNode();envelope.put("expires",expires);var identity=envelope.putObject("value");
    identity.put("id",id).put("conceptName","taxon_"+id).put("authorityName","TAXA").put("label",id).put("description",id).put("score",1).put("locator","TAXA:"+id);
    identity.putArray("parentIds");identity.putArray("parentRelationship");identity.putObject("documentation");identity.putArray("semanticBoundaries").add("SPECIES");identity.put("hierarchyStatus","COMPLETE");
    var file=fixture.responses().resolve(CachedAuthority.digest(json.writeValueAsString(List.of("identity",view,id)))+".json");Files.write(file,json.writeValueAsBytes(envelope));
  }
  @Test void frozenExactResponsesAndSubauthorityViewsShareBridgeLifetime() throws Exception {
    var fixture=fixture();response(fixture,"SPECIES","1",Long.MAX_VALUE);response(fixture,"","1",Long.MAX_VALUE);
    var catalog=captured(fixture);assertTrue(catalog.supports(fixture.binding()));
    var provider=catalog.provider(fixture.binding(),request(),directory.resolve("owned"));var id=provider.configure(AuthorityBindings.providerRequest(request()));
    var view=provider.subAuthority("SPECIES");assertNotNull(view);assertEquals("1",view.resolveIdentity(id,"1").getId());
    Files.writeString(fixture.responses().resolve(CachedAuthority.digest(json.writeValueAsString(List.of("identity","SPECIES","1")))+".json"),"corrupted after capture");
    assertEquals("1",view.resolveIdentity(id,"1").getId());
    assertThrows(UnsupportedOperationException.class,()->provider.resolveIdentity(id,"unknown"));
    provider.releaseConfiguration(id);assertThrows(IllegalStateException.class,()->view.resolveIdentity(id,"1"));
  }
  @Test void changedParametersExpiredResponsesAndChangedArtifactDoNotFallBack() throws Exception {
    var fixture=fixture();response(fixture,"","1",0);var catalog=captured(fixture);
    var provider=catalog.provider(fixture.binding(),request(),directory.resolve("owned"));
    var changed=new Authority.ConfigurationRequest("w","TAXA","w:Identity",Map.of("urn","klab.authority.taxa","datasetKey",99));
    assertThrows(UnsupportedOperationException.class,()->provider.configure(changed));
    assertThrows(UnsupportedOperationException.class,()->catalog.provider(fixture.binding(),changed,directory.resolve("other")));
    var id=provider.configure(AuthorityBindings.providerRequest(request()));assertThrows(UnsupportedOperationException.class,()->provider.resolveIdentity(id,"1"));
    Files.writeString(fixture.data().resolve("components/plugins/klab.authority.taxa-1.0.0-SNAPSHOT-component.jar"),"changed");
    assertThrows(UnsupportedOperationException.class,()->CapturedAuthorityCatalog.capture(fixture.data()));
  }
  @Test void producerProofCannotCertifyAChangedApprovedLedger() throws Exception {
    var fixture=fixture();
    String key=request().worldview()+"\0"+request().name()+"\0"+request().rootIdentity()+"\0"+request().parameters().get("urn");
    var ledger=Files.createDirectories(fixture.data().resolve("authority-codelists")).resolve(CachedAuthority.digest(key)+".json");
    Files.write(ledger,json.writeValueAsBytes(new AuthorityCodelists.State(3,List.of())));
    assertThrows(UnsupportedOperationException.class,()->captured(fixture));
    Files.write(ledger,json.writeValueAsBytes(new AuthorityCodelists.State(0,List.of())));
    assertTrue(captured(fixture).supports(fixture.binding()));
  }
  @Test void provenanceIgnoresOnlyJsonObjectMemberOrderAndPreservesArrayOrder() {
    byte[] first="{\"b\":{\"x\":1,\"y\":2},\"a\":[1,2]}".getBytes();
    byte[] reordered="{\"a\":[1,2],\"b\":{\"y\":2,\"x\":1}}".getBytes();
    byte[] changedArray="{\"a\":[2,1],\"b\":{\"y\":2,\"x\":1}}".getBytes();
    assertEquals(CapturedAuthorityCatalog.canonicalMetadataDigest(first),CapturedAuthorityCatalog.canonicalMetadataDigest(reordered));
    assertNotEquals(CapturedAuthorityCatalog.canonicalMetadataDigest(first),CapturedAuthorityCatalog.canonicalMetadataDigest(changedArray));
  }
  @Test void immutableProofSurvivesInstallationBookkeepingButRejectsChangedRightsAndCode() throws Exception {
    var fixture=fixture();var evidence=CapturedAuthorityCatalog.captureProviderEvidence(fixture.data());
    var frozen=Files.createDirectories(fixture.data().resolve("captured-authority-state/provider-components/plugins"));
    Files.copy(fixture.data().resolve("components/catalog.json"),frozen.getParent().resolve("catalog.json"));
    Files.copy(fixture.data().resolve("components/plugins/klab.authority.taxa-1.0.0-SNAPSHOT-component.jar"),frozen.resolve("klab.authority.taxa-1.0.0-SNAPSHOT-component.jar"));
    var file=fixture.data().resolve("components/catalog.json");var entries=json.readTree(Files.readAllBytes(file));
    var descriptor=(com.fasterxml.jackson.databind.node.ObjectNode)entries.get(0);
    descriptor.put("sourceArchive",directory.resolve("relocated/components/plugins/klab.authority.taxa-1.0.0-SNAPSHOT-component.jar").toString());
    descriptor.put("timestamp",12345).put("latestVersionTimestamp",12345).put("sourceServiceId","relocated-deployment");
    Files.write(file,json.writeValueAsBytes(entries));
    assertEquals(evidence,CapturedAuthorityCatalog.captureProviderEvidence(fixture.data()));
    assertTrue(captured(fixture).supports(fixture.binding()));
    ((com.fasterxml.jackson.databind.node.ObjectNode)descriptor.get("usageRights")).put("public",false);Files.write(file,json.writeValueAsBytes(entries));
    assertThrows(UnsupportedOperationException.class,()->CapturedAuthorityCatalog.captureProviderEvidence(fixture.data()));
    ((com.fasterxml.jackson.databind.node.ObjectNode)descriptor.get("usageRights")).put("public",true);Files.write(file,json.writeValueAsBytes(entries));
    Files.writeString(fixture.data().resolve("components/plugins/klab.authority.taxa-1.0.0-SNAPSHOT-component.jar"),"replacement code");
    assertThrows(UnsupportedOperationException.class,()->CapturedAuthorityCatalog.captureProviderEvidence(fixture.data()));
  }
  @Test void privateComponentPermissionsRequireAnExplicitAuthorizedCapture() throws Exception {
    var fixture=fixture();var file=fixture.data().resolve("components/catalog.json");var descriptors=json.readTree(Files.readAllBytes(file));
    ((com.fasterxml.jackson.databind.node.ObjectNode)descriptors.get(0).get("usageRights")).put("public",false);Files.write(file,json.writeValueAsBytes(descriptors));
    assertThrows(UnsupportedOperationException.class,()->CapturedAuthorityCatalog.capture(fixture.data()));
  }
  @Test void productionPrivateActorConfiguresAndMaterializesOnlyFrozenProviderFacts() throws Exception {
    var fixture=fixture();response(fixture,"","1",Long.MAX_VALUE);var catalog=captured(fixture);
    var statement=new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptStatementImpl();
    statement.setUrn("Identity");statement.setNamespace("w");statement.setOffsetInDocument(0);statement.setLength(10);
    statement.setType(EnumSet.of(org.integratedmodelling.klab.api.knowledge.SemanticType.IDENTITY,org.integratedmodelling.klab.api.knowledge.SemanticType.PREDICATE));
    var parent=new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl();parent.setName("w:RootIdentity");parent.setUrn("w:RootIdentity");
    statement.setDeclaredParent(parent);statement.setAuthorityRequired("TAXA");statement.setAuthorityParameters(request().parameters());
    var ontology=new org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl();ontology.setUrn("w");ontology.setProjectName("fixture");ontology.setSourceCode(" ".repeat(200));var rootIdentity=new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptStatementImpl();
    rootIdentity.setUrn("RootIdentity");rootIdentity.setNamespace("w");rootIdentity.setOffsetInDocument(11);
    rootIdentity.setType(EnumSet.of(org.integratedmodelling.klab.api.knowledge.SemanticType.IDENTITY,org.integratedmodelling.klab.api.knowledge.SemanticType.PREDICATE));
    rootIdentity.setUpperConceptDefined("odo:Identity");ontology.getStatements().add(rootIdentity);ontology.getStatements().add(statement);
    var binding=fixture.binding();binding=new Worldview.AuthorityBinding(binding.localId(),binding.rootIdentity(),"w",0,10,
        org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest.sourceHash(ontology.getSourceCode()),binding.provider(),binding.componentUrn(),binding.componentVersion(),null);
    var worldview=new org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl();worldview.setUrn("w");worldview.setWorldviewId("w");worldview.setEmpty(false);
    worldview.setOntologies(new ArrayList<>(List.of(ontology)));worldview.setAuthorityBindings(new ArrayList<>(List.of(binding)));
    byte[] core;try(var input=getClass().getClassLoader().getResourceAsStream("knowledge/odo.owl")){assertNotNull(input);core=input.readAllBytes();}
    var factory=new SemanticEnvironmentFactory(directory.resolve("actors"),Map.of("odo.owl",core),Map.of(),Map.of(),catalog);
    var prepared=factory.capture(worldview);assertEquals(CandidatePublicationCoordinator.BeanStatus.VALID,prepared.beanStatus(),prepared.diagnostics().toString());
    var result=factory.build(prepared,()->false);
    try(var state=result.state()) {
      assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,result.logicalStatus(),result.diagnostics().toString());
      assertFalse(state.actor().resolveConcept("TAXA:1").is(org.integratedmodelling.klab.api.knowledge.SemanticType.NOTHING));
      assertThrows(UnsupportedOperationException.class,()->state.actor().resolveConcept("TAXA:unknown"));
    }
  }

  @Test void configuredSourceProofIsRequiredAndPinnedBeforeDefaultCatalogCanServeBindings() throws Exception {
    var fixture=fixture();assertFalse(CapturedAuthorityCatalog.capture(fixture.data()).supports(fixture.binding()));
    var statement=new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptStatementImpl();
    statement.setUrn("Identity");statement.setNamespace("w");statement.setAuthorityRequired("TAXA");statement.setAuthorityParameters(request().parameters());
    var ontology=new org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl();ontology.setUrn("w");ontology.getStatements().add(statement);
    var source=new org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl();source.setUrn("w");source.setWorldviewId("w");source.setEmpty(false);
    source.setOntologies(new ArrayList<>(List.of(ontology)));source.setAuthorityBindings(new ArrayList<>(List.of(fixture.binding())));
    byte[] contents=JacksonConfiguration.newObjectMapper().writeValueAsBytes(source);
    var state=Files.createDirectories(fixture.data().resolve("captured-authority-state"));Files.write(state.resolve("source.json"),contents);
    var proof=json.createObjectNode();proof.put("inputSha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contents)));proof.putObject("authorityVersions").put("TAXA",0);
    proof.set("providerEvidence",json.valueToTree(CapturedAuthorityCatalog.captureProviderEvidence(fixture.data())));
    Files.write(state.resolve("proof.json"),json.writeValueAsBytes(proof));
    assertTrue(CapturedAuthorityCatalog.capture(fixture.data()).supports(fixture.binding()));
    var archive=fixture.data().resolve("components/plugins/klab.authority.taxa-1.0.0-SNAPSHOT-component.jar");
    var entries=new LinkedHashMap<String,byte[]>();
    try(var input=new JarInputStream(Files.newInputStream(archive))) {
      JarEntry entry;while((entry=input.getNextJarEntry())!=null)entries.put(entry.getName(),input.readAllBytes());
    }
    entries.put("replacement.txt","same URN/version, different implementation artifact".getBytes());
    try(var output=new JarOutputStream(Files.newOutputStream(archive))) {
      for(var entry:entries.entrySet()){output.putNextEntry(new JarEntry(entry.getKey()));output.write(entry.getValue());output.closeEntry();}
    }
    var descriptorFile=fixture.data().resolve("components/catalog.json");var descriptors=json.readTree(Files.readAllBytes(descriptorFile));
    ((com.fasterxml.jackson.databind.node.ObjectNode)descriptors.get(0)).put("fileHash",HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(Files.readAllBytes(archive))));
    Files.write(descriptorFile,json.writeValueAsBytes(descriptors));
    // Both archive and its descriptor agree, but the old successful configuration did not use them.
    assertThrows(UnsupportedOperationException.class,()->CapturedAuthorityCatalog.capture(fixture.data()));
    Files.writeString(state.resolve("source.json"),"modified producer input");
    assertThrows(UnsupportedOperationException.class,()->CapturedAuthorityCatalog.capture(fixture.data()));
  }

}
