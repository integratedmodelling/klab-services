package org.integratedmodelling.klab.services.reasoner.internal;
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
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
class BootstrapSourcePinTest {
  @TempDir Path directory;
  private final String url="http://127.0.0.1:8092/resources",md5="1".repeat(32),sha="3".repeat(64);
  private final UserIdentity identity=mock(UserIdentity.class);
  private final ServiceScope owner=mock(ServiceScope.class);
  private final ResourcesService local=mock(ResourcesService.class),peer=mock(ResourcesService.class);
  private final ResourcePrivileges rights=new ResourcePrivileges();
  private final ResourcesCapabilitiesImpl localCaps=new ResourcesCapabilitiesImpl();
  private final Extensions.AuthorityDescriptor authority=new Extensions.AuthorityDescriptor("klab.authority.taxa",Version.create("1.0.0"),true,true,List.of(),List.of());
  BootstrapSourcePinTest() throws Exception {
    when(identity.getId()).thenReturn("owner-token");when(identity.getUsername()).thenReturn("owner");when(identity.getGroups()).thenReturn(List.of());when(owner.getIdentity()).thenReturn(identity);
    rights.setPublic(true);setup(local,"local",url,md5,localCaps);
    setup(peer,"peer","https://peer.invalid/resources","2".repeat(32),new ResourcesCapabilitiesImpl());
    when(owner.getServices(ResourcesService.class)).thenReturn(List.of(local,peer));
  }
  private Extensions.ComponentDescriptor descriptor(String hash) {
    return new Extensions.ComponentDescriptor("klab.authority.taxa",Version.create("1.0.0-SNAPSHOT"),"fixture",new File("fixture.jar"),hash,"vetted:taxa:1.0.0-SNAPSHOT",rights,List.of(),List.of(),List.of(authority),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),null,1,null,null,0);
  }
  private void setup(ResourcesService service,String id,String endpoint,String hash,ResourcesCapabilitiesImpl caps)throws Exception {
    when(service.serviceId()).thenReturn(id);when(service.getUrl()).thenReturn(URI.create(endpoint).toURL());
    var status=mock(KlabService.ServiceStatus.class);when(status.isOperational()).thenReturn(true);when(service.status()).thenReturn(status);
    caps.getComponents().add(descriptor(hash));when(service.capabilities(any())).thenReturn(caps);
    var result=ResourceSet.of(new ResourceSet.Resource(id,"klab.authority.taxa@1.0.0-SNAPSHOT",null,Version.create("1.0.0-SNAPSHOT"),KlabAsset.KnowledgeClass.COMPONENT,1,false));result.getServices().put(id,service.getUrl());
    when(service.resolve(eq("klab.authority.taxa"),eq(KlabAsset.KnowledgeClass.COMPONENT),any())).thenReturn(result);
  }
  private Worldview source() {
    var world=new WorldviewImpl();world.setUrn("w");var document=new KimOntologyImpl();document.setUrn("w");document.setSourceCode("source");world.getOntologies().add(document);
    world.getAuthorityBindings().add(new Worldview.AuthorityBinding("TAXA","w:Identity","w",0,1,"hash",authority,"klab.authority.taxa",Version.create("1.0.0-SNAPSHOT"),null));return world;
  }
  private BootstrapSourcePin pin(String id,String endpoint,String version,String digest,String strong) {
    return new BootstrapSourcePin(id,endpoint,Map.of("klab.authority.taxa",new BootstrapSourcePin.Artifact(version,"1.0.0",digest,strong),"klab.authority.pubchem",new BootstrapSourcePin.Artifact("1.0.0-SNAPSHOT","1.0.0","c".repeat(32),"d".repeat(64))));
  }
  private BootstrapSourcePin pin(){return pin("local",url,"1.0.0-SNAPSHOT",md5,sha);}
  @Test void defaultDiscoveryPrefersLocalBuildWithSameVersionDifferentContent() {
    var plan=AuthorityBootstrap.resolve(source(),owner);
    assertEquals(md5,plan.components().getFirst().fileHash());
    assertEquals("local",plan.resources().getResults().iterator().next().getServiceId());
    verify(local,never()).exportAsset(anyString(),any(),anyString(),any(),any());
  }
  @Test void explicitPinQueriesOnlyApprovedSourceAndCarriesBothContentAndSourceGuards() {
    var plan=AuthorityBootstrap.resolve(source(),owner,pin());var resource=plan.resources().getResults().iterator().next();
    assertEquals(1,plan.components().size());assertEquals(md5,plan.components().getFirst().fileHash());assertEquals("local",resource.getServiceId());
    assertEquals(sha,resource.getMetadata().get("authority.bootstrap.expected.sha256"));assertEquals(url,resource.getMetadata().get("authority.bootstrap.source.url"));
    verify(peer,never()).resolve(anyString(),any(),any());verify(peer,never()).capabilities(any());
  }
  @Test void wrongServiceIdentityOrUrlNeverFallsBackToAnotherPeer() {
    assertThrows(IllegalStateException.class,()->AuthorityBootstrap.resolve(source(),owner,pin("absent",url,"1.0.0-SNAPSHOT",md5,sha)));
    assertThrows(IllegalStateException.class,()->AuthorityBootstrap.resolve(source(),owner,pin("local","http://127.0.0.1:8099/resources","1.0.0-SNAPSHOT",md5,sha)));
    verify(local,never()).resolve(anyString(),any(),any());verify(peer,never()).resolve(anyString(),any(),any());
  }
  @Test void wrongArtifactPinCannotReachExport() {
    assertThrows(IllegalStateException.class,()->AuthorityBootstrap.resolve(source(),owner,pin("local",url,"1.0.0-SNAPSHOT","9".repeat(32),sha)));
    verify(local,never()).exportAsset(anyString(),any(),anyString(),any(),any());
  }
  @Test void wrongApprovedVersionDoesNotResolveCompatibleAlternative() {
    assertThrows(IllegalStateException.class,()->AuthorityBootstrap.resolve(source(),owner,pin("local",url,"1.1.0-SNAPSHOT",md5,sha)));
    verify(local,never()).resolve(anyString(),any(),any());
  }
  @Test void excludedOwnerStillFailsOnPinnedSource() {
    rights.setExcludedUsers(Set.of("owner"));assertThrows(IllegalStateException.class,()->AuthorityBootstrap.resolve(source(),owner,pin()));
    verify(local,never()).exportAsset(anyString(),any(),anyString(),any(),any());
  }
  @Test void ambiguityWithinPinnedServiceIsStillRejectedEvenIfOneDigestMatches() {
    localCaps.getComponents().add(descriptor("7".repeat(32)));
    assertThrows(IllegalStateException.class,()->AuthorityBootstrap.resolve(source(),owner,pin()));
  }
  @Test void requestingUserIdentityAndUserScopeProtocolArePreserved() {
    var user=mock(UserScope.class);when(user.getUser()).thenReturn(identity);when(user.getIdentity()).thenReturn(identity);when(user.getServices(ResourcesService.class)).thenReturn(List.of(local,peer));
    AuthorityBootstrap.resolve(source(),user,pin());
    verify(local).resolve(eq("klab.authority.taxa"),eq(KlabAsset.KnowledgeClass.COMPONENT),argThat(s->s!=null && s.getUser()==identity && s.getIdentity()==identity && s.getServices(ResourcesService.class).equals(List.of(local))));
  }
  @Test void pinRevisionIncludesStrongArtifactDigestAndSource() {
    assertNotEquals(pin().revision(),pin("local",url,"1.0.0-SNAPSHOT",md5,"8".repeat(64)).revision());
    assertNotEquals(pin().revision(),pin("other",url,"1.0.0-SNAPSHOT",md5,sha).revision());
  }
  @Test void pinFileIsStrictAndCannotNameRemoteSourceOrVersionRange()throws Exception {
    assertThrows(IllegalArgumentException.class,()->pin("peer","https://peer.invalid:443/resources","1.0.0-SNAPSHOT",md5,sha));
    assertThrows(IllegalArgumentException.class,()->pin("local",url,">=1.0.0",md5,sha));
    var json=new com.fasterxml.jackson.databind.ObjectMapper();var file=directory.resolve("pin.json");Files.write(file,json.writeValueAsBytes(pin()));assertEquals(pin(),BootstrapSourcePin.read(file));
    var tree=json.valueToTree(pin());((com.fasterxml.jackson.databind.node.ObjectNode)tree).put("unknownGrant",true);Files.write(file,json.writeValueAsBytes(tree));assertThrows(Exception.class,()->BootstrapSourcePin.read(file));
  }
}
