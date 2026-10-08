package org.integratedmodelling.klab.services.reasoner;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.integratedmodelling.klab.api.services.Authority;
import org.integratedmodelling.klab.api.knowledge.impl.CodelistImpl;
import org.integratedmodelling.klab.api.services.resources.objects.AuthorityIdentity;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.services.reasoner.internal.*;

class AuthorityCodelistsTest {
  @TempDir Path directory;
  AuthorityBindings.Binding binding() {
    var provider = mock(Authority.class);
    var identity = new AuthorityIdentity(); identity.setId("3DXV3"); identity.setConceptName("Cat"); identity.setLabel("Felis catus");
    when(provider.resolveIdentity("configured", "3DXV3")).thenReturn(identity);
    when(provider.getCodelists("configured")).thenReturn(Map.of("species", new CodelistImpl()));
    when(provider.getCodelistDefinitions("configured")).thenCallRealMethod();
    return new AuthorityBindings.Binding(new Authority.ConfigurationRequest("worldview", "TAXA", "life:Identity",
        Map.of("urn", "taxa", "codelists", Map.of("species", "taxonomy.species"))), provider, "configured");
  }
  AuthorityCodelistRequest submit() {
    return new AuthorityCodelistRequest(AuthorityCodelistRequest.Operation.SUBMIT, "TAXA", "taxonomy.species",
        "FelisCatus", "3DXV3", null, 0, null, null, null, null);
  }

  @Test void undeclaredWorldviewListStartsEmptyAndUsesTheSameDurableReviewWorkflow() {
    var binding = binding();
    when(binding.provider().getCodelists("configured")).thenReturn(Map.of());
    var store = new AuthorityCodelists(binding, directory);
    var policy = store.snapshot().policies().get("taxonomy.species");
    assertEquals("species", policy.listId());
    assertFalse(policy.providerDeclared());
    assertTrue(policy.acceptsProposals());
    assertTrue(store.snapshot().codelists().get("taxonomy.species").codes().isEmpty());
    var pending = store.execute(submit(), "community-member");
    assertNull(store.resolve("taxonomy.species", "FelisCatus"));
    store.execute(new AuthorityCodelistRequest(AuthorityCodelistRequest.Operation.REVIEW, "TAXA",
        "taxonomy.species", null, null, pending.proposals().getFirst().id(), pending.revision(),
        AuthorityCodelistRequest.Decision.ACCEPT, null, null, null), "admin");
    assertEquals("3DXV3", new AuthorityCodelists(binding, directory).resolve("taxonomy.species", "FelisCatus"));
    assertThrows(IllegalArgumentException.class, () -> store.execute(new AuthorityCodelistRequest(
        AuthorityCodelistRequest.Operation.SUBMIT, "TAXA", "unbound.list", "Cat", "3DXV3",
        null, 0, null, null, null, null), "user"));
  }

  @Test void providerPolicyRejectsNewProposalsButAllowsAdministratorManagement() throws Exception {
    var binding = binding();
    var seed = new CodelistImpl();
    seed.getEntries().add(new CodelistImpl.Entry("TAXA", "OfficialCat", "3DXV3", null, true));
    doReturn(Map.of("species", new Authority.CodelistDefinition(seed, false)))
        .when(binding.provider()).getCodelistDefinitions("configured");
    var store = new AuthorityCodelists(binding, directory);
    assertEquals("3DXV3", store.resolve("taxonomy.species", "OfficialCat"));
    var policy = store.snapshot().policies().get("taxonomy.species");
    assertTrue(policy.providerDeclared());
    assertFalse(policy.acceptsProposals());
    assertTrue(assertThrows(IllegalArgumentException.class, () -> store.execute(submit(), "user"))
        .getMessage().contains("does not accept proposals"));
    assertEquals(0, store.revision());
    assertTrue(store.snapshot().proposals().isEmpty());
    store.execute(managed(AuthorityCodelistRequest.Operation.CREATE, "Cat", null, "3DXV3", 0), "admin");
    store.execute(managed(AuthorityCodelistRequest.Operation.UPDATE, "Cat", "DomesticCat", "3DXV3", 1), "admin");
    var restarted = new AuthorityCodelists(binding, directory);
    assertEquals("3DXV3", restarted.resolve("taxonomy.species", "DomesticCat"));
    restarted.execute(managed(AuthorityCodelistRequest.Operation.DELETE, "DomesticCat", null, null, 2), "admin");
    assertNull(restarted.resolve("taxonomy.species", "DomesticCat"));
    var mapper = org.integratedmodelling.common.utils.Utils.Json.newObjectMapper();
    var response = mapper.readValue(mapper.writeValueAsBytes(restarted.snapshot()), AuthorityCodelistResponse.class);
    assertEquals(restarted.snapshot().policies(), response.policies());
  }

  @Test void legacyDeclarationsRemainProposalEnabledAndPolicyChangesPreserveHistory() {
    var binding = binding();
    var store = new AuthorityCodelists(binding, directory);
    var policy = store.snapshot().policies().get("taxonomy.species");
    assertTrue(policy.providerDeclared());
    assertTrue(policy.acceptsProposals());
    var pending = store.execute(submit(), "user");
    doReturn(Map.of("species", new Authority.CodelistDefinition(new CodelistImpl(), false)))
        .when(binding.provider()).getCodelistDefinitions("configured");
    var closed = new AuthorityCodelists(binding, directory);
    assertEquals(pending.proposals(), closed.snapshot().proposals());
    assertThrows(IllegalArgumentException.class, () -> closed.execute(submit(), "user"));
    closed.execute(new AuthorityCodelistRequest(AuthorityCodelistRequest.Operation.REVIEW, "TAXA",
        "taxonomy.species", null, null, pending.proposals().getFirst().id(), pending.revision(),
        AuthorityCodelistRequest.Decision.REJECT, null, "List is now closed", null), "admin");
    assertEquals(AuthorityCodelistResponse.Status.REJECTED, closed.snapshot().proposals().getFirst().status());
  }
  @Test void reviewPersistsAndNeverPublishesPendingProposals() {
    var binding = binding(); var store = new AuthorityCodelists(binding, directory);
    assertEquals(0, store.snapshot().codelists().get("taxonomy.species").size());
    var pending = store.execute(submit(), "user");
    assertNull(store.resolve("taxonomy.species", "FelisCatus"));
    assertEquals(pending.revision(), store.execute(submit(), "collaborator").revision());
    String id = pending.proposals().getFirst().id();
    var accept = new AuthorityCodelistRequest(AuthorityCodelistRequest.Operation.REVIEW, "TAXA", "taxonomy.species",
        null, null, id, pending.revision(), AuthorityCodelistRequest.Decision.ACCEPT, "DomesticCat", "Preferred name", null);
    var result = store.execute(accept, "admin");
    assertEquals(AuthorityCodelistResponse.Status.REDIRECTED, result.proposals().getFirst().status());
    assertEquals("3DXV3", store.resolve("taxonomy.species", "DomesticCat"));
    assertNull(store.resolve("taxonomy.species", "FelisCatus"));
    assertThrows(ConcurrentModificationException.class, () -> store.execute(accept, "another-admin"));
    var restarted = new AuthorityCodelists(binding, directory);
    assertEquals("3DXV3", restarted.resolve("taxonomy.species", "DomesticCat"));
    assertThrows(IllegalArgumentException.class, () -> restarted.execute(
        managed(AuthorityCodelistRequest.Operation.DELETE, "DomesticCat", null, null, result.revision()), "admin"));
    assertThrows(IllegalArgumentException.class, () -> restarted.execute(
        managed(AuthorityCodelistRequest.Operation.UPDATE, "DomesticCat", "Cat", "3DXV3", result.revision()), "admin"));
    assertEquals("3DXV3", restarted.resolve("taxonomy.species", "DomesticCat"));
  }

  AuthorityCodelistRequest managed(AuthorityCodelistRequest.Operation operation, String alias,
      String renamed, String identity, long revision) {
    return new AuthorityCodelistRequest(operation, "TAXA", "taxonomy.species", alias, identity,
        null, revision, null, renamed, null, null);
  }

  @Test void directManagementIsAtomicPersistentAndRevisionProtected() {
    var binding = binding();
    var store = new AuthorityCodelists(binding, directory);
    var created = store.execute(managed(AuthorityCodelistRequest.Operation.CREATE, "Cat", null, "3DXV3", 0), "admin");
    assertEquals(AuthorityCodelistResponse.Status.MANAGED, created.proposals().getFirst().status());
    assertEquals("3DXV3", store.resolve("taxonomy.species", "Cat"));
    assertThrows(ConcurrentModificationException.class, () -> store.execute(
        managed(AuthorityCodelistRequest.Operation.UPDATE, "Cat", "DomesticCat", "3DXV3", 0), "admin"));
    assertThrows(IllegalArgumentException.class, () -> store.execute(
        managed(AuthorityCodelistRequest.Operation.UPDATE, "Cat", "DomesticCat", "invalid", 1), "admin"));
    assertEquals(1, store.revision());
    store.execute(managed(AuthorityCodelistRequest.Operation.UPDATE, "Cat", "DomesticCat", "3DXV3", 1), "admin");
    var restarted = new AuthorityCodelists(binding, directory);
    assertNull(restarted.resolve("taxonomy.species", "Cat"));
    assertEquals("3DXV3", restarted.resolve("taxonomy.species", "DomesticCat"));
    restarted.execute(managed(AuthorityCodelistRequest.Operation.DELETE, "DomesticCat", null, null, 2), "admin");
    assertNull(new AuthorityCodelists(binding, directory).resolve("taxonomy.species", "DomesticCat"));
  }

  @Test void communityAdoptionLocksDirectlyManagedEntry() {
    var store = new AuthorityCodelists(binding(), null);
    store.execute(managed(AuthorityCodelistRequest.Operation.CREATE, "FelisCatus", null, "3DXV3", 0), "admin");
    var pending = store.execute(submit(), "user");
    var proposal = pending.proposals().stream().filter(p -> p.status() == AuthorityCodelistResponse.Status.PENDING).findFirst().orElseThrow();
    store.execute(new AuthorityCodelistRequest(AuthorityCodelistRequest.Operation.REVIEW, "TAXA", "taxonomy.species",
        null, null, proposal.id(), pending.revision(), AuthorityCodelistRequest.Decision.ACCEPT, null, null, null), "admin");
    assertThrows(IllegalArgumentException.class, () -> store.execute(
        managed(AuthorityCodelistRequest.Operation.DELETE, "FelisCatus", null, null, store.revision()), "admin"));
    assertThrows(IllegalArgumentException.class, () -> store.execute(
        managed(AuthorityCodelistRequest.Operation.CREATE, "FelisCatus", null, "3DXV3", store.revision()), "admin"));
  }

  @Test void proposalRevisionsTriggerServiceStatusChanges() {
    var before = new org.integratedmodelling.klab.api.services.impl.ServiceStatusImpl();
    var after = new org.integratedmodelling.klab.api.services.impl.ServiceStatusImpl();
    before.getMetadata().put("authority.codelist.revisions", Map.of("TAXA", 1L));
    after.getMetadata().put("authority.codelist.revisions", Map.of("TAXA", 2L));
    assertTrue(after.hasChangedComparedTo(before));
    assertFalse(before.hasChangedComparedTo(before));
  }

  @Test void searchFiltersListsAndAlwaysDecoratesCanonicalMatches() {
    var binding = binding(); var provider = binding.provider();
    when(provider.configure(AuthorityBindings.providerRequest(binding.request()))).thenReturn("configured");
    var capabilities = mock(Authority.Capabilities.class); when(capabilities.isSearchable()).thenReturn(true);
    when(provider.getCapabilities()).thenReturn(capabilities);
    var identity = provider.resolveIdentity("configured", "3DXV3");
    when(provider.search(anyString(), isNull(), eq("configured"))).thenReturn(List.of(identity));
    var bindings = new AuthorityBindings(); bindings.configure(binding.request(), provider);
    var lists = bindings.codelists("TAXA"); var pending = lists.execute(submit(), "user");
    lists.execute(new AuthorityCodelistRequest(AuthorityCodelistRequest.Operation.REVIEW, "TAXA", "taxonomy.species",
        null, null, pending.proposals().getFirst().id(), pending.revision(), AuthorityCodelistRequest.Decision.ACCEPT,
        null, null, null), "admin");
    var all = bindings.search(new AuthoritySearchRequest("TAXA", "3DXV3", null, 0, 10));
    assertEquals(1, all.matches().size());
    assertEquals(List.of("taxonomy.species:FelisCatus"), all.matches().getFirst().getAliases());
    clearInvocations(provider);
    var filtered = bindings.search(new AuthoritySearchRequest("TAXA", "FelisCatus", "taxonomy.species", 0, 10));
    assertEquals("3DXV3", filtered.matches().getFirst().getId());
    verify(provider, never()).search(anyString(), any(), anyString());
    assertTrue(bindings.search(new AuthoritySearchRequest("TAXA", "dog", "taxonomy.species", 0, 10)).matches().isEmpty());
  }

  @Test void standardJacksonPreservesStringAndLegacyConceptValues() throws Exception {
    var mapper = org.integratedmodelling.common.utils.Utils.Json.newObjectMapper();
    var list = new CodelistImpl(); list.setAuthorityId("TAXA");
    list.getEntries().add(new CodelistImpl.Entry("TAXA", "FelisCatus", "3DXV3", "Cat", true));
    var concept = new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl();
    concept.setName("life:Individual");
    list.getEntries().add(new CodelistImpl.Entry("LEGACY", 42L, concept, "Legacy", true));
    var response = new AuthorityCodelistResponse(1, Map.of("taxonomy.species", list), List.of());
    var copy = mapper.readValue(mapper.writeValueAsBytes(response), AuthorityCodelistResponse.class);
    var recovered = copy.codelists().get("taxonomy.species");
    assertEquals("3DXV3", recovered.value("FelisCatus"));
    assertInstanceOf(org.integratedmodelling.klab.api.lang.kim.KimConcept.class, recovered.value(42L));
    assertEquals(recovered.value(42L), recovered.value(42));
    assertNull(recovered.value(42.5));
    var legacy = mapper.valueToTree(response);
    ((com.fasterxml.jackson.databind.node.ObjectNode) legacy).remove("policies");
    assertTrue(mapper.treeToValue(legacy, AuthorityCodelistResponse.class).policies().isEmpty());
  }
}
