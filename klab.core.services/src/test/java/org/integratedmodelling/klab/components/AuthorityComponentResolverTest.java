package org.integratedmodelling.klab.components;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.File;
import java.net.URI;
import java.util.*;
import org.integratedmodelling.common.services.ResourcesCapabilitiesImpl;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.identities.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.junit.jupiter.api.Test;

class AuthorityComponentResolverTest {
  @Test void serviceCertificateSelectsPublicProvidersWithoutInventingUserGrants() throws Exception {
    when(scope.getIdentity()).thenReturn(mock(PartnerIdentity.class));
    var rights=publicRights();
    var remote=source("remote",component("hash",rights,"1.0.0")); services(remote);
    assertEquals(1,AuthorityComponentResolver.resolve("fixture.authority@1.0.0",scope).components().size());
    rights.setPublic(false); rights.setAllowedUsers(Set.of("owner"));
    assertTrue(AuthorityComponentResolver.resolve("fixture.authority@1.0.0",scope).components().isEmpty());
  }

  @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
  private final UserIdentity owner = mock(UserIdentity.class);
  private final ServiceScope scope = mock(ServiceScope.class);
  AuthorityComponentResolverTest() {
    when(owner.getUsername()).thenReturn("owner"); when(owner.getId()).thenReturn("test-owner-context");
    when(owner.getGroups()).thenReturn(List.of()); when(scope.getIdentity()).thenReturn(owner);
  }
  private Extensions.ComponentDescriptor component(String hash, ResourcePrivileges rights, String providerVersion) {
    return component(hash, rights, providerVersion, "2.0.0");
  }
  private Extensions.ComponentDescriptor component(String hash, ResourcePrivileges rights, String providerVersion, String componentVersion) {
    return new Extensions.ComponentDescriptor("fixture.component", Version.create(componentVersion), "fixture",
        new File("fixture.jar"), hash, "fixture:component:"+componentVersion, rights, List.of(), List.of(),
        List.of(new Extensions.AuthorityDescriptor("fixture.authority",Version.create(providerVersion),true,true,List.of(),List.of())),
        Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),null,1,null,null,0);
  }
  private ResourcePrivileges publicRights() { var rights = new ResourcePrivileges(); rights.setPublic(true); return rights; }
  private Extensions.ComponentDescriptor withAdapter(Extensions.ComponentDescriptor component) {
    var adapter = new org.integratedmodelling.klab.api.services.runtime.extension.AdapterDescriptor();
    adapter.setName("geospatial.fixture");adapter.setVersion(Version.create("1.0.0"));
    adapter.getMetadata().put("runtime.details",Map.of("nested",List.of("unused by authority admission")));
    var wire=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper().valueToTree(adapter);
    assertEquals(adapter.getClass().getName(),wire.path("@CLASS").asText()); // Matches the live nested capability shape.
    return new Extensions.ComponentDescriptor(component.id(),component.version(),component.description(),component.sourceArchive().getAbsoluteFile(),
        component.fileHash(),component.mavenCoordinates(),component.usageRights(),component.libraries(),List.of(adapter),component.authorities(),
        component.services(),component.annotations(),component.actors(),component.exporters(),component.importers(),"origin-service",42,
        component.importType(),component.updateStatus(),123);
  }

  @Test void installedMixedAdapterMetadataCannotBreakOwnedAuthorityAdmission() {
    var original=withAdapter(component("hash",publicRights(),"1.0.0"));
    var result=AuthorityComponentResolver.forWorldview(worldview(),List.of(original),scope);
    var owned=result.components().getFirst();
    assertEquals(original.id(),owned.id());assertEquals(original.fileHash(),owned.fileHash());
    assertEquals(original.mavenCoordinates(),owned.mavenCoordinates());assertEquals(original.sourceArchive(),owned.sourceArchive());
    assertEquals("origin-service",owned.sourceServiceId());assertEquals(42,owned.timestamp());assertEquals(123,owned.latestVersionTimestamp());
    assertEquals(original.importType(),owned.importType());assertEquals(original.updateStatus(),owned.updateStatus());
    assertEquals(original.authorities(),owned.authorities());assertFalse(owned.usageRights()==original.usageRights());
    assertTrue(owned.adapters().isEmpty());assertTrue(owned.libraries().isEmpty());assertTrue(owned.services().isEmpty());
    assertEquals(1,original.adapters().size());
    original.version().setMajor(9);original.authorities().getFirst().version().setMajor(8);original.usageRights().setExcludedUsers(Set.of("owner"));
    assertEquals(Version.create("2.0.0"),owned.version());assertEquals(Version.create("1.0.0"),owned.authorities().getFirst().version());
    assertTrue(owned.usageRights().getExcludedUsers().isEmpty());
    verify(scope,never()).getServices(ResourcesService.class);
  }

  @Test void remoteMixedAdapterMetadataPreservesExactAuthorizedProvenance() throws Exception {
    var rights=new ResourcePrivileges();rights.setAllowedUsers(Set.of("owner"));
    var original=withAdapter(component("hash",rights,"1.0.0"));var remote=source("remote",original);services(remote);
    var result=AuthorityComponentResolver.resolve("fixture.authority@1.0.0",scope);
    assertEquals(1,result.components().size());assertTrue(result.components().getFirst().adapters().isEmpty());
    assertFalse(result.components().getFirst().usageRights().isPublic());
    var resource=result.resources().getResults().iterator().next();
    assertEquals("fixture.component@2.0.0",resource.getResourceUrn());assertEquals("remote",resource.getServiceId());
    assertEquals("fixture:component:2.0.0",resource.getMetadata().get(Extensions.COMPONENT_MAVEN_COORDINATES_METADATA_KEY));
    assertEquals(remote.getUrl(),result.resources().getServices().get("remote"));
    rights.setExcludedUsers(Set.of("owner"));
    assertTrue(AuthorityComponentResolver.resolve("fixture.authority@1.0.0",scope).components().isEmpty());
    verify(remote,never()).exportAsset(anyString(),any(),anyString(),any(),any());
  }
  private ResourcesService source(String id, Extensions.ComponentDescriptor descriptor) throws Exception {
    var service = mock(ResourcesService.class); when(service.serviceId()).thenReturn(id);
    when(service.getUrl()).thenReturn(URI.create("https://"+id+".invalid/resources").toURL());
    var status = mock(KlabService.ServiceStatus.class); when(status.isOperational()).thenReturn(true);when(service.status()).thenReturn(status);
    var caps = new ResourcesCapabilitiesImpl();caps.setWorldviewProvider(false);caps.setAdoptedWorldview("unrelated");
    caps.getComponents().add(descriptor);when(service.capabilities(any())).thenReturn(caps);
    var result = ResourceSet.of(new ResourceSet.Resource(id,descriptor.id(),null,descriptor.version(),KlabAsset.KnowledgeClass.COMPONENT,1,false));
    result.getServices().put(id,service.getUrl());when(service.resolve(anyString(),eq(KlabAsset.KnowledgeClass.COMPONENT),any())).thenReturn(result);
    return service;
  }
  private void services(ResourcesService... services) {when(scope.getServices(ResourcesService.class)).thenReturn(List.of(services));}
  private WorldviewImpl worldview() {
    var declaration=new KimConceptStatementImpl();declaration.setUrn("Identity");declaration.setNamespace("w");
    declaration.setAuthorityRequired("TEST");declaration.setAuthorityParameters(new HashMap<>(Map.of("urn","fixture.authority@1.0.0")));
    var ontology=new KimOntologyImpl();ontology.setUrn("w");ontology.getStatements().add(declaration);
    var world=new WorldviewImpl();world.setUrn("w");world.setOntologies(new ArrayList<>(List.of(ontology)));return world;
  }

  @Test void generalResourcesUsesComponentResolutionWithoutWorldviewHostingOrInstallation() throws Exception {
    var source=source("remote",component("hash-a",publicRights(),"1.0.0"));services(source);
    var result=AuthorityComponentResolver.resolve("fixture.authority@1.0.0",scope);
    assertEquals(1,result.components().size());assertEquals("fixture.component@2.0.0",result.resources().getResults().iterator().next().getResourceUrn());
    assertEquals("fixture:component:2.0.0",result.resources().getResults().iterator().next().getMetadata().get(Extensions.COMPONENT_MAVEN_COORDINATES_METADATA_KEY));
    verify(source).resolve("fixture.authority",KlabAsset.KnowledgeClass.COMPONENT,null);
    verify(source).capabilities(scope);
    verifyNoMoreInteractions(ignoreStubs(source)); // No export, authority invocation, configuration, registry load, or broad query.
  }

  @Test void absentAndAnonymousOwnerNeverQueryPeers() throws Exception {
    var remote=source("remote",component("hash-a",publicRights(),"1.0.0"));services(remote);
    when(scope.getIdentity()).thenReturn(null);assertTrue(AuthorityComponentResolver.resolve("fixture.authority",scope).components().isEmpty());
    when(scope.getIdentity()).thenReturn(owner);when(owner.isAnonymous()).thenReturn(true);
    assertTrue(AuthorityComponentResolver.resolve("fixture.authority",scope).components().isEmpty());
    verify(remote,never()).resolve(anyString(),any(),any());
  }

  @Test void publicUserExclusionCannotUseTrustedLocalScopeShortcut() throws Exception {
    var rights=publicRights();rights.setExcludedUsers(Set.of("owner"));assertTrue(rights.checkAuthorization(scope));
    services(source("remote",component("hash-a",rights,"1.0.0")));
    assertTrue(AuthorityComponentResolver.resolve("fixture.authority",scope).components().isEmpty());
  }

  @Test void explicitGroupExclusionOverridesPublicAndAllowedOwner() throws Exception {
    var group=mock(Group.class);when(group.getName()).thenReturn("BLOCKED");when(owner.getGroups()).thenReturn(List.of(group));
    var rights=publicRights();rights.setAllowedUsers(Set.of("owner"));rights.setExcludedGroups(Set.of("BLOCKED"));
    services(source("remote",component("hash-a",rights,"1.0.0")));
    assertTrue(AuthorityComponentResolver.resolve("fixture.authority",scope).components().isEmpty());
  }

  @Test void allowedOwnerCanReadPrivateDescriptorWithoutMakingItPublic() throws Exception {
    var rights=new ResourcePrivileges();rights.setAllowedUsers(Set.of("owner"));services(source("remote",component("hash-a",rights,"1.0.0")));
    var result=AuthorityComponentResolver.resolve("fixture.authority",scope);assertEquals(1,result.components().size());
    assertFalse(result.components().getFirst().usageRights().isPublic());
  }

  @Test void unknownRightsAndUnavailablePeerFailClosed() throws Exception {
    var remote=source("remote",component("hash-a",null,"1.0.0"));services(remote);
    assertTrue(AuthorityComponentResolver.resolve("fixture.authority",scope).components().isEmpty());
    when(remote.status().isOperational()).thenReturn(false);clearInvocations(remote);
    assertTrue(AuthorityComponentResolver.resolve("fixture.authority",scope).components().isEmpty());verify(remote,never()).resolve(anyString(),any(),any());
  }

  @Test void exactAuthorityAndComponentVersionsAreBothRequired() throws Exception {
    var remote=source("remote",component("hash-a",publicRights(),"1.1.0"));services(remote);
    assertTrue(AuthorityComponentResolver.resolve("fixture.authority@1.0.0",scope).components().isEmpty());
    var response=ResourceSet.of(new ResourceSet.Resource("remote","fixture.component",null,Version.create("9.0.0"),KlabAsset.KnowledgeClass.COMPONENT,1,false));response.getServices().put("remote",remote.getUrl());
    when(remote.resolve(anyString(),any(),any())).thenReturn(response);
    assertTrue(AuthorityComponentResolver.resolve("fixture.authority@1.1.0",scope).components().isEmpty());
  }

  @Test void forgedSourceIdOrUrlIsRejected() throws Exception {
    var remote=source("remote",component("hash-a",publicRights(),"1.0.0"));services(remote);
    var response=ResourceSet.of(new ResourceSet.Resource("other","fixture.component",null,Version.create("2.0.0"),KlabAsset.KnowledgeClass.COMPONENT,1,false));response.getServices().put("remote",remote.getUrl());
    when(remote.resolve(anyString(),any(),any())).thenReturn(response);assertTrue(AuthorityComponentResolver.resolve("fixture.authority",scope).components().isEmpty());
    response.getResults().clear();response.getResults().add(new ResourceSet.Resource("remote","fixture.component",null,Version.create("2.0.0"),KlabAsset.KnowledgeClass.COMPONENT,1,false));
    response.getServices().put("remote",URI.create("https://forged.invalid/resources").toURL());
    assertTrue(AuthorityComponentResolver.resolve("fixture.authority",scope).components().isEmpty());
  }

  @Test void localMatchAndLocalAmbiguityDoNotTriggerRemoteLookup() throws Exception {
    var remote=source("remote",component("hash-remote",publicRights(),"1.0.0"));services(remote);
    var a=component("hash-a",publicRights(),"1.0.0");var b=component("hash-b",publicRights(),"1.0.0");
    assertEquals(1,AuthorityComponentResolver.forWorldview(worldview(),List.of(a),scope).components().size());
    assertEquals(2,AuthorityComponentResolver.forWorldview(worldview(),List.of(a,b),scope).components().size());
    verify(remote,never()).resolve(anyString(),any(),any());
  }

  @Test void mergedResourceProvenanceSelectsTheMatchingDescriptor() throws Exception {
    var a=source("mirror-a",component("same-hash",publicRights(),"1.0.0"));var b=source("mirror-b",component("same-hash",publicRights(),"1.0.0"));services(a,b);
    assertEquals(1,AuthorityComponentResolver.resolve("fixture.authority",scope).components().size());
    services(a,source("different",component("different-hash",publicRights(),"1.0.0")));
    var selected=AuthorityComponentResolver.resolve("fixture.authority",scope);
    assertEquals(1,selected.components().size());
    assertEquals("mirror-a",selected.resources().getResults().iterator().next().getServiceId());
    assertEquals("same-hash",selected.components().getFirst().fileHash());
  }

  private ResourcesService localSource(Extensions.ComponentDescriptor descriptor) throws Exception {
    var local=source("local",descriptor);
    when(local.getUrl()).thenReturn(URI.create("http://127.0.0.1:8092/resources").toURL());
    local.resolve("fixture.authority",KlabAsset.KnowledgeClass.COMPONENT,null)
        .getServices().put("local",local.getUrl());
    return local;
  }

  @Test void lateLocalProviderReplacesRemoteAndUnselectedChangesStayQuiet() throws Exception {
    var remote=source("remote",component("remote-hash",publicRights(),"1.0.0")); services(remote);
    var first=AuthorityComponentResolver.resolve("fixture.authority@1.0.0",scope);
    var local=localSource(component("local-hash",publicRights(),"1.0.0")); services(remote,local);
    var second=AuthorityComponentResolver.resolve("fixture.authority@1.0.0",scope);
    assertEquals(1,second.components().size()); assertEquals("local-hash",second.components().getFirst().fileHash());
    var resource=second.resources().getResults().iterator().next();
    assertTrue(resource.isLocal()); assertEquals("local",resource.getServiceId());
    assertNotEquals(first.revision(),second.revision());
    services(local,source("other-remote",component("new-remote-hash",publicRights(),"1.0.0")));
    assertEquals(second.revision(),AuthorityComponentResolver.resolve("fixture.authority@1.0.0",scope).revision());
    local.capabilities(scope).getComponents().clear();
    local.capabilities(scope).getComponents().add(component("rebuilt-local-hash",publicRights(),"1.0.0"));
    assertNotEquals(second.revision(),AuthorityComponentResolver.resolve("fixture.authority@1.0.0",scope).revision());
    verify(local,never()).exportAsset(anyString(),any(),anyString(),any(),any());
    when(local.status().isOperational()).thenReturn(false); services(local,remote);
    assertEquals(first.revision(),AuthorityComponentResolver.resolve("fixture.authority@1.0.0",scope).revision());
  }

  @Test void unauthorizedOrIncompatibleLocalProviderCannotDisplaceRemote() throws Exception {
    var remote=source("remote",component("remote-hash",publicRights(),"1.0.0"));
    var rights=publicRights(); rights.setExcludedUsers(Set.of("owner"));
    var local=localSource(component("local-hash",rights,"1.0.0")); services(remote,local);
    assertEquals("remote-hash",AuthorityComponentResolver.resolve("fixture.authority@1.0.0",scope).components().getFirst().fileHash());
    local.capabilities(scope).getComponents().clear();
    local.capabilities(scope).getComponents().add(component("incompatible",publicRights(),"2.0.0"));
    assertEquals("remote-hash",AuthorityComponentResolver.resolve("fixture.authority@1.0.0",scope).components().getFirst().fileHash());
  }

  @Test void resolvedMetadataIsOwnedAndRightsChangesAlterRevision() throws Exception {
    var rights=publicRights();var remote=source("remote",component("hash-a",rights,"1.0.0"));services(remote);
    var before=AuthorityComponentResolver.resolve("fixture.authority",scope);rights.setExcludedUsers(Set.of("owner"));
    assertTrue(before.components().getFirst().usageRights().getExcludedUsers().isEmpty());
    var after=AuthorityComponentResolver.resolve("fixture.authority",scope);assertTrue(after.components().isEmpty());assertNotEquals(before.revision(),after.revision());
  }
  @Test void ownerWithoutUsableIdentityCannotResolveRemoteMetadata() throws Exception {
    var remote=source("remote",component("hash-a",publicRights(),"1.0.0"));services(remote);when(owner.getId()).thenReturn("");
    assertTrue(AuthorityComponentResolver.resolve("fixture.authority",scope).components().isEmpty());verify(remote,never()).resolve(anyString(),any(),any());
  }

  @Test void successfulResolutionRefreshesStaleClientCapabilitiesOnce() throws Exception {
    var component=component("hash-a",publicRights(),"1.0.0");var seeded=source("remote",component);
    var client=mock(org.integratedmodelling.common.services.client.ResourcesClient.class);
    var url=seeded.getUrl();var status=seeded.status();
    var resolved=seeded.resolve("fixture.authority",KlabAsset.KnowledgeClass.COMPONENT,null);
    when(client.serviceId()).thenReturn("remote");when(client.getUrl()).thenReturn(url);when(client.status()).thenReturn(status);
    when(client.resolve(anyString(),eq(KlabAsset.KnowledgeClass.COMPONENT),any())).thenReturn(resolved);
    var stale=new ResourcesCapabilitiesImpl();var fresh=new ResourcesCapabilitiesImpl();fresh.getComponents().add(component);
    when(client.capabilities(scope)).thenReturn(stale,fresh);services(client);
    assertEquals(1,AuthorityComponentResolver.resolve("fixture.authority",scope).components().size());verify(client,times(1)).invalidateCapabilities();verify(client,times(2)).capabilities(scope);
  }

  @Test void matchingCachedLocalCoordinatesStillRefreshRebuiltSnapshotHash() throws Exception {
    var seeded=localSource(component("old-hash",publicRights(),"1.0.0"));
    var client=mock(org.integratedmodelling.common.services.client.ResourcesClient.class);
    var url=seeded.getUrl(); var status=seeded.status();
    var response=seeded.resolve("fixture.authority",KlabAsset.KnowledgeClass.COMPONENT,null);
    when(client.serviceId()).thenReturn("local"); when(client.getUrl()).thenReturn(url); when(client.status()).thenReturn(status);
    when(client.resolve(anyString(),eq(KlabAsset.KnowledgeClass.COMPONENT),any())).thenReturn(response);
    var fresh=new ResourcesCapabilitiesImpl(); fresh.getComponents().add(component("new-hash",publicRights(),"1.0.0"));
    var cached=new java.util.concurrent.atomic.AtomicReference<>(seeded.capabilities(scope));
    when(client.capabilities(scope)).thenAnswer(call->cached.get());
    doAnswer(call->{cached.set(fresh); return null;}).when(client).invalidateCapabilities();
    services(client);
    var selected=AuthorityComponentResolver.resolve("fixture.authority@1.0.0",scope);
    assertEquals("new-hash",selected.components().getFirst().fileHash());
    verify(client,times(1)).invalidateCapabilities();
    verify(client,never()).exportAsset(anyString(),any(),anyString(),any(),any());
  }

  @Test void genericResolversLatestChoiceCannotHideAnotherEligibleComponentVersion() throws Exception {
    var remote=source("remote",component("new-hash",publicRights(),"1.0.0","3.0.0"));
    remote.capabilities(scope).getComponents().add(component("old-hash",publicRights(),"1.0.0","2.0.0"));services(remote);
    var result=AuthorityComponentResolver.resolve("fixture.authority@1.0.0",scope);
    assertTrue(result.components().isEmpty());assertTrue(result.resources().isEmpty());
    assertTrue(result.diagnostics().stream().anyMatch(n->n.getMessage().contains("Ambiguous authority provider")));
  }

  private ComponentRegistry initializedRegistry(Extensions.ComponentDescriptor installed) throws Exception {
    var host=mock(org.integratedmodelling.klab.services.base.BaseService.class);when(host.serviceId()).thenReturn("host");
    var registry=spy(new ComponentRegistry(host,null,null,List.of(installed)));
    var path=ComponentRegistry.class.getDeclaredField("pluginPath");path.setAccessible(true);path.set(registry,directory.toFile());
    var manager=ComponentRegistry.class.getDeclaredField("componentManager");manager.setAccessible(true);manager.set(registry,mock(org.pf4j.PluginManager.class));
    return registry;
  }

  @Test void selectedVersionAndVettedCoordinatesReachRealLoaderExportBoundary() throws Exception {
    var remote=source("remote",component("selected",publicRights(),"1.0.0","2.0.0"));services(remote);
    var schema=org.integratedmodelling.klab.api.services.resources.ResourceTransport.Schema.create("jar",
        org.integratedmodelling.klab.api.services.resources.ResourceTransport.Schema.Type.STREAM,KlabAsset.KnowledgeClass.COMPONENT,"fixture").mediaType("application/java-archive");
    remote.capabilities(scope).getExportSchemata().put("jar",List.of(schema));
    var response=remote.resolve("fixture.authority",KlabAsset.KnowledgeClass.COMPONENT,null);
    response.getResults().iterator().next().getMetadata().put(Extensions.COMPONENT_MAVEN_COORDINATES_METADATA_KEY,"untrusted:replacement:99");
    var resolution=AuthorityComponentResolver.resolve("fixture.authority@1.0.0",scope);
    var selected=resolution.resources().getResults().iterator().next();
    assertEquals("fixture.component@2.0.0",selected.getResourceUrn());
    assertEquals("fixture:component:2.0.0",selected.getMetadata().get(Extensions.COMPONENT_MAVEN_COORDINATES_METADATA_KEY,String.class));
    // The real loader must not reuse installed v3. Deliberately stop at export, before any file is written.
    var registry=initializedRegistry(component("installed",publicRights(),"1.0.0","3.0.0"));
    when(scope.findService(eq(ResourcesService.class),any())).thenReturn(Optional.of(remote));
    when(remote.exportAsset(anyString(),any(),anyString(),any(),same(scope))).thenThrow(new IllegalStateException("isolated export boundary"));
    assertFalse(registry.loadComponents(resolution.resources(),scope));
    verify(remote).exportAsset(eq("fixture.component@2.0.0"),eq(KlabAsset.KnowledgeClass.COMPONENT),eq("application/java-archive"),any(),same(scope));
    assertEquals(0,Objects.requireNonNull(directory.toFile().list()).length);
  }

  @Test void compatibleInstalledVersionCannotSilentlyReplaceExactBinding() throws Exception {
    var selected=component("selected",publicRights(),"1.0.0","2.0.0");var remote=source("remote",selected);services(remote);
    var registry=initializedRegistry(component("installed",publicRights(),"1.0.0","2.1.0"));
    assertEquals(Version.create("2.1.0"),registry.getComponent("fixture.component",Version.create("2.0.0")).version());
    var target=mock(org.integratedmodelling.klab.services.base.BaseService.class);when(target.getComponentRegistry()).thenReturn(registry);
    var binding=new Worldview.AuthorityBinding("TEST","w:Identity","w",0,1,"source",selected.authorities().getFirst(),selected.id(),selected.version(),null);
    var failure=assertThrows(org.integratedmodelling.klab.api.exceptions.KlabValidationException.class,
        ()->org.integratedmodelling.klab.utilities.Utils.Resources.resolveAuthority(binding,scope,target,worldview()));
    assertTrue(failure.getMessage().contains("Cannot honor exact authority component fixture.component@2.0.0"));
    verify(registry,never()).loadComponents(any(),any());verify(remote,never()).exportAsset(anyString(),any(),anyString(),any(),any());
  }

  @Test void stringDiscoveryUsesExactProviderAndComponentAfterInstallation() throws Exception {
    var remote=source("remote",component("selected",publicRights(),"1.0.0","2.0.0"));services(remote);
    var registry=mock(ComponentRegistry.class);var target=mock(org.integratedmodelling.klab.services.base.BaseService.class);
    when(target.getComponentRegistry()).thenReturn(registry);when(target.serviceType()).thenReturn(KlabService.Type.REASONER);
    var implementation=mock(org.integratedmodelling.klab.api.services.Authority.class);
    when(registry.loadComponents(any(),same(scope))).thenReturn(true);
    when(registry.getAuthority(any(Worldview.AuthorityBinding.class),same(scope))).thenReturn(implementation);
    assertSame(implementation,org.integratedmodelling.klab.utilities.Utils.Resources.resolveAuthority("fixture.authority@1.0.0",scope,target,worldview()));
    verify(registry).getAuthority(argThat((Worldview.AuthorityBinding binding)->binding.componentVersion().equals(Version.create("2.0.0"))
        && binding.provider().version().equals(Version.create("1.0.0"))),same(scope));
    verify(registry).loadComponents(argThat(resources->resources.getResults().stream().allMatch(resource->"fixture.component@2.0.0".equals(resource.getResourceUrn())
        && "fixture:component:2.0.0".equals(resource.getMetadata().get(Extensions.COMPONENT_MAVEN_COORDINATES_METADATA_KEY)))),same(scope));
  }


  @Test void bootstrapArtifactHashIsCheckedAtRealDownloadBoundaryBeforePluginExecution() throws Exception {
    var remote=source("remote",component("selected",publicRights(),"1.0.0","2.0.0"));services(remote);
    var schema=org.integratedmodelling.klab.api.services.resources.ResourceTransport.Schema.create("jar",
        org.integratedmodelling.klab.api.services.resources.ResourceTransport.Schema.Type.STREAM,KlabAsset.KnowledgeClass.COMPONENT,"fixture").mediaType("application/java-archive");
    remote.capabilities(scope).getExportSchemata().put("jar",List.of(schema));
    var resolution=AuthorityComponentResolver.resolve("fixture.authority@1.0.0",scope);
    resolution.resources().getResults().iterator().next().getMetadata().put("authority.bootstrap.expected.md5","00000000000000000000000000000000");
    var registry=initializedRegistry(component("installed",publicRights(),"1.0.0","3.0.0"));
    when(scope.findService(eq(ResourcesService.class),any())).thenReturn(Optional.of(remote));
    when(remote.exportAsset(anyString(),any(),anyString(),any(),same(scope))).thenReturn(new java.io.ByteArrayInputStream("wrong artifact".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    assertFalse(registry.loadComponents(resolution.resources(),scope));
    assertTrue(resolution.resources().getNotifications().stream().anyMatch(n->n.getMessage().contains("differs from selected descriptor")));
    var field=ComponentRegistry.class.getDeclaredField("componentManager");field.setAccessible(true);
    verify((org.pf4j.PluginManager)field.get(registry),never()).loadPlugin(any());
  }


  @Test void explicitStrongPinRejectsDifferentBytesEvenWhenDescriptorMd5Matches() throws Exception {
    var remote=source("remote",component("selected",publicRights(),"1.0.0","2.0.0"));services(remote);
    var schema=org.integratedmodelling.klab.api.services.resources.ResourceTransport.Schema.create("jar",
        org.integratedmodelling.klab.api.services.resources.ResourceTransport.Schema.Type.STREAM,KlabAsset.KnowledgeClass.COMPONENT,"fixture").mediaType("application/java-archive");
    remote.capabilities(scope).getExportSchemata().put("jar",List.of(schema));
    var resolution=AuthorityComponentResolver.resolve("fixture.authority@1.0.0",scope);var resource=resolution.resources().getResults().iterator().next();
    byte[] bytes="different content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    resource.getMetadata().put("authority.bootstrap.expected.md5",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("MD5").digest(bytes)));
    resource.getMetadata().put("authority.bootstrap.expected.sha256","0".repeat(64));
    var registry=initializedRegistry(component("installed",publicRights(),"1.0.0","3.0.0"));
    when(scope.findService(eq(ResourcesService.class),any())).thenReturn(Optional.of(remote));
    when(remote.exportAsset(anyString(),any(),anyString(),any(),same(scope))).thenReturn(new java.io.ByteArrayInputStream(bytes));
    assertFalse(registry.loadComponents(resolution.resources(),scope));
    assertTrue(resolution.resources().getNotifications().stream().anyMatch(n->n.getMessage().contains("differs from approved SHA-256")));
    var field=ComponentRegistry.class.getDeclaredField("componentManager");field.setAccessible(true);verify((org.pf4j.PluginManager)field.get(registry),never()).loadPlugin(any());
  }
  @Test void explicitSourceUrlMismatchRejectsBeforeAnyExport() throws Exception {
    var remote=source("remote",component("selected",publicRights(),"1.0.0","2.0.0"));services(remote);
    var resolution=AuthorityComponentResolver.resolve("fixture.authority@1.0.0",scope);
    resolution.resources().getResults().iterator().next().getMetadata().put("authority.bootstrap.source.url","http://127.0.0.1:8092/resources");
    var registry=initializedRegistry(component("installed",publicRights(),"1.0.0","3.0.0"));
    when(scope.findService(eq(ResourcesService.class),any())).thenReturn(Optional.of(remote));
    assertFalse(registry.loadComponents(resolution.resources(),scope));
    assertTrue(resolution.resources().getNotifications().stream().anyMatch(n->n.getMessage().contains("source differs from explicit approval")));
    verify(remote,never()).exportAsset(anyString(),any(),anyString(),any(),any());assertEquals(0,Objects.requireNonNull(directory.toFile().list()).length);
  }
}
