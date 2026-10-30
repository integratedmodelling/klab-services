package org.integratedmodelling.klab.services.resources.lang;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.File;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import org.integratedmodelling.common.services.ResourcesCapabilitiesImpl;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;
import org.integratedmodelling.klab.services.resources.storage.WorkspaceManager;
import org.integratedmodelling.klab.services.reasoner.internal.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RemoteAuthorityMetadataAdmissionTest {
  @TempDir Path directory;
  private record Fixture(ResourcesProvider provider, ServiceScope scope, ResourcesService remote,
                         ComponentRegistry registry, WorldviewImpl source) {}
  private Fixture fixture(ResourcePrivileges rights) throws Exception {
    var owner=mock(UserIdentity.class);when(owner.getUsername()).thenReturn("owner");when(owner.getId()).thenReturn("test-owner-context");when(owner.getGroups()).thenReturn(List.of());
    var scope=mock(ServiceScope.class);when(scope.getIdentity()).thenReturn(owner);
    var declaration=new KimConceptStatementImpl();declaration.setNamespace("w");declaration.setUrn("Identity");
    declaration.setType(EnumSet.of(SemanticType.IDENTITY,SemanticType.PREDICATE));declaration.setAuthorityRequired("TEST");
    declaration.setAuthorityParameters(new HashMap<>(Map.of("urn","fixture.authority@1.0.0")));
    var ontology=new KimOntologyImpl();ontology.setUrn("w");ontology.setProjectName("fixture");ontology.setSourceCode(" ".repeat(100));ontology.getStatements().add(declaration);
    var source=new WorldviewImpl();source.setUrn("w");source.setWorldviewId("w");source.setEmpty(false);source.setOntologies(new ArrayList<>(List.of(ontology)));
    var registry=mock(ComponentRegistry.class);when(registry.getComponents(scope)).thenReturn(List.of());
    var manager=mock(WorkspaceManager.class);when(manager.getWorldview()).thenReturn(source);
    var provider=mock(ResourcesProvider.class,CALLS_REAL_METHODS);doReturn(registry).when(provider).getComponentRegistry();doReturn("local").when(provider).serviceId();doReturn(scope).when(provider).serviceScope();
    var field=ResourcesProvider.class.getDeclaredField("workspaceManager");field.setAccessible(true);field.set(provider,manager);
    var remote=mock(ResourcesService.class);when(remote.serviceId()).thenReturn("remote");when(remote.getUrl()).thenReturn(URI.create("https://remote.invalid/resources").toURL());
    var status=mock(KlabService.ServiceStatus.class);when(status.isOperational()).thenReturn(true);when(remote.status()).thenReturn(status);
    var component=new Extensions.ComponentDescriptor("fixture.component",Version.create("2.0.0"),"fixture",new File("fixture.jar"),"hash",null,rights,List.of(),List.of(),
        List.of(new Extensions.AuthorityDescriptor("fixture.authority",Version.create("1.0.0"),true,true,List.of(),List.of())),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),null,1,null,null,0);
    var caps=new ResourcesCapabilitiesImpl();caps.getComponents().add(component);caps.setWorldviewProvider(false);caps.setAdoptedWorldview("different");when(remote.capabilities(any())).thenReturn(caps);
    var result=ResourceSet.of(new ResourceSet.Resource("remote",component.id(),null,component.version(),KlabAsset.KnowledgeClass.COMPONENT,1,false));result.getServices().put("remote",remote.getUrl());
    when(remote.resolve(anyString(),eq(KlabAsset.KnowledgeClass.COMPONENT),any())).thenReturn(result);when(scope.getServices(ResourcesService.class)).thenReturn(List.of(remote));
    return new Fixture(provider,scope,remote,registry,source);
  }
  private Worldview retrieve(Fixture fixture)throws Exception {
    var method=ResourcesProvider.class.getDeclaredMethod("retrieveWorldview",Scope.class);method.setAccessible(true);return (Worldview)method.invoke(fixture.provider(),fixture.scope());
  }

  @Test void requestScopeWithoutPeersStillDiscoversThroughHostServices() throws Exception {
    var rights = new ResourcePrivileges(); rights.setPublic(true);
    var fixture = fixture(rights);
    var request = mock(UserScope.class);
    var owner = (UserIdentity) fixture.scope().getIdentity();
    when(request.getUser()).thenReturn(owner); when(request.getIdentity()).thenReturn(owner);
    when(request.getServices(ResourcesService.class)).thenReturn(List.of());
    var worldview = fixture.provider().list(Worldview.class, request).getFirst();
    assertEquals(1, worldview.getAuthorityBindings().size());
    assertTrue(worldview.getNotifications().isEmpty());
    verify(fixture.remote()).resolve(eq("fixture.authority"), eq(KlabAsset.KnowledgeClass.COMPONENT), same(request));
    verify(fixture.registry(), never()).loadComponents(any(), any());
  }

  @Test void realServerResolverKeepsAuthorityVersionSeparateFromComponentVersion() throws Exception {
    var rights=new ResourcePrivileges();rights.setPublic(true);var fixture=fixture(rights);
    var component=fixture.remote().capabilities(fixture.scope()).getComponents().iterator().next();
    var host=mock(org.integratedmodelling.klab.services.base.BaseService.class);when(host.serviceId()).thenReturn("remote");
    var constructor=Arrays.stream(ComponentRegistry.class.getDeclaredConstructors()).filter(c->c.getParameterCount()==4).findFirst().orElseThrow();constructor.setAccessible(true);
    var registry=spy((ComponentRegistry)constructor.newInstance(host,null,null,List.of(component)));
    var finder=ComponentRegistry.class.getDeclaredField("authorityFinder");finder.setAccessible(true);
    @SuppressWarnings("unchecked") var authorities=(org.apache.commons.collections4.MultiValuedMap<String,Extensions.ComponentDescriptor>)finder.get(registry);
    authorities.put("fixture.authority",component);
    var server=mock(ResourcesProvider.class,CALLS_REAL_METHODS);doReturn(registry).when(server).getComponentRegistry();doReturn("remote").when(server).serviceId();
    var url=fixture.remote().getUrl();doReturn(url).when(server).getUrl();
    // Existing server protocol treats this suffix as component version: authority v1 is in component v2.
    assertTrue(server.resolveAuthority("fixture.authority@1.0.0",fixture.scope()).isEmpty());
    when(fixture.remote().resolve(anyString(),eq(KlabAsset.KnowledgeClass.COMPONENT),any()))
        .thenAnswer(call->server.resolveAuthority(call.getArgument(0),fixture.scope()));
    var resolved=org.integratedmodelling.klab.components.AuthorityComponentResolver.resolve("fixture.authority@1.0.0",fixture.scope());
    assertEquals(1,resolved.components().size());
    assertEquals(Version.create("1.0.0"),resolved.components().getFirst().authorities().getFirst().version());
    assertEquals("fixture.component@2.0.0",resolved.resources().getResults().iterator().next().getResourceUrn());
    var mismatch=org.integratedmodelling.klab.components.AuthorityComponentResolver.resolve("fixture.authority@1.1.0",fixture.scope());
    assertTrue(mismatch.components().isEmpty());assertTrue(mismatch.resources().isEmpty());
    assertTrue(mismatch.diagnostics().stream().anyMatch(n->n.getMessage().contains("could not honor the exact")));
    verify(registry,never()).loadComponents(any(),any());verify(fixture.remote(),never()).exportAsset(anyString(),any(),anyString(),any(),any());
  }

  @Test void supportedComponentResolutionRunsBeforeAuthorityValidationWithoutInstalling() throws Exception {
    var rights=new ResourcePrivileges();rights.setPublic(true);var fixture=fixture(rights);var admitted=retrieve(fixture);
    assertEquals(1,admitted.getAuthorityBindings().size());assertEquals("fixture.component",admitted.getAuthorityBindings().getFirst().componentUrn());
    assertEquals(Version.create("2.0.0"),admitted.getAuthorityBindings().getFirst().componentVersion());
    assertTrue(admitted.getNotifications().stream().noneMatch(n->n.getLevel()==org.integratedmodelling.klab.api.services.runtime.Notification.Level.Error));
    assertTrue(fixture.source().getAuthorityBindings().isEmpty()); // Retrieval does not rewrite the cached authored worldview.
    verify(fixture.remote()).resolve("fixture.authority",KlabAsset.KnowledgeClass.COMPONENT,null);
    verify(fixture.registry(),never()).loadComponents(any(),any());
  }

  @Test void unrelatedInstalledAdapterCannotTurnWorldviewRetrievalIntoServerError() throws Exception {
    var rights=new ResourcePrivileges();rights.setPublic(true);var fixture=fixture(rights);
    var adapter=new org.integratedmodelling.klab.api.services.runtime.extension.AdapterDescriptor();
    adapter.setName("geospatial.fixture");adapter.setVersion(Version.create("1.0.0"));
    adapter.getMetadata().put("nested",Map.of("kind","live-shaped capability metadata"));
    var wire=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper().valueToTree(adapter);
    assertEquals(adapter.getClass().getName(),wire.path("@CLASS").asText());
    var unrelated=new Extensions.ComponentDescriptor("fixture.geospatial",Version.create("1.0.0"),"adapter-only component",
        new File("geospatial.jar"),"adapter-hash",null,rights,List.of(),List.of(adapter),List.of(),
        Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),"local",1,null,null,0);
    when(fixture.registry().getComponents(fixture.scope())).thenReturn(List.of(unrelated));
    var result=retrieve(fixture);
    assertFalse(result.isEmpty());assertEquals(1,result.getAuthorityBindings().size());
    assertEquals("fixture.component",result.getAuthorityBindings().getFirst().componentUrn());
    assertEquals(Version.create("2.0.0"),result.getAuthorityBindings().getFirst().componentVersion());
    assertEquals(1,unrelated.adapters().size());verify(fixture.registry(),never()).loadComponents(any(),any());
    verify(fixture.remote(),never()).exportAsset(anyString(),any(),anyString(),any(),any());
  }

  @Test void deniedRemoteOwnerRemainsAnUnresolvedAuthority() throws Exception {
    var rights=new ResourcePrivileges();rights.setPublic(true);rights.setExcludedUsers(Set.of("owner"));
    var result=retrieve(fixture(rights));assertTrue(result.getAuthorityBindings().isEmpty());
    assertTrue(result.getNotifications().stream().anyMatch(n->n.getMessage().contains("Missing authority provider descriptor")));
  }

  @Test void admittedDescriptorCannotBypassImmutableProviderCapture() throws Exception {
    var rights=new ResourcePrivileges();rights.setPublic(true);var world=retrieve(fixture(rights));
    var files=new LinkedHashMap<String,byte[]>();var coreDirectory=System.getProperty("klab.test.core.directory");
    for(String name:List.of("odo.owl","klab.owl","dc.owl")) {
      if(coreDirectory!=null)files.put(name,Files.readAllBytes(Path.of(coreDirectory,name)));
      else try(var input=SemanticEnvironmentFactory.class.getResourceAsStream("/knowledge/"+name)) {
        assertNotNull(input,"Missing packaged core fixture "+name);files.put(name,input.readAllBytes());
      }
    }
    var factory=new SemanticEnvironmentFactory(directory.resolve("private-assets"),files);
    var input=factory.capture(world);assertEquals(CandidatePublicationCoordinator.BeanStatus.VALID,input.beanStatus());
    var result=factory.build(input,()->false);assertEquals(CandidatePublicationCoordinator.LogicalStatus.UNRESOLVED,result.logicalStatus());
    assertNull(result.state());assertTrue(result.diagnostics().stream().anyMatch(n->n.contains("matching owned provider snapshot")));
  }
}
