package org.integratedmodelling.klab.services.runtime.digitaltwin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import com.google.common.cache.CacheBuilder;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.impl.ActivityImpl;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.services.runtime.MemberClassifierExecutor.PendingAttribution;
import org.integratedmodelling.klab.services.runtime.neo4j.KnowledgeGraphNeo4j;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.*;

class ClassificationTransactionTest {
  DigitalTwinImpl twin;
  KnowledgeGraphNeo4j kg;
  KnowledgeGraph.Transaction storage;
  ServiceContextScope scope;
  Reasoner reasoner;
  DigitalTwinImpl.TransactionImpl root;
  ActivityImpl activity;
  DigitalTwinImpl.TransactionImpl child;

  @BeforeEach void setup() throws Exception {
    twin = mock(DigitalTwinImpl.class);
    kg = mock(KnowledgeGraphNeo4j.class);
    storage = mock(KnowledgeGraph.Transaction.class);
    scope = mock(ServiceContextScope.class);
    reasoner = mock(Reasoner.class);
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    when(reasoner.satisfiable(any())).thenReturn(true);
    var user = mock(UserIdentity.class);
    when(user.getUsername()).thenReturn("tester");
    when(scope.getUser()).thenReturn(user);
    when(scope.getId()).thenReturn("context");
    when(kg.nextKey()).thenReturn(900L);
    when(kg.createTransaction(scope)).thenReturn(storage);
    field("knowledgeGraph", kg);
    field("commitCache", CacheBuilder.newBuilder().build());
    var nextId = new java.util.concurrent.atomic.AtomicLong(1000);
    doAnswer(call -> {
      var asset = call.getArgument(0);
      if (asset instanceof ActivityImpl a) a.setId(nextId.incrementAndGet());
      if (asset instanceof ObservationImpl o) { o.setId(nextId.incrementAndGet()); o.setUrn("context." + o.getId()); }
      return null;
    }).when(storage).store(any());
    root = twin.new TransactionImpl(Activity.of(Activity.Type.SUBMISSION), scope, RuntimeAsset.PROVENANCE_ASSET);
    activity = Activity.of(Activity.Type.CLASSIFICATION, root.getActivity());
    child = (DigitalTwinImpl.TransactionImpl) root.getChild(activity, scope);
  }

  void field(String name, Object value) throws Exception {
    var field = DigitalTwinImpl.class.getDeclaredField(name); field.setAccessible(true); field.set(twin, value);
  }

  PendingAttribution pending(long id, boolean role) {
    var before = mock(Observable.class); when(before.getUrn()).thenReturn("test:Region" + id);
    var after = mock(Observable.class); when(after.getUrn()).thenReturn("test:ClassifiedRegion" + id);
    when(after.getSemantics()).thenReturn(mock(Concept.class));
    var builder = mock(Observable.Builder.class);
    when(before.builder(scope)).thenReturn(builder);
    when(builder.buildObservable()).thenReturn(after);
    var predicate = mock(Concept.class);
    when(predicate.is(role ? SemanticType.ROLE : SemanticType.TRAIT)).thenReturn(true);
    when(predicate.getUrn()).thenReturn("test:Concrete");
    var family = mock(Concept.class); when(family.getUrn()).thenReturn("test:Abstract");
    var member = new ObservationImpl(); member.setId(id); member.setUrn("member:" + id);
    member.setName("Member"); member.setObservable(before); member.setGeometry(Geometry.create("S2"));
    member.setParentId(77); member.setParentTransientId(88);
    return new PendingAttribution(member, before, family, predicate, Geometry.create("S2"), Scheduler.Event.initialization());
  }

  @Test void stagesDetachedTraitsAndRolesWithTypedProvenance() {
    for (boolean role : List.of(false, true)) {
      var pending = pending(role ? 42 : 43, role);
      child.stageAttributions(List.of(pending), scope);
      var staged = (Observation) root.assets().stream().filter(a -> a.getId() == pending.member().getId()).findFirst().orElseThrow();
      assertNotSame(pending.member(), staged);
      assertSame(pending.originalObservable(), pending.member().getObservable());
      assertEquals(77, staged.getParentId()); assertEquals(88, staged.getParentTransientId());
      assertSame(pending.member().getGeometry(), staged.getGeometry());
      var link = child.outgoing(activity).stream().filter(l -> l.target().getId() == staged.getId()).findFirst().orElseThrow();
      assertEquals(GraphModel.Relationship.CLASSIFIED, link.type());
      assertEquals(pending.originalObservable().getUrn(), link.properties().get("before"));
      assertEquals(staged.getObservable().getUrn(), link.properties().get("after"));
      assertFalse(child.outgoing(activity).stream().anyMatch(l -> l.type() == GraphModel.Relationship.CREATED));
      if (role) verify(pending.originalObservable().builder(scope)).withRole(pending.predicate());
      else verify(pending.originalObservable().builder(scope)).withTrait(pending.predicate());
    }
  }

  @Test void invalidLaterMemberPublishesNoStagedBatchAndFailurePoisonsRoot() {
    var first = pending(41, false); var invalid = pending(42, false);
    when(invalid.originalObservable().builder(scope).buildObservable()).thenReturn(null);
    assertThrows(IllegalArgumentException.class, () -> child.stageAttributions(List.of(first, invalid), scope));
    assertFalse(root.assets().contains(first.member()));
    child.fail(null);
    assertEquals(-1, root.commit());
    verifyNoInteractions(storage);
    assertSame(first.originalObservable(), first.member().getObservable());
  }

  @Test void rollbackAfterStagingRestoresTransactionViews() {
    var value = pending(41, false);
    child.stageAttributions(List.of(value), scope);
    child.fail(new IllegalStateException("later operation failed"));
    assertTrue(root.assets().contains(value.member()));
    assertSame(value.originalObservable(), value.member().getObservable());
    assertEquals(-1, root.commit());
    verifyNoInteractions(storage);
  }

  @Test void commitPublishesExistingAndNewMembersOnlyAfterStorageCloses() throws Exception {
    var existing = pending(41, false); var created = pending(-2, false);
    root.add(created.member());
    child.stageAttributions(List.of(existing, created), scope);
    doAnswer(call -> {
      assertSame(existing.originalObservable(), existing.member().getObservable());
      assertSame(created.originalObservable(), created.member().getObservable());
      return null;
    }).when(storage).close();
    assertEquals(900, root.commit());
    assertNotSame(existing.originalObservable(), existing.member().getObservable());
    assertEquals(41, existing.member().getId());
    assertTrue(created.member().getId() > 0);
    var before = existing.originalObservable().getUrn();
    verify(storage).updateSemantics(any(Observation.class), eq(before));
    verify(scope).invalidateObservation(41);
    var commit = DigitalTwinImpl.TransactionImpl.createCommit(900, "tester", List.of(created.member()),
        List.of(existing.member()), List.of());
    assertEquals(Set.of(41L), commit.getModifiedAssets());
    assertEquals(Set.of(created.member().getId()), commit.getAddedObservations());
  }

  @Test void storageFailureRollsBackBeforeCloseAndNeverPublishes() throws Exception {
    var value = pending(41, false);
    child.stageAttributions(List.of(value), scope);
    doThrow(new IllegalStateException("conflict")).when(storage).updateSemantics(any(), any());
    assertEquals(-1, root.commit());
    assertSame(value.originalObservable(), value.member().getObservable());
    var order = inOrder(storage); order.verify(storage).fail(any()); order.verify(storage).close();
    verify(scope, never()).invalidateObservation(anyLong());
  }

  @Test void duplicateStagingIsRejectedAndEmptyBatchDoesNothing() {
    var value = pending(41, false);
    child.stageAttributions(List.of(), scope);
    assertTrue(child.outgoing(activity).isEmpty());
    child.stageAttributions(List.of(value), scope);
    assertThrows(IllegalStateException.class, () -> child.stageAttributions(List.of(value), scope));
    assertSame(value.originalObservable(), value.member().getObservable());
  }
}
