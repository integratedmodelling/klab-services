package org.integratedmodelling.klab.services.reasoner.internal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.File;
import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.common.services.ResourcesCapabilitiesImpl;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.junit.jupiter.api.Test;
class AuthorityBootstrapPlanTest {
  private final UserIdentity identity=mock(UserIdentity.class);
  private final ServiceScope scope=mock(ServiceScope.class);
  private final ResourcesService resources=mock(ResourcesService.class);
  private final ResourcePrivileges rights=new ResourcePrivileges();
  private final ResourcesCapabilitiesImpl capabilities=new ResourcesCapabilitiesImpl();
  private final Extensions.AuthorityDescriptor authority=new Extensions.AuthorityDescriptor("klab.authority.taxa",Version.create("1.0.0"),true,true,List.of(),List.of());
  private final AtomicInteger producers=new AtomicInteger();
  AuthorityBootstrapPlanTest() throws Exception {
    when(identity.getUsername()).thenReturn("owner");when(identity.getId()).thenReturn("owner-context");when(identity.getGroups()).thenReturn(List.of());when(scope.getIdentity()).thenReturn(identity);
    when(scope.getServices(ResourcesService.class)).thenReturn(List.of(resources));when(resources.serviceId()).thenReturn("source");when(resources.getUrl()).thenReturn(URI.create("https://authorized.invalid/resources").toURL());
    var status=mock(KlabService.ServiceStatus.class);when(status.isOperational()).thenReturn(true);when(resources.status()).thenReturn(status);
    when(resources.capabilities(any())).thenReturn(capabilities);rights.setPublic(true);advertise("1.0.0-SNAPSHOT");
  }
  private void advertise(String version) {
    capabilities.getComponents().clear();capabilities.getComponents().add(new Extensions.ComponentDescriptor("klab.authority.taxa",Version.create(version),"fixture",new File("fixture.jar"),
        "0123456789abcdef0123456789abcdef","vetted:taxa:"+version,rights,List.of(),List.of(),List.of(authority),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),"source",1,null,null,0));
    var response=ResourceSet.of(new ResourceSet.Resource("source","klab.authority.taxa@"+version,null,Version.create(version),KlabAsset.KnowledgeClass.COMPONENT,1,false));
    response.getServices().put("source",resources.getUrl());when(resources.resolve("klab.authority.taxa",KlabAsset.KnowledgeClass.COMPONENT,null)).thenReturn(response);
  }
  private Worldview source() {
    var source=new WorldviewImpl();source.setUrn("w");var ontology=new KimOntologyImpl();ontology.setUrn("w");ontology.setSourceCode("source");source.getOntologies().add(ontology);
    source.getAuthorityBindings().add(new Worldview.AuthorityBinding("TAXA","w:Identity","w",0,1,"hash",authority,"klab.authority.taxa",Version.create("1.0.0-SNAPSHOT"),null));return source;
  }
  private AuthorityBootstrap bootstrap() {
    return new AuthorityBootstrap(AuthorityBootstrap::resolve,(w,s,p,c)->{producers.incrementAndGet();var catalog=mock(CapturedAuthorityCatalog.class);when(catalog.supports(any())).thenReturn(true);return catalog;});
  }
  @Test void actualDescriptorResolverSuppliesPinnedExactAuthorizedLoaderPlan() {
    var plan=AuthorityBootstrap.resolve(source(),scope);var resource=plan.resources().getResults().iterator().next();
    assertEquals("klab.authority.taxa@1.0.0-SNAPSHOT",resource.getResourceUrn());assertEquals("source",resource.getServiceId());
    assertEquals("0123456789abcdef0123456789abcdef",resource.getMetadata().get("authority.bootstrap.expected.md5"));
    assertEquals("vetted:taxa:1.0.0-SNAPSHOT",resource.getMetadata().get(Extensions.COMPONENT_MAVEN_COORDINATES_METADATA_KEY));
    verify(resources,never()).exportAsset(anyString(),any(),anyString(),any(),any());
  }
  @Test void compatibleDifferentVersionCannotReachProducer() {
    advertise("1.1.0-SNAPSHOT");var source=source();var bootstrap=bootstrap();
    assertThrows(IllegalStateException.class,()->bootstrap.prepare(source,scope,AuthorityBootstrap.key(source,scope),()->false));assertEquals(0,producers.get());
  }
  @Test void excludedOwnerCannotReachProducer() {
    rights.setExcludedUsers(Set.of("owner"));var source=source();var bootstrap=bootstrap();
    assertThrows(IllegalStateException.class,()->bootstrap.prepare(source,scope,AuthorityBootstrap.key(source,scope),()->false));assertEquals(0,producers.get());
  }
  @Test void privateGrantIsNotConvertedIntoUnsupportedPublicCapture() {
    rights.setPublic(false);rights.setAllowedUsers(Set.of("owner"));var source=source();var bootstrap=bootstrap();
    assertThrows(IllegalStateException.class,()->bootstrap.prepare(source,scope,AuthorityBootstrap.key(source,scope),()->false));assertEquals(0,producers.get());
  }
  @Test void anonymousOwnerCannotReachMetadataOrProducer() {
    when(identity.isAnonymous()).thenReturn(true);var source=source();var bootstrap=bootstrap();
    assertThrows(IllegalStateException.class,()->bootstrap.prepare(source,scope,AuthorityBootstrap.key(source,scope),()->false));assertEquals(0,producers.get());
    verify(resources,never()).resolve(anyString(),any(),any());
  }

  @Test void serviceCertificateCanBootstrapPublicBackboneWithoutUserIdentity() {
    when(scope.getIdentity()).thenReturn(mock(org.integratedmodelling.klab.api.identities.PartnerIdentity.class));
    assertEquals(1,AuthorityBootstrap.resolve(source(),scope).components().size());
    rights.setPublic(false);
    rights.setAllowedUsers(Set.of("owner"));
    assertThrows(IllegalStateException.class,()->AuthorityBootstrap.resolve(source(),scope));
  }

  @Test void lateLocalArtifactUsesNewCaptureAndUnchangedSelectionReusesIt() throws Exception {
    var bootstrap=bootstrap(); var source=source();
    source.getMetadata().put(org.integratedmodelling.klab.components.AuthorityComponentResolver.SELECTION_METADATA,
        bootstrap.selectionRevision(source,scope));
    var remoteKey=AuthorityBootstrap.key(source,scope);
    bootstrap.prepare(source,scope,remoteKey,()->false);
    var local=mock(ResourcesService.class);
    when(local.serviceId()).thenReturn("local");
    when(local.getUrl()).thenReturn(URI.create("http://localhost:8092/resources").toURL());
    var available=resources.status(); when(local.status()).thenReturn(available);
    var localCaps=new ResourcesCapabilitiesImpl();
    localCaps.getComponents().add(new Extensions.ComponentDescriptor("klab.authority.taxa",Version.create("1.0.0-SNAPSHOT"),"local fixture",new File("local.jar"),
        "abcdef0123456789abcdef0123456789","vetted:taxa:1.0.0-SNAPSHOT",rights,List.of(),List.of(),List.of(authority),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),"local",1,null,null,0));
    when(local.capabilities(any())).thenReturn(localCaps);
    var response=ResourceSet.of(new ResourceSet.Resource("local","klab.authority.taxa@1.0.0-SNAPSHOT",null,Version.create("1.0.0-SNAPSHOT"),KlabAsset.KnowledgeClass.COMPONENT,1,false));
    response.getServices().put("local",local.getUrl());
    when(local.resolve(anyString(),eq(KlabAsset.KnowledgeClass.COMPONENT),any())).thenReturn(response);
    when(scope.getServices(ResourcesService.class)).thenReturn(List.of(resources,local));
    assertThrows(IllegalStateException.class,()->bootstrap.prepare(source,scope,remoteKey,()->false));
    assertEquals(1,producers.get());
    var selected=AuthorityBootstrap.resolve(source,scope);
    assertEquals("local",selected.resources().getResults().iterator().next().getServiceId());
    assertEquals("abcdef0123456789abcdef0123456789",selected.components().getFirst().fileHash());
    source.getMetadata().put(org.integratedmodelling.klab.components.AuthorityComponentResolver.SELECTION_METADATA,
        bootstrap.selectionRevision(source,scope));
    var localKey=AuthorityBootstrap.key(source,scope); assertNotEquals(remoteKey,localKey);
    bootstrap.prepare(source,scope,localKey,()->false);
    bootstrap.prepare(source,scope,localKey,()->false);
    assertEquals(2,producers.get());
    verify(local,never()).exportAsset(anyString(),any(),anyString(),any(),any());
  }
}
