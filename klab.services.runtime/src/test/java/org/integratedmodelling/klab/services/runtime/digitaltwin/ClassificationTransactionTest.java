package org.integratedmodelling.klab.services.runtime.digitaltwin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import com.google.common.cache.CacheBuilder;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
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
  @Test void semanticReasonerCallbackCanReadTheSameTransactionGraph() throws Exception {
    semanticCallback(false);
  }
  @Test void projectedSnapshotSemanticCallbackCanReadTheSameTransactionGraph() throws Exception {
    semanticCallback(true);
  }
  private void semanticCallback(boolean projected) throws Exception {
    var initial=pending(41,false);
    var meaning=new org.integratedmodelling.common.knowledge.ConceptImpl();
    meaning.setUrn("test:Region41");
    meaning.setType(EnumSet.of(SemanticType.SUBJECT,SemanticType.OBSERVABLE));
    var before=new org.integratedmodelling.common.knowledge.ObservableImpl();
    before.setUrn(meaning.getUrn());before.setSemantics(meaning);
    ((ObservationImpl)initial.member()).setObservable(before);
    var value=new PendingAttribution(initial.member(),before,initial.abstractPredicate(),initial.predicate(),
        initial.support(),initial.event());
    root.add(value.member());
    var transactionField=ServiceContextScope.class.getDeclaredField("currentTransaction");
    transactionField.setAccessible(true);transactionField.set(scope,root);
    doCallRealMethod().when(scope).getObservation(41L);
    var after=new org.integratedmodelling.common.knowledge.ObservableImpl(before);
    after.setUrn("test:ClassifiedRegion41");
    var readCompleted=new java.util.concurrent.atomic.AtomicBoolean();
    var cohort=new org.integratedmodelling.common.knowledge.CohortImpl();cohort.setId(10);cohort.setObservable(before);
    var producer=pending(90,false).member();
    when(producer.getObservable().getContextualization()).thenReturn(Contextualization.INSTANTIATION);
    List<String> expected;
    if(projected) {
      root.link(cohort,value.member(),GraphModel.Relationship.HAS_MEMBER);root.add(producer);
      expected=child.classificationOverlay(cohort,producer).state();
    } else expected=List.of();
    try(var callback=java.util.concurrent.Executors.newSingleThreadExecutor()) {
      when(reasoner.buildObservable(any(ObservableBuildStrategy.class),eq(scope))).thenAnswer(call->{
        // This is the actual ObservableBuildStrategy -> Reasoner API boundary. Model the
        // Reasoner's Runtime callback on a different HTTP thread. Runtime scope reconstruction
        // resolves a transaction-local focus through this actual getObservation -> assets path.
        var response=callback.submit(()->{
          var snapshot=scope.getObservation(value.member().getId());
          assertEquals(41,snapshot.getId());
          readCompleted.set(true);
          return snapshot;
        });
        try {response.get(2,java.util.concurrent.TimeUnit.SECONDS);}
        catch(java.util.concurrent.TimeoutException blocked) {
          throw new IllegalStateException("Semantic Runtime callback is blocked by the attribution graph monitor",blocked);
        }
        return after;
      });
      assertDoesNotThrow(()->{
        if(projected) child.stageClassificationSnapshot(List.of(value),scope,cohort,producer,List.of(),expected);
        else child.stageAttributions(List.of(value),scope);
      });
      assertTrue(readCompleted.get());
      assertEquals(after.getUrn(),root.readObservation(value.member()).getObservable().getUrn());
      verify(reasoner).buildObservable(any(ObservableBuildStrategy.class),eq(scope));
    }
  }

  @Test void graphMutationDuringSemanticPreparationRejectsTheWholeBatch() throws Exception {
    var value=pending(41,false);root.add(value.member());
    var after=value.originalObservable().builder(scope).buildObservable();
    try(var callback=java.util.concurrent.Executors.newSingleThreadExecutor()) {
      when(value.originalObservable().builder(scope).buildObservable()).thenAnswer(call->{
        callback.submit(()->root.add(pending(42,false).member())).get(2,java.util.concurrent.TimeUnit.SECONDS);
        return after;
      });
      var failure=assertThrows(IllegalStateException.class,()->child.stageAttributions(List.of(value),scope));
      assertTrue(failure.getMessage().contains("graph changed"));
      assertUnstagedFailure();
    }
  }
  @Test void unjournaledMemberMutationDuringPreparationRejectsWithoutPartialStaging() throws Exception {
    var first=pending(41,false);var second=pending(42,false);
    root.add(first.member());root.add(second.member());
    var after=first.originalObservable().builder(scope).buildObservable();
    try(var callback=java.util.concurrent.Executors.newSingleThreadExecutor()) {
      when(first.originalObservable().builder(scope).buildObservable()).thenAnswer(call->{
        callback.submit(()->second.member().getMetadata().put("external","changed"))
            .get(2,java.util.concurrent.TimeUnit.SECONDS);
        return after;
      });
      var failure=assertThrows(IllegalStateException.class,()->child.stageAttributions(List.of(first,second),scope));
      assertTrue(failure.getMessage().contains("input changed"));
      assertEquals("changed",second.member().getMetadata().get("external"));
      assertUnstagedFailure();
    }
  }
  @Test void concurrentCommitCannotPublishWhileSemanticPreparationIsInFlight() throws Exception {
    var value=pending(41,false);root.add(value.member());
    var after=value.originalObservable().builder(scope).buildObservable();
    var receipt=new java.util.concurrent.atomic.AtomicLong();
    try(var callback=java.util.concurrent.Executors.newSingleThreadExecutor()) {
      when(value.originalObservable().builder(scope).buildObservable()).thenAnswer(call->{
        receipt.set(callback.submit(()->root.commit()).get(2,java.util.concurrent.TimeUnit.SECONDS));
        return after;
      });
      assertThrows(IllegalStateException.class,()->child.stageAttributions(List.of(value),scope));
      assertEquals(-1,receipt.get());assertUnstagedFailure();
    }
  }
  @Test void cancellationDuringSemanticPreparationCannotStageResults() throws Exception {
    var value=pending(41,false);root.add(value.member());
    var after=value.originalObservable().builder(scope).buildObservable();
    var cancelled=new java.util.concurrent.atomic.AtomicBoolean();
    when(scope.isInterrupted()).thenAnswer(call->cancelled.get());
    when(value.originalObservable().builder(scope).buildObservable()).thenAnswer(call->{cancelled.set(true);return after;});
    assertThrows(java.util.concurrent.CancellationException.class,()->child.stageAttributions(List.of(value),scope));
    assertUnstagedFailure();
  }
  @Test void semanticPreparationUsesDetachedInputsEvenWhenOriginalsChangeAndRestore() {
    var initial=pending(41,false);
    var meaning=new org.integratedmodelling.common.knowledge.ConceptImpl();
    meaning.setUrn("test:Region41");meaning.setType(EnumSet.of(SemanticType.SUBJECT));
    var before=new org.integratedmodelling.common.knowledge.ObservableImpl();
    before.setUrn(meaning.getUrn());before.setSemantics(meaning);
    ((ObservationImpl)initial.member()).setObservable(before);
    var family=new org.integratedmodelling.common.knowledge.ConceptImpl();family.setUrn("test:Family");
    var predicate=new org.integratedmodelling.common.knowledge.ConceptImpl();
    predicate.setUrn("test:Concrete");predicate.setType(EnumSet.of(SemanticType.PREDICATE));
    var value=new PendingAttribution(initial.member(),before,family,predicate,initial.support(),initial.event());
    root.add(value.member());
    var after=new org.integratedmodelling.common.knowledge.ObservableImpl(before);after.setUrn("test:After");
    var existing=new org.integratedmodelling.common.knowledge.ConceptImpl();existing.setUrn("test:Unrelated");
    when(reasoner.directTraits(any())).thenAnswer(call->{
      meaning.setUrn("test:ChangedBase");family.setUrn("test:ChangedFamily");
      predicate.setUrn("test:ChangedResult");predicate.setType(EnumSet.of(SemanticType.ROLE));
      return List.of(existing);
    });
    when(reasoner.is(any(Concept.class),any(Concept.class))).thenAnswer(call->{
      Concept capturedFamily=call.getArgument(1);
      assertNotSame(family,capturedFamily);assertEquals("test:Family",capturedFamily.getUrn());
      return false;
    });
    when(reasoner.buildObservable(any(ObservableBuildStrategy.class),eq(scope))).thenAnswer(call->{
      ObservableBuildStrategy strategy=call.getArgument(0);
      assertNotSame(before,strategy.getBaseObservable());
      assertNotSame(meaning,strategy.getBaseObservable().getSemantics());
      assertEquals("test:Region41",strategy.getBaseObservable().getSemantics().getUrn());
      var operation=strategy.getOperations().stream()
          .filter(op->op.getType()==ObservableBuildStrategy.OperationType.WITH_TRAITS).findFirst().orElseThrow();
      var captured=operation.getConcepts().getFirst();
      assertNotSame(predicate,captured);assertEquals("test:Concrete",captured.getUrn());
      assertTrue(captured.is(SemanticType.PREDICATE));assertFalse(captured.is(SemanticType.ROLE));
      // A before/after fingerprint alone would miss this ABA mutation. Computation must use
      // the captured values throughout, even though the caller restores its values before apply.
      meaning.setUrn("test:Region41");family.setUrn("test:Family");
      predicate.setUrn("test:Concrete");predicate.setType(EnumSet.of(SemanticType.PREDICATE));
      return after;
    });
    assertDoesNotThrow(()->child.stageAttributions(List.of(value),scope));
    assertEquals("test:After",root.readObservation(value.member()).getObservable().getUrn());
    var evidence=(List<Map<String,Object>>)activity.getMetadata().get(Metadata.IM_ATTRIBUTIONS);
    assertEquals("test:Family",evidence.getFirst().get("abstractPredicate"));
    assertEquals("test:Concrete",evidence.getFirst().get("predicate"));
  }
  @Test void requestAliasCannotRedirectItsMemberDuringSemanticPreparation() {
    var canonical=pending(41,false);root.add(canonical.member());
    var alias=new ObservationImpl();alias.setId(41);alias.setUrn("alias:41");
    alias.setObservable(canonical.originalObservable());alias.setGeometry(canonical.member().getGeometry());
    var value=new PendingAttribution(alias,canonical.originalObservable(),canonical.abstractPredicate(),
        canonical.predicate(),canonical.support(),canonical.event());
    var after=value.originalObservable().builder(scope).buildObservable();
    when(value.originalObservable().builder(scope).buildObservable()).thenAnswer(call->{alias.setId(42);return after;});
    var failure=assertThrows(IllegalStateException.class,()->child.stageAttributions(List.of(value),scope));
    assertTrue(failure.getMessage().contains("request changed"));
    assertEquals(41,canonical.member().getId());assertEquals(42,alias.getId());assertUnstagedFailure();
  }
  @Test void staleReadViewCannotSelectARedirectedSourceBeforePreparation() {
    var original=pending(41,false);root.add(original.member());
    var view=root.readObservation(original.member());
    ((ObservationImpl)original.member()).setId(42);
    assertEquals(41,view.getId());assertEquals(42,original.member().getId());
    var value=new PendingAttribution(view,original.originalObservable(),original.abstractPredicate(),
        original.predicate(),original.support(),original.event());
    var failure=assertThrows(IllegalStateException.class,()->child.stageAttributions(List.of(value),scope));
    assertTrue(failure.getMessage().contains("association changed before preparation"));
    assertUnstagedFailure();verify(reasoner,never()).buildObservable(any(),any());
  }
  private void assertUnstagedFailure() {
    assertTrue(root.isFailed());
    assertTrue(child.outgoing(activity).stream().noneMatch(link->link.type()==GraphModel.Relationship.CLASSIFIED));
    assertNull(activity.getMetadata().get(Metadata.IM_ATTRIBUTIONS));
    assertEquals(-1,root.commit());verifyNoInteractions(storage);
  }

  @Test void parallelDescriptivePropertiesPersistWithDurableBearerAndDoNotBecomeWriterClaims() {
    var source = pending(41, false).member();
    var target = pending(42, false).member();
    var bearer = new ObservationImpl(); bearer.setId(-55); bearer.setObservable(source.getObservable());
    bearer.setGeometry(source.getGeometry());
    root.add(bearer);
    for (var kind : List.of(SemanticInfluence.Kind.INCREASES_WITH, SemanticInfluence.Kind.DECREASES_WITH))
      root.link(source, target, GraphModel.Relationship.AFFECTS, ProcessPlan.EDGE_ROLE, ProcessPlan.DESCRIPTIVE,
          "property", kind.property(), "semanticRelation", kind.name(), "provenance", "test:restriction",
          "bearerId", bearer);
    assertEquals(2, root.outgoing(source).stream().filter(l -> l.type() == GraphModel.Relationship.AFFECTS).count());
    assertTrue(root.commit() > 0);
    assertTrue(bearer.getId() > 0);
    for (var kind : List.of(SemanticInfluence.Kind.INCREASES_WITH, SemanticInfluence.Kind.DECREASES_WITH))
      verify(storage).link(eq(source), eq(target), eq(GraphModel.Relationship.AFFECTS),
          eq("sequence"), anyInt(), eq(ProcessPlan.EDGE_ROLE), eq(ProcessPlan.DESCRIPTIVE),
          eq("property"), eq(kind.property()), eq("semanticRelation"), eq(kind.name()),
          eq("provenance"), eq("test:restriction"), eq("bearerId"), eq(bearer.getId()));
  }
  @Test void storageBindingsRemainDistinctThroughTransactionPublicationAndJsonRecovery() throws Exception {
    var source = pending(41, false).member(); var target = pending(-2, false).member(); root.add(target);
    var from = new StorageScan.Semantics("test:elevation", "m", "", "", "");
    for (var unit : List.of("mm", "km")) {
      var to = new StorageScan.Semantics("test:elevation", unit, "", "", "");
      var binding = new StorageScan.Binding(1, unit + ":0", from, to,
          org.integratedmodelling.klab.runtime.storage.ValueMediation.compile(from, to, null));
      root.link(source, target, GraphModel.Relationship.AFFECTS, "rank", 0,
          ProcessPlan.EDGE_ROLE, ProcessPlan.PREREQUISITE, "readState", "CURRENT", "semanticRelations", List.of(),
          StorageScan.Binding.PROPERTY, org.integratedmodelling.klab.utilities.Utils.Json.asString(binding));
    }
    assertEquals(2, root.outgoing(source).stream().filter(link -> link.type() == GraphModel.Relationship.AFFECTS).count());
    assertTrue(root.commit() > 0); assertTrue(target.getId() > 0);
    var restored = new ArrayList<StorageScan.Binding>();
    for (var invocation : mockingDetails(storage).getInvocations()) {
      var args = invocation.getArguments();
      if (!invocation.getMethod().getName().equals("link") || args[2] != GraphModel.Relationship.AFFECTS) continue;
      var properties = new HashMap<String,Object>();
      for (int i=3; i<args.length; i+=2) properties.put(args[i].toString(),args[i+1]);
      assertEquals(0, properties.get("rank")); assertEquals("CURRENT", properties.get("readState"));
      assertEquals(ProcessPlan.PREREQUISITE, properties.get(ProcessPlan.EDGE_ROLE));
      var binding = org.integratedmodelling.klab.utilities.Utils.Json.parseObject(properties.get(StorageScan.Binding.PROPERTY).toString(), StorageScan.Binding.class);
      restored.add(binding);
      assertEquals(binding.conversion(), org.integratedmodelling.klab.runtime.storage.ValueMediation.compile(binding.source(),binding.target(),null));
    }
    assertEquals(Set.of("mm:0","km:0"), restored.stream().map(StorageScan.Binding::id).collect(java.util.stream.Collectors.toSet()));
  }

  @Test void failedConsumerPublishesNeitherMediationRelationshipNorConsumer() {
    var source = pending(41,false).member(); var target = pending(-2,false).member(); root.add(target);
    root.link(source,target,GraphModel.Relationship.AFFECTS,StorageScan.Binding.PROPERTY,"test binding");
    child.fail(new IllegalStateException("consumer failed"));
    assertEquals(-1,root.commit()); verifyNoInteractions(storage); assertEquals(-2,target.getId());
  }

  DigitalTwinImpl twin;
  KnowledgeGraphNeo4j kg;
  KnowledgeGraph.Transaction storage;
  ServiceContextScope scope;
  Reasoner reasoner;
  DigitalTwinImpl.TransactionImpl root;
  ActivityImpl activity;
  DigitalTwinImpl.TransactionImpl child;

  @Test void nativeReadGuardRunsOnRootCommitTransactionBeforeAnyWrites() {
    var receipt=new KnowledgeGraph.ClassificationReadSet("context",10,List.of());
    child.guardClassificationReadSet(receipt);
    assertEquals(900,root.commit());
    var order=inOrder(storage);order.verify(storage).validateClassificationMembers(receipt);order.verify(storage,atLeastOnce()).store(any());
    verify(kg,times(1)).createTransaction(scope);
  }
  @Test void staleReusedReadGuardAbortsRootBeforeAssetWrites() {
    var receipt=new KnowledgeGraph.ClassificationReadSet("context",10,List.of());
    root.guardClassificationReadSet(receipt);
    doThrow(new IllegalStateException("stale reused input")).when(storage).validateClassificationMembers(receipt);
    assertEquals(-1,root.commit());verify(storage,never()).store(any());assertTrue(root.isFailed());
  }
  @Test void newCohortOwnershipLinkPrecedesMembershipRegardlessOfJournalInsertionOrder() {
    var cohort=new org.integratedmodelling.common.knowledge.CohortImpl();cohort.setId(-8);
    cohort.setObservable(pending(41,false).member().getObservable());
    var member=pending(-2,false).member();root.link(cohort,member,GraphModel.Relationship.HAS_MEMBER);
    root.link(RuntimeAsset.CONTEXT_ASSET,cohort,GraphModel.Relationship.HAS_CHILD);
    var next=new java.util.concurrent.atomic.AtomicLong(2000);
    doAnswer(i->{var asset=i.getArgument(0);if(asset instanceof org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl o)o.setId(next.incrementAndGet());
      if(asset instanceof org.integratedmodelling.common.knowledge.CohortImpl c)c.setId(next.incrementAndGet());
      if(asset instanceof ActivityImpl a)a.setId(next.incrementAndGet());return null;}).when(storage).store(any());
    assertTrue(root.commit()>0);
    var links=mockingDetails(storage).getInvocations().stream().filter(call->call.getMethod().getName().equals("link")).toList();
    int owner=-1,membership=-1;
    for(int n=0;n<links.size();n++){var args=links.get(n).getArguments();if(args[0]==RuntimeAsset.CONTEXT_ASSET&&args[1]==cohort)owner=n;if(args[0]==cohort&&args[1]==member)membership=n;}
    assertTrue(owner>=0&&membership>owner);
  }
  @Test void rootLocksGlobalUnionAcrossReadSetsAndSemanticWritesBeforeValidation() {
    root.guardClassificationReadSet(new KnowledgeGraph.ClassificationReadSet("context",10,List.of(new KnowledgeGraph.ClassificationMemberSnapshot(100,"before","*",0))));
    root.guardClassificationReadSet(new KnowledgeGraph.ClassificationReadSet("context",11,List.of(new KnowledgeGraph.ClassificationMemberSnapshot(41,"before","*",0))));
    child.stageAttributions(List.of(pending(42,false)),scope);
    assertTrue(root.commit()>0);
    verify(storage).lockClassificationInputs(new java.util.TreeSet<>(List.of(41L,42L,100L)));
  }
  @BeforeEach
  void setup() throws Exception {
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
    doAnswer(
            call -> {
              var asset = call.getArgument(0);
              if (asset instanceof ActivityImpl a) a.setId(nextId.incrementAndGet());
              if (asset instanceof ObservationImpl o) {
                o.setId(nextId.incrementAndGet());
                o.setUrn("context." + o.getId());
              }
              return null;
            })
        .when(storage)
        .store(any());
    root =
        twin
        .new TransactionImpl(
            Activity.of(Activity.Type.SUBMISSION), scope, RuntimeAsset.PROVENANCE_ASSET);
    activity = Activity.of(Activity.Type.CLASSIFICATION, root.getActivity());
    child = (DigitalTwinImpl.TransactionImpl) root.getChild(activity, scope);
  }

  void field(String name, Object value) throws Exception {
    var field = DigitalTwinImpl.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(twin, value);
  }

  PendingAttribution pending(long id, boolean role) {
    var before = mock(Observable.class);
    when(before.getUrn()).thenReturn("test:Region" + id);
    var after = mock(Observable.class);
    when(after.getUrn()).thenReturn("test:ClassifiedRegion" + id);
    when(after.getSemantics()).thenReturn(mock(Concept.class));
    var builder = mock(Observable.Builder.class);
    when(before.builder(scope)).thenReturn(builder);
    when(builder.buildObservable()).thenReturn(after);
    var predicate = mock(Concept.class);
    when(predicate.is(role ? SemanticType.ROLE : SemanticType.PREDICATE)).thenReturn(true);
    when(predicate.getUrn()).thenReturn("test:Concrete");
    var family = mock(Concept.class);
    when(family.getUrn()).thenReturn("test:Abstract");
    var member = new ObservationImpl();
    member.setId(id);
    member.setUrn("member:" + id);
    member.setName("Member");
    member.setObservable(before);
    member.setGeometry(Geometry.create("S2"));
    member.setParentId(77);
    member.setParentTransientId(88);
    return new PendingAttribution(
        member, before, family, predicate, Geometry.create("S2"), Scheduler.Event.initialization());
  }

  @Test
  void stagesDetachedTraitsAndRolesWithTypedProvenance() {
    for (boolean role : List.of(false, true)) {
      var pending = pending(role ? 42 : 43, role);
      child.stageAttributions(List.of(pending), scope);
      var staged =
          (Observation)
              root.assets().stream()
                  .filter(a -> a.getId() == pending.member().getId())
                  .findFirst()
                  .orElseThrow();
      assertNotSame(pending.member(), staged);
      assertSame(pending.originalObservable(), pending.member().getObservable());
      assertEquals(77, staged.getParentId());
      assertEquals(88, staged.getParentTransientId());
      assertSame(pending.member().getGeometry(), staged.getGeometry());
      var link =
          child.outgoing(activity).stream()
              .filter(l -> l.target().getId() == staged.getId())
              .findFirst()
              .orElseThrow();
      assertEquals(GraphModel.Relationship.CLASSIFIED, link.type());
      assertEquals(pending.originalObservable().getUrn(), link.properties().get("before"));
      assertEquals(staged.getObservable().getUrn(), link.properties().get("after"));
      assertFalse(
          child.outgoing(activity).stream()
              .anyMatch(l -> l.type() == GraphModel.Relationship.CREATED));
      var captured=org.mockito.ArgumentCaptor.forClass(Concept.class);
      if (role) verify(pending.originalObservable().builder(scope)).withRole(captured.capture());
      else verify(pending.originalObservable().builder(scope)).withTrait(captured.capture());
      assertEquals(pending.predicate().getUrn(),captured.getValue().getUrn());
      assertNotSame(pending.predicate(),captured.getValue());
    }
  }

  @Test
  void invalidLaterMemberPublishesNoStagedBatchAndFailurePoisonsRoot() {
    var first = pending(41, false);
    var invalid = pending(42, false);
    when(invalid.originalObservable().builder(scope).buildObservable()).thenReturn(null);
    assertThrows(
        IllegalArgumentException.class,
        () -> child.stageAttributions(List.of(first, invalid), scope));
    assertFalse(root.assets().contains(first.member()));
    child.fail(null);
    assertEquals(-1, root.commit());
    verifyNoInteractions(storage);
    assertSame(first.originalObservable(), first.member().getObservable());
  }

  @Test
  void rollbackAfterStagingRestoresTransactionViews() {
    var value = pending(41, false);
    child.stageAttributions(List.of(value), scope);
    child.fail(new IllegalStateException("later operation failed"));
    assertTrue(root.assets().contains(value.member()));
    assertSame(value.originalObservable(), value.member().getObservable());
    assertEquals(-1, root.commit());
    verifyNoInteractions(storage);
  }

  @Test
  void commitPublishesExistingAndNewMembersOnlyAfterStorageCloses() throws Exception {
    var existing = pending(41, false);
    var created = pending(-2, false);
    root.add(created.member());
    child.stageAttributions(List.of(existing, created), scope);
    doAnswer(
            call -> {
              assertSame(existing.originalObservable(), existing.member().getObservable());
              assertSame(created.originalObservable(), created.member().getObservable());
              return null;
            })
        .when(storage)
        .close();
    assertEquals(900, root.commit());
    assertNotSame(existing.originalObservable(), existing.member().getObservable());
    assertEquals(41, existing.member().getId());
    assertTrue(created.member().getId() > 0);
    var before = existing.originalObservable().getUrn();
    verify(storage).updateSemantics(any(Observation.class), eq(before));
    verify(scope).invalidateObservation(41);
    var commit =
        DigitalTwinImpl.TransactionImpl.createCommit(
            900, "tester", List.of(created.member()), List.of(existing.member()), List.of());
    assertEquals(Set.of(41L), commit.getModifiedAssets());
    assertEquals(Set.of(created.member().getId()), commit.getAddedObservations());
  }

  @Test
  void storageFailureRollsBackBeforeCloseAndNeverPublishes() throws Exception {
    var value = pending(41, false);
    child.stageAttributions(List.of(value), scope);
    doThrow(new IllegalStateException("conflict")).when(storage).updateSemantics(any(), any());
    assertEquals(-1, root.commit());
    assertSame(value.originalObservable(), value.member().getObservable());
    var order = inOrder(storage);
    order.verify(storage).fail(any());
    order.verify(storage).close();
    verify(scope, never()).invalidateObservation(anyLong());
    clearInvocations(storage);
    assertEquals(-1, root.commit());
    verifyNoInteractions(storage);
  }

  PendingAttribution independent(PendingAttribution first, String familyName) {
    var family = mock(Concept.class);
    when(family.getUrn()).thenReturn(familyName);
    var predicate = mock(Concept.class);
    when(predicate.getUrn()).thenReturn(familyName + "Concrete");
    when(predicate.is(SemanticType.PREDICATE)).thenReturn(true);
    return new PendingAttribution(first.member(), first.originalObservable(), family, predicate,
        first.support(), first.event());
  }

  Observable composed(PendingAttribution first) {
    var initial = first.originalObservable().builder(scope).buildObservable();
    var builder = mock(Observable.Builder.class);
    var after = mock(Observable.class);
    when(after.getUrn()).thenReturn("test:BothFamilies");
    when(after.getSemantics()).thenReturn(mock(Concept.class));
    when(initial.builder(scope)).thenReturn(builder);
    when(builder.buildObservable()).thenReturn(after);
    return after;
  }

  @Test void independentFamiliesComposeAcrossChildrenAndPublishOnlyAtRoot() {
    var first = pending(41, false);
    var second = independent(first, "test:Other");
    var after = composed(first);
    child.stageAttributions(List.of(first), scope);
    assertEquals(DigitalTwin.Transaction.INTERMEDIATE_COMMIT_ID, child.commit());
    verifyNoInteractions(storage);
    var sibling = (DigitalTwinImpl.TransactionImpl) root.getChild(
        Activity.of(Activity.Type.CLASSIFICATION), scope);
    assertNotSame(root.readObservation(first.member()), sibling.readObservation(first.member()));
    assertEquals(root.readObservation(first.member()).getObservable().getUrn(),
        sibling.readObservation(first.member()).getObservable().getUrn());
    sibling.stageAttributions(List.of(second), scope);
    assertEquals(after.getUrn(), root.readObservation(first.member()).getObservable().getUrn());
    assertSame(first.originalObservable(), first.member().getObservable());
    assertEquals(DigitalTwin.Transaction.INTERMEDIATE_COMMIT_ID, sibling.commit());
    assertEquals(900, root.commit());
    assertSame(after, first.member().getObservable());
    var originalUrn = first.originalObservable().getUrn();
    verify(storage).updateSemantics(any(), eq(originalUrn));
  }

  @Test void independentFamiliesComposeWithinBatchAndRetainBothEvidenceEdges() {
    var first = pending(41, false);
    var after = composed(first);
    child.stageAttributions(List.of(first, independent(first, "test:Other")), scope);
    assertEquals(after.getUrn(), root.readObservation(first.member()).getObservable().getUrn());
    assertEquals(2, child.outgoing(activity).size());
    assertEquals(2, ((List<?>) activity.getMetadata().get(Metadata.IM_ATTRIBUTIONS)).size());
  }

  @Test void overlappingFamilyRejectsAndPoisonsWholeAttempt() {
    var first = pending(41, false);
    var second = independent(first, "test:SubtypeFamily");
    when(reasoner.is(argThat((Concept c)->c!=null&&Objects.equals(c.getUrn(),second.abstractPredicate().getUrn())),
        argThat((Concept c)->c!=null&&Objects.equals(c.getUrn(),first.abstractPredicate().getUrn())))).thenReturn(true);
    child.stageAttributions(List.of(first), scope);
    assertThrows(IllegalStateException.class, () -> child.stageAttributions(List.of(second), scope));
    assertEquals(-1, root.commit());
    verifyNoInteractions(storage);
  }

  @Test void cancellationDiscardsOnlyTentativeClassificationsAndCannotResume() {
    var first = pending(41, false);
    // This observable represents already committed knowledge, including any prior families.
    var committed = first.originalObservable();
    child.stageAttributions(List.of(first), scope);
    child.commit();
    root.fail(new java.util.concurrent.CancellationException("unresolved prerequisite"));
    assertSame(committed, first.member().getObservable());
    assertThrows(IllegalStateException.class, () -> root.readObservation(first.member()));
    assertThrows(IllegalStateException.class, () -> child.stageAttributions(List.of(), scope));
    assertEquals(-1, root.commit());
    verifyNoInteractions(storage);
  }

  @Test void laterAttemptRollbackPreservesEarlierCommittedClassification() {
    var first = pending(41, false);
    var after = composed(first);
    child.stageAttributions(List.of(first), scope);
    assertEquals(900, root.commit());
    var committed = first.member().getObservable();
    var nextRoot = twin.new TransactionImpl(Activity.of(Activity.Type.SUBMISSION), scope,
        RuntimeAsset.PROVENANCE_ASSET);
    var nextChild = (DigitalTwinImpl.TransactionImpl) nextRoot.getChild(
        Activity.of(Activity.Type.CLASSIFICATION), scope);
    var other = independent(first, "test:Other");
    var pending = new PendingAttribution(first.member(), committed, other.abstractPredicate(),
        other.predicate(), other.support(), other.event());
    clearInvocations(storage);
    nextChild.stageAttributions(List.of(pending), scope);
    assertEquals(after.getUrn(), nextRoot.readObservation(first.member()).getObservable().getUrn());
    nextChild.commit();
    nextRoot.fail(new IllegalStateException("parent unresolved; attempt terminated"));
    assertEquals(-1, nextRoot.commit());
    assertSame(committed, first.member().getObservable());
    verifyNoInteractions(storage);
  }

  @Test void concurrentCommittedSemanticChangeIsRejectedEvenForIndependentFamily() {
    var first = pending(41, false);
    child.stageAttributions(List.of(first), scope);
    var changed = mock(Observable.class);
    when(changed.getUrn()).thenReturn("test:Concurrent");
    ((ObservationImpl) first.member()).setObservable(changed);
    assertThrows(IllegalStateException.class,
        () -> child.stageAttributions(List.of(independent(first, "test:Other")), scope));
    assertEquals(-1, root.commit());
    assertSame(changed, first.member().getObservable());
    verifyNoInteractions(storage);
  }

  @Test void grandchildrenShareOnlyTheRootPublication() {
    var value = pending(41, false);
    var grandchild = (DigitalTwinImpl.TransactionImpl) child.getChild(
        Activity.of(Activity.Type.CLASSIFICATION), scope);
    grandchild.stageAttributions(List.of(value), scope);
    assertEquals(root.readObservation(value.member()).getObservable().getUrn(),
        child.readObservation(value.member()).getObservable().getUrn());
    assertEquals(DigitalTwin.Transaction.INTERMEDIATE_COMMIT_ID, grandchild.commit());
    assertEquals(DigitalTwin.Transaction.INTERMEDIATE_COMMIT_ID, child.commit());
    verifyNoInteractions(storage);
    assertEquals(900, root.commit());
    assertNotSame(value.originalObservable(), value.member().getObservable());
  }

  @Test void closeFreezesAttributionsAndRejectsLateStage() throws Exception {
    var first = pending(41, false);
    var second = independent(first, "test:Other");
    composed(first);
    var sibling = (DigitalTwinImpl.TransactionImpl) root.getChild(
        Activity.of(Activity.Type.CLASSIFICATION), scope);
    child.stageAttributions(List.of(first), scope);
    var closing = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    var requested = new java.util.concurrent.CountDownLatch(1);
    doAnswer(call -> { closing.countDown(); assertTrue(release.await(10, java.util.concurrent.TimeUnit.SECONDS)); return null; })
        .when(storage).close();
    try (var threads = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var commit = threads.submit(root::commit);
      assertTrue(closing.await(10, java.util.concurrent.TimeUnit.SECONDS));
      var late = threads.submit(() -> {
        requested.countDown();
        assertThrows(IllegalStateException.class, () -> sibling.stageAttributions(List.of(second), scope));
      });
      assertTrue(requested.await(10, java.util.concurrent.TimeUnit.SECONDS));
      release.countDown();
      assertEquals(900, commit.get(10, java.util.concurrent.TimeUnit.SECONDS));
      late.get(10, java.util.concurrent.TimeUnit.SECONDS);
    } finally { release.countDown(); }
    var persisted = org.mockito.ArgumentCaptor.forClass(Observation.class);
    verify(storage).updateSemantics(persisted.capture(), anyString());
    assertEquals("test:ClassifiedRegion41", persisted.getValue().getObservable().getUrn());
    assertEquals(persisted.getValue().getObservable().getUrn(), first.member().getObservable().getUrn());
    assertTrue(sibling.outgoing(sibling.getActivity()).isEmpty());
    assertEquals(Activity.Outcome.SUCCESS, root.getActivity().getOutcome());
  }

  @Test void cancellationAfterCommitOwnsCloseCannotRollBackPublishedState() throws Exception {
    var value = pending(41, false);
    child.stageAttributions(List.of(value), scope);
    var closing = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    var requested = new java.util.concurrent.CountDownLatch(1);
    doAnswer(call -> { closing.countDown(); assertTrue(release.await(10, java.util.concurrent.TimeUnit.SECONDS)); return null; })
        .when(storage).close();
    try (var threads = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var commit = threads.submit(root::commit);
      assertTrue(closing.await(10, java.util.concurrent.TimeUnit.SECONDS));
      var cancel = threads.submit(() -> {
        requested.countDown();
        return child.fail(new java.util.concurrent.CancellationException());
      });
      assertTrue(requested.await(10, java.util.concurrent.TimeUnit.SECONDS));
      release.countDown();
      assertEquals(900, commit.get(10, java.util.concurrent.TimeUnit.SECONDS));
      cancel.get(10, java.util.concurrent.TimeUnit.SECONDS);
    } finally { release.countDown(); }
    assertNotSame(value.originalObservable(), value.member().getObservable());
    assertEquals(Activity.Outcome.SUCCESS, root.getActivity().getOutcome());
    verify(storage, never()).fail(any());
    assertThrows(IllegalStateException.class, root::commit);
  }

  @Test void setupFailuresAreTerminalBeforeAnyGraphTransaction() {
    for (var setup : List.of("key", "observer", "transaction")) {
      var attempt = twin.new TransactionImpl(Activity.of(Activity.Type.SUBMISSION), scope,
          RuntimeAsset.PROVENANCE_ASSET);
      var classification = (DigitalTwinImpl.TransactionImpl) attempt.getChild(
          Activity.of(Activity.Type.CLASSIFICATION), scope);
      var failure = new IllegalStateException(setup);
      if (setup.equals("key")) when(kg.nextKey()).thenThrow(failure);
      else if (setup.equals("observer")) when(scope.getObserver()).thenThrow(failure);
      else when(kg.createTransaction(scope)).thenThrow(failure);
      assertEquals(-1, attempt.commit());
      assertEquals(-1, attempt.commit());
      assertThrows(IllegalStateException.class, () -> classification.stageAttributions(List.of(), scope));
      verifyNoInteractions(storage);
      // doReturn replaces throwing stubs without invoking them.
      doReturn(900L).when(kg).nextKey();
      doReturn(null).when(scope).getObserver();
      doReturn(storage).when(kg).createTransaction(scope);
    }
  }

  @Test void preparationFailureAlsoTerminatesAttempt() {
    var member = spy((ObservationImpl) pending(41, false).member());
    root.add(member);
    doThrow(new IllegalStateException("metadata preparation")).when(member).getMetadata();
    assertEquals(-1, root.commit());
    assertEquals(-1, root.commit());
    assertThrows(IllegalStateException.class, () -> child.stageAttributions(List.of(), scope));
    verifyNoInteractions(storage);
  }

  private void clearGeometryCaches() throws Exception {
    for (var name : List.of("cache", "mergeCache")) {
      var field = org.integratedmodelling.common.knowledge.GeometryRepository.class.getDeclaredField(name);
      field.setAccessible(true);
      ((com.github.benmanes.caffeine.cache.Cache<?, ?>) field.get(
          org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE)).invalidateAll();
    }
  }

  private Geometry polygonForPrivateRead() {
    return Geometry.create(org.integratedmodelling.klab.runtime.scale.space.ShapeImpl.create(
        "EPSG:4326 POLYGON ((7 7, 8 7, 8 8, 7 8, 7 7))").encode());
  }

  @Test void plainGeometryCacheHitCannotExposeSharedScale() throws Exception {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
    clearGeometryCaches();
    try {
      var repository = org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE;
      var member = (ObservationImpl) pending(41, false).member();
      member.setGeometry(polygonForPrivateRead());
      var shared = repository.scale(member.getGeometry());
      var view = root.readObservation(member).getGeometry();
      var privateScale = repository.scale(view);
      assertNotSame(shared, privateScale);
      privateScale.getMetadata().put("private", "read");
      assertFalse(shared.getMetadata().containsKey("private"));
      assertSame(shared, repository.scale(member.getGeometry()));
      assertNotSame(shared, repository.scale(privateScale.as(Geometry.class)));
      assertNotSame(shared, Scale.create(view));
      assertNotSame(shared, org.integratedmodelling.klab.api.Klab.INSTANCE.getConfiguration()
          .promoteGeometryToScale(view, null));
      assertInstanceOf(Geometry.LocalSnapshot.class, repository.geometry(privateScale));
      assertInstanceOf(Geometry.LocalSnapshot.class, privateScale.getSpace().as(Geometry.class));
      var sharedMerge = repository.getIntersection(member.getGeometry(), member.getGeometry(), Scale.class);
      var privateMerge = repository.getIntersection(view, member.getGeometry(), Scale.class);
      assertNotSame(sharedMerge, privateMerge);
      assertInstanceOf(Geometry.LocalSnapshot.class, privateMerge);
      privateMerge.getMetadata().put("private", "merge");
      assertFalse(sharedMerge.getMetadata().containsKey("private"));
      repository.put(view, privateScale);
      assertSame(shared, repository.scale(member.getGeometry()));
    } finally { clearGeometryCaches(); }
  }

  @Test void plainGeometryCacheMissCannotPoisonOriginalKey() throws Exception {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
    clearGeometryCaches();
    try {
      var repository = org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE;
      var member = (ObservationImpl) pending(41, false).member();
      var original = polygonForPrivateRead();
      member.setGeometry(original);
      var originalEncoding = original.encode();
      var view = root.readObservation(member).getGeometry();
      assertEquals(original.key(), view.key());
      var point = Geometry.create(org.integratedmodelling.klab.runtime.scale.space.ShapeImpl.create(
          "EPSG:4326 POINT (7.5 7.5)").encode());
      var parameters = view.dimension(Geometry.Dimension.Type.SPACE).getParameters();
      parameters.clear();
      parameters.putAll(point.dimension(Geometry.Dimension.Type.SPACE).getParameters());
      assertNotEquals(originalEncoding, view.encode());
      var privateScale = repository.scale(view);
      assertEquals(new org.integratedmodelling.klab.runtime.scale.ScaleImpl(point).encode(), privateScale.encode());
      assertEquals(new org.integratedmodelling.klab.runtime.scale.ScaleImpl(original).encode(),
          repository.scale(original).encode());
      assertEquals(originalEncoding, original.encode());
      assertEquals(Geometry.create(view.encode()).key(), view.key());
    } finally { clearGeometryCaches(); }
  }
  @Test void geometryReadsSupportScaleLookupConversionAndIsolation() {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
    var member = (ObservationImpl) pending(41, false).member();
    var original = member.getGeometry();
    var view = root.readObservation(member).getGeometry();
    assertNotSame(original, view);
    assertEquals(original.key(), view.key());
    assertNotNull(org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.scale(view));
    assertEquals(original.encode(), view.as(Geometry.class).encode());
    view.dimension(Geometry.Dimension.Type.SPACE).getParameters().put("test", "detached");
    assertFalse(original.dimension(Geometry.Dimension.Type.SPACE).getParameters().containsKey("test"));
    assertFalse(root.readObservation(member).getGeometry().dimension(Geometry.Dimension.Type.SPACE)
        .getParameters().containsKey("test"));
  }
  @Test @SuppressWarnings("unchecked") void geometrySnapshotsRetainMetadataCyclesEmptyAndScaleOperations() {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
    var member = (ObservationImpl) pending(41, false).member();
    for (var source : List.of(Geometry.create("X"), Geometry.create("1"), Geometry.create("*"),
        Geometry.create("S2"), Scale.create(Geometry.create(
            org.integratedmodelling.klab.runtime.scale.space.ShapeImpl.create(
                "EPSG:4326 POLYGON ((0 0, 2 0, 2 2, 0 2, 0 0))").encode())))) {
      member.setGeometry(source);
      var before = source.encode();
      var view = root.readObservation(member).getGeometry();
      assertNotSame(source, view);
      assertEquals(before, view.encode());
      assertEquals(source.isEmpty(), view.isEmpty());
      assertEquals(source.isScalar(), view.isScalar());
      assertEquals(source.isUniversal(), view.isUniversal());
      assertEquals(source.key(), view.key());
      assertNotNull(view.as(Geometry.class));
      view.getMetadata().put("private", new ArrayList<>(List.of("read")));
      assertFalse(source.getMetadata().containsKey("private"));
      if (source instanceof Scale) {
        var scale = assertInstanceOf(Scale.class, view);
        assertSame(source, org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE
            .scale(source.as(Geometry.class)), "A read must not publish its private scale to the geometry cache");
        assertSame(scale, org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.scale(view));
        assertNotSame(source, scale.as(Scale.class));
        assertEquals(before, scale.merge(Scale.create("*"),
            org.integratedmodelling.klab.api.lang.LogicalConnector.INTERSECTION).encode());
        assertNotNull(scale.without(Geometry.Dimension.Type.SPACE));
      }
      assertEquals(before, source.encode());
    }
    var geometry = Geometry.create("S2");
    geometry.getMetadata().put("parent", geometry);
    geometry.getMetadata().put("nested", new ArrayList<>(List.of("committed")));
    member.setGeometry(geometry);
    var view = root.readObservation(member).getGeometry();
    assertSame(view, view.getMetadata().get("parent"));
    ((List<String>) view.getMetadata().get("nested")).add("private");
    root.fail(new java.util.concurrent.CancellationException());
    assertEquals(List.of("committed"), geometry.getMetadata().get("nested"));
    assertSame(geometry, geometry.getMetadata().get("parent"));
  }
  @Test @SuppressWarnings("unchecked") void committedMemberReadIsDetachedBeforeAnyAttribution() {
    var member = (ObservationImpl) pending(41, false).member();
    member.getMetadata().put("nested", new ArrayList<>(List.of("committed")));
    member.setValue(new int[] { 1 });
    var read = root.readObservation(member);
    assertFalse(read instanceof ObservationImpl);
    ((List<String>) read.getMetadata().get("nested")).add("private");
    ((int[]) read.getValue())[0] = 9;
    assertEquals(List.of("committed"), member.getMetadata().get("nested"));
    assertEquals(1, ((int[]) member.getValue())[0]);
    assertEquals(List.of("committed"), root.readObservation(member).getMetadata().get("nested"));
    assertTrue(root.assets().stream().noneMatch(asset -> asset instanceof Observation));
  }

  @Test @SuppressWarnings("unchecked") void readPayloadsCannotMutateCommittedOrStagedState() {
    var value = pending(41, false);
    var member = (ObservationImpl) value.member();
    member.getMetadata().put("nested", new ArrayList<>(List.of("committed")));
    member.setValue(new int[] { 1, 2 });
    member.setEventTimestamps(new ArrayList<>(List.of(1L)));
    member.setParticipants(List.of(member));
    var histogram = new org.integratedmodelling.klab.api.data.impl.HistogramImpl();
    var bin = new org.integratedmodelling.klab.api.data.impl.HistogramImpl.BinImpl();
    bin.setCount(3); histogram.getBins().add(bin);
    member.setHistograms(Map.of(1L, histogram));
    var contextualization = new ObservationImpl.ContextualizationDataImpl();
    contextualization.getParameters().put("nested", new ArrayList<>(List.of("original")));
    ((org.integratedmodelling.klab.api.collections.impl.ParametersImpl<String>) contextualization.getParameters())
        .setUnnamedKeys(new ArrayList<>(List.of("nested")));
    member.getMetadata().put("urn", org.integratedmodelling.klab.api.knowledge.Urn.of("local:test:resource:item#key=value"));
    contextualization.setNativeShardingStrategy(Data.ShardingStrategy.neutral());
    member.setContextualizationData(contextualization);
    child.stageAttributions(List.of(value), scope);
    var read = root.readObservation(member);
    assertFalse(read instanceof ObservationImpl, "No mutable observation implementation escapes");
    var participant = read.getParticipants().getFirst();
    assertEquals(member.getId(), participant.getId());
    assertSame(participant, participant.getParticipants().getFirst(), "Participant cycles retain identity");
    assertEquals(member.getParentId(), read.getParentId());
    assertEquals(member.getParentTransientId(), read.getParentTransientId());
    ((List<String>) read.getMetadata().get("nested")).add("unvalidated");
    ((int[]) read.getValue())[0] = 9;
    read.getEventTimestamps().add(99L);
    read.getHistograms().get(1L).getBins().clear();
    ((List<String>) read.getContextualizationData().getParameters().get("nested")).add("unvalidated");
    assertEquals(List.of("nested"), read.getContextualizationData().getParameters().getUnnamedKeys());
    read.getContextualizationData().getParameters().getUnnamedKeys().clear();
    ((org.integratedmodelling.klab.api.knowledge.Urn) read.getMetadata().get("urn")).getParameters().clear();
    assertEquals("value", ((org.integratedmodelling.klab.api.knowledge.Urn)
        root.readObservation(member).getMetadata().get("urn")).getParameters().get("key"));
    assertEquals(List.of("nested"), root.readObservation(member).getContextualizationData().getParameters().getUnnamedKeys());
    read.getContextualizationData().getNativeShardingStrategy().setSuggestedSplits(99);
    assertEquals(List.of("committed"), root.readObservation(member).getMetadata().get("nested"));
    assertEquals(List.of(1L), root.readObservation(member).getEventTimestamps());
    assertEquals(1, root.readObservation(member).getHistograms().get(1L).getBins().size());
    root.fail(new java.util.concurrent.CancellationException());
    assertEquals(List.of("committed"), member.getMetadata().get("nested"));
    assertEquals(1, ((int[]) member.getValue())[0]);
    assertEquals(List.of(1L), member.getEventTimestamps());
    assertEquals(1, histogram.getBins().size());
    assertEquals(List.of("original"), contextualization.getParameters().get("nested"));
    assertEquals(-1, contextualization.getNativeShardingStrategy().getSuggestedSplits());
    assertSame(value.originalObservable(), member.getObservable());
  }

  @Test
  void duplicateStagingIsRejectedAndEmptyBatchDoesNothing() {
    var value = pending(41, false);
    child.stageAttributions(List.of(), scope);
    assertTrue(child.outgoing(activity).isEmpty());
    child.stageAttributions(List.of(value), scope);
    assertThrows(IllegalStateException.class, () -> child.stageAttributions(List.of(value), scope));
    assertSame(value.originalObservable(), value.member().getObservable());
  }
  private void guardMemberCounts(List<Observation> members) {
    var producer=pending(59,false).member();
    when(producer.getObservable().getContextualization()).thenReturn(Contextualization.INSTANTIATION);
    var cohort=new org.integratedmodelling.common.knowledge.CohortImpl();cohort.setId(10);cohort.setObservable(producer.getObservable());
    root.add(producer);members.forEach(root::add);
    root.guardClassificationOverlay(cohort,producer,members,root.classificationOverlay(cohort,producer,members).state());
  }
  private Observation attributionCountChain() {
    var first=pending(41,false);composed(first);child.stageAttributions(List.of(first),scope);
    child.link(first.member(),pending(43,false).member(),GraphModel.Relationship.HAS_CHILD);
    child.stageAttributions(List.of(independent(first,"test:Other")),scope);guardMemberCounts(List.of(first.member()));
    child.link(first.member(),pending(44,false).member(),GraphModel.Relationship.HAS_CHILD);
    assertEquals(2,root.readObservation(first.member()).getChildrenCount());return first.member();
  }
  @Test void successiveAttributionCopiesRetainOwnedCountsInNativeUpdate() {
    var member=attributionCountChain();assertTrue(root.commit()>0);assertEquals(2,member.getChildrenCount());
    verify(storage).updateSemantics(argThat(o->o.getId()==41&&o.getChildrenCount()==2),any(),eq(2));
  }
  @Test void cancelledAttributionCountChainRestoresTheOriginal() {
    var member=attributionCountChain();child.fail(new java.util.concurrent.CancellationException());
    assertEquals(0,member.getChildrenCount());assertEquals(-1,root.commit());verifyNoInteractions(storage);
  }
  @Test void freshAttributionKeepsOwnedChildCountsInTheGuardedAndStoredReplacement() {
    var value=pending(41,false);var member=value.member();child.stageAttributions(List.of(value),scope);guardMemberCounts(List.of(member));
    child.link(member,pending(43,false).member(),GraphModel.Relationship.HAS_CHILD);
    child.link(member,pending(44,false).member(),GraphModel.Relationship.HAS_CHILD);
    assertEquals(2,member.getChildrenCount());assertEquals(2,root.readObservation(member).getChildrenCount());
    assertTrue(root.commit()>0);
    verify(storage).updateSemantics(argThat(o->o.getId()==41&&o.getChildrenCount()==2),any(),eq(2));
  }
  @Test void freshAttributionDoesNotHideForeignCountMutationAfterOwnedLinks() {
    var value=pending(41,false);var member=value.member();child.stageAttributions(List.of(value),scope);guardMemberCounts(List.of(member));
    child.link(member,pending(43,false).member(),GraphModel.Relationship.HAS_CHILD);
    ((ObservationImpl)member).setChildrenCount(member.getChildrenCount()+4);
    assertEquals(-1,root.commit());assertEquals(4,member.getChildrenCount());verify(storage,never()).store(any());
  }
  @Test void freshAttributionStillRejectsForeignMetadataAfterOwnedLinks() {
    var value=pending(41,false);var member=value.member();child.stageAttributions(List.of(value),scope);guardMemberCounts(List.of(member));
    child.link(member,pending(43,false).member(),GraphModel.Relationship.HAS_CHILD);member.getMetadata().put("foreign-input",9);
    assertEquals(-1,root.commit());assertEquals(0,member.getChildrenCount());verify(storage,never()).store(any());
  }
  @Test void ownChildBookkeepingAfterCaptureIsProjectedAcrossChildTransactions() {
    var member=(ObservationImpl)pending(41,false).member();guardMemberCounts(List.of(member));
    child.link(member,pending(43,false).member(),GraphModel.Relationship.HAS_CHILD);
    assertEquals(1,member.getChildrenCount());assertTrue(root.commit()>0);
  }
  @Test void foreignCountMutationCannotUseTheOwnChildAllowance() {
    var member=(ObservationImpl)pending(41,false).member();guardMemberCounts(List.of(member));
    child.link(member,pending(43,false).member(),GraphModel.Relationship.HAS_CHILD);
    member.setChildrenCount(member.getChildrenCount()+4);
    assertEquals(-1,root.commit());verify(storage,never()).store(any());
    assertEquals(4,member.getChildrenCount(),"Rollback subtracts only this attempt's one increment");
  }
  @Test void foreignMetadataMutationStillFailsAfterOwnChildInsertion() {
    var member=(ObservationImpl)pending(41,false).member();guardMemberCounts(List.of(member));
    child.link(member,pending(43,false).member(),GraphModel.Relationship.HAS_CHILD);
    member.getMetadata().put("scientific-input",3);
    assertEquals(-1,root.commit());verify(storage,never()).store(any());assertEquals(0,member.getChildrenCount());
  }
  @Test void countAllowanceCannotMoveBetweenMembers() {
    var first=(ObservationImpl)pending(41,false).member();var second=(ObservationImpl)pending(42,false).member();
    guardMemberCounts(List.of(first,second));
    child.link(second,pending(43,false).member(),GraphModel.Relationship.HAS_CHILD);
    first.setChildrenCount(1);
    assertEquals(-1,root.commit());assertEquals(1,first.getChildrenCount());assertEquals(0,second.getChildrenCount());
  }
  @Test void cancelledAttemptUndoesOnlyItsChildBookkeeping() {
    var member=(ObservationImpl)pending(41,false).member();guardMemberCounts(List.of(member));
    child.link(member,pending(43,false).member(),GraphModel.Relationship.HAS_CHILD);
    child.fail(new java.util.concurrent.CancellationException());
    assertEquals(0,member.getChildrenCount());assertEquals(-1,root.commit());verifyNoInteractions(storage);
  }

  @Test void absoluteForeignCountResetStillRejectsAndRollbackCannotMakeCountNegative() {
    var member=(ObservationImpl)pending(41,false).member();guardMemberCounts(List.of(member));
    child.link(member,pending(43,false).member(),GraphModel.Relationship.HAS_CHILD);
    member.setChildrenCount(0);
    assertEquals(-1,root.commit());assertEquals(0,member.getChildrenCount());verify(storage,never()).store(any());
  }

  private static final String REGISTERED=org.integratedmodelling.klab.api.digitaltwin.Scheduler.REGISTRATION_METADATA_KEY;
  @Test void capturedConsumerMemberAllowsOnlyItsOwnedSchedulerInitialization() {
    var member=(ObservationImpl)pending(41,false).member();guardMemberCounts(List.of(member));
    child.appendSchedulerInitializationTimestamps(member,List.of(0L,1L));child.markSchedulerRegistered(member);
    assertTrue(root.commit()>0);assertEquals(List.of(0L,1L),member.getEventTimestamps());assertEquals(true,member.getMetadata().get(REGISTERED));
  }
  @Test void ownedSchedulerWritesAreScopedToEachGuardCursorAndSharedAcrossChildren() {
    var member=(ObservationImpl)pending(41,false).member();guardMemberCounts(List.of(member));
    child.appendSchedulerInitializationTimestamps(member,List.of(0L));child.markSchedulerRegistered(member);
    guardMemberCounts(List.of(member));
    child.appendSchedulerInitializationTimestamps(member,List.of(1L));child.markSchedulerRegistered(member);
    assertTrue(root.commit()>0);assertEquals(List.of(0L,1L),member.getEventTimestamps());
  }
  @Test void foreignRegistrationBeforeOwnedWriteIsNotAbsorbed() {
    var member=(ObservationImpl)pending(41,false).member();guardMemberCounts(List.of(member));
    member.getMetadata().put(REGISTERED,false);child.markSchedulerRegistered(member);
    assertEquals(-1,root.commit());verify(storage,never()).store(any());
  }
  @Test void foreignRegistrationAfterOwnedWriteStillFails() {
    var member=(ObservationImpl)pending(41,false).member();guardMemberCounts(List.of(member));
    child.markSchedulerRegistered(member);member.getMetadata().put(REGISTERED,false);
    assertEquals(-1,root.commit());verify(storage,never()).store(any());
  }
  @Test void foreignTimestampPrefixBeforeOwnedAppendStillFails() {
    var member=(ObservationImpl)pending(41,false).member();guardMemberCounts(List.of(member));
    member.setEventTimestamps(new ArrayList<>(List.of(99L)));child.appendSchedulerInitializationTimestamps(member,List.of(0L));
    assertEquals(-1,root.commit());verify(storage,never()).store(any());
  }
  @Test void foreignTimestampSuffixAfterOwnedAppendStillFails() {
    var member=(ObservationImpl)pending(41,false).member();guardMemberCounts(List.of(member));
    child.appendSchedulerInitializationTimestamps(member,List.of(0L));member.getEventTimestamps().add(99L);
    assertEquals(-1,root.commit());verify(storage,never()).store(any());
  }
  @Test void ownedSchedulerWritesDoNotMaskUnrelatedMetadata() {
    var member=(ObservationImpl)pending(41,false).member();guardMemberCounts(List.of(member));
    child.appendSchedulerInitializationTimestamps(member,List.of(0L));child.markSchedulerRegistered(member);
    member.getMetadata().put("foreign",true);assertEquals(-1,root.commit());verify(storage,never()).store(any());
  }
  @Test void schedulerOwnershipCannotMoveBetweenMembers() {
    var first=(ObservationImpl)pending(41,false).member();var second=(ObservationImpl)pending(42,false).member();guardMemberCounts(List.of(first,second));
    child.markSchedulerRegistered(first);second.getMetadata().put(REGISTERED,true);
    assertEquals(-1,root.commit());verify(storage,never()).store(any());
  }
  @Test void cancelledOwnedSchedulerInitializationRestoresOriginalState() {
    var member=(ObservationImpl)pending(41,false).member();guardMemberCounts(List.of(member));
    child.appendSchedulerInitializationTimestamps(member,List.of(0L));child.markSchedulerRegistered(member);
    child.fail(new java.util.concurrent.CancellationException());assertEquals(List.of(),member.getEventTimestamps());
    assertFalse(member.getMetadata().containsKey(REGISTERED));assertEquals(-1,root.commit());verifyNoInteractions(storage);
  }}
