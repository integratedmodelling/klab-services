package org.integratedmodelling.klab.api.lang.kim;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl;
import org.junit.jupiter.api.Test;

class PreparedDependencyExpansionTest {
  private static KimConceptImpl concept(String name, SemanticType... flags) {
    var c=new KimConceptImpl();c.setName(name);c.setType(EnumSet.copyOf(List.of(flags)));return c;
  }
  private static KimConceptImpl predicate(String name) {
    return concept(name,SemanticType.PREDICATE,SemanticType.ATTRIBUTE);
  }
  private static KimConcept declaration(String name) {
    if (name.equals("test:Head")) return concept(name,SemanticType.QUALITY);
    var p=predicate(name);if(name.equals("test:A")||name.equals("test:B"))p.getType().add(SemanticType.ABSTRACT);return p;
  }
  private static KimObservableImpl source() {
    var root=concept("test:Head",SemanticType.QUALITY);root.getTraits().addAll(List.of(predicate("test:A"),predicate("test:B")));
    var o=new KimObservableImpl();o.setSemantics(root);o.setUrn("authored");o.setUnit("m");o.setNamespace("owner");return o;
  }
  private static DependencyExpansion.Classification success(String... names) {
    return new DependencyExpansion.Classification(DependencyExpansion.Status.SUCCESS,
        Arrays.stream(names).map(PreparedDependencyExpansionTest::predicate).map(c->(KimConcept)c).toList());
  }
  private static Map<DependencyExpansion.RequestIdentity,DependencyExpansion.Classification> outcomes(DependencyExpansion.Prepared plan) {
    var r=plan.requests();return Map.of(r.get(0).identity(),success("test:A1","test:A2"),r.get(1).identity(),success("test:B1","test:B2","test:B3"));
  }
  private static Set<String> pairs(List<KimObservableImpl> values) {
    var pairs=new HashSet<String>();for(var o:values)pairs.add(o.getSemantics().getTraits().get(0).getName()+"/"+o.getSemantics().getTraits().get(1).getName());return pairs;
  }

  @Test void preparationFreezesOneAttemptAndNeverClassifiesOrLooksUpDeclarationsAgain() {
    var calls=new AtomicInteger();var plan=DependencyExpansion.prepare(source(),name->{calls.incrementAndGet();return declaration(name);});
    int afterPrepare=calls.get();var first=plan.requests();var second=plan.requests();
    assertEquals(2,first.size());assertEquals(plan.attemptId(),first.getFirst().identity().attemptId());
    assertEquals(first.stream().map(DependencyExpansion.Request::identity).toList(),second.stream().map(DependencyExpansion.Request::identity).toList());
    assertNotSame(first.getFirst().originalObservable(),second.getFirst().originalObservable());
    assertEquals(6,plan.apply(outcomes(plan)).size());assertEquals(afterPrepare,calls.get());
  }
  @Test void sourceAndDeclarationsAndRequestPayloadsCannotMutatePreparedSemantics() {
    var original=source();var a=(KimConceptImpl)declaration("test:A");
    var plan=DependencyExpansion.prepare(original,name->name.equals("test:A")?a:declaration(name));
    original.setUnit("changed");((KimConceptImpl)original.getSemantics()).setName("changed");a.setName("changed");a.getType().clear();
    var request=plan.requests().getFirst();((KimObservableImpl)request.originalObservable()).setUnit("changed-again");
    ((KimConceptImpl)request.predicate()).setName("wrong");((KimConceptImpl)request.declaration()).getType().clear();
    var fresh=plan.requests().getFirst();assertEquals("test:A",fresh.declaration().getName());assertTrue(fresh.declaration().is(SemanticType.ABSTRACT));
    var result=plan.apply(outcomes(plan));assertEquals(6,result.size());assertEquals("m",result.getFirst().getUnit());
    assertEquals("test:Head",result.getFirst().getSemantics().getName());assertTrue(result.getFirst().isOptional());assertNull(result.getFirst().getUrn());
    result.getFirst().setUnit("corrupt-result");assertEquals("m",plan.apply(outcomes(plan)).getFirst().getUnit());
  }
  @Test void exactKeysRejectMissingExtraWrongPathAndOtherAttemptBeforeApplication() {
    var plan=DependencyExpansion.prepare(source(),PreparedDependencyExpansionTest::declaration);
    assertThrows(IllegalArgumentException.class,()->plan.apply(Map.of()));
    assertThrows(IllegalArgumentException.class,()->plan.apply(outcomes(DependencyExpansion.prepare(source(),PreparedDependencyExpansionTest::declaration))));
    var extra=new LinkedHashMap<>(outcomes(plan));extra.put(new DependencyExpansion.RequestIdentity(plan.attemptId(),List.of("semantics","wrong")),success());
    assertThrows(IllegalArgumentException.class,()->plan.apply(extra));
    extra.remove(plan.requests().getFirst().identity());assertThrows(IllegalArgumentException.class,()->plan.apply(extra));
  }
  @Test void everyFactorMustSucceedBeforeEmptyCanReturnNoDependencies() {
    var plan=DependencyExpansion.prepare(source(),PreparedDependencyExpansionTest::declaration);var r=plan.requests();
    for(var status:List.of(DependencyExpansion.Status.UNKNOWN,DependencyExpansion.Status.FAILED)) {
      var error=assertThrows(DependencyExpansion.ClassificationException.class,()->plan.apply(Map.of(r.get(0).identity(),success(),r.get(1).identity(),new DependencyExpansion.Classification(status,List.of()))));
      assertEquals(status,error.status());assertEquals(r.get(1).identity(),error.identity());
    }
    assertTrue(plan.apply(Map.of(r.get(0).identity(),success(),r.get(1).identity(),success("test:B1"))).isEmpty());
  }
  @Test void completedClassificationOwnsDetachedCanonicalCandidates() {
    var original=predicate("test:Concrete");var classification=new DependencyExpansion.Classification(DependencyExpansion.Status.SUCCESS,List.of(original));
    original.setName("wrong");original.getType().add(SemanticType.ABSTRACT);
    ((KimConceptImpl)classification.concreteClosure().getFirst()).setName("wrong-again");
    assertEquals("test:Concrete",classification.concreteClosure().getFirst().getName());
    assertFalse(classification.concreteClosure().getFirst().is(SemanticType.ABSTRACT));
  }
  @Test void noFactorPreparedApplicationIsDetachedAndLegacyAdapterKeepsOriginalIdentity() {
    var original=source();original.getSemantics().getTraits().clear();
    var plan=DependencyExpansion.prepare(original,PreparedDependencyExpansionTest::declaration);
    assertTrue(plan.requests().isEmpty());var result=plan.apply(Map.of()).getFirst();assertNotSame(original,result);
    assertFalse(result.isOptional());assertEquals("authored",result.getUrn());
    assertSame(original,DependencyExpansion.expand(original,PreparedDependencyExpansionTest::declaration,request->{throw new AssertionError();}).getFirst());
    assertFalse(plan.applyAsync(Map.of()).join().getFirst().isOptional());
  }
  @Test void asynchronousOutOfOrderCompletionRetainsPreparedOrderAndFullProduct() {
    var plan=DependencyExpansion.prepare(source(),PreparedDependencyExpansionTest::declaration);var r=plan.requests();
    var a=new CompletableFuture<DependencyExpansion.Classification>();var b=new CompletableFuture<DependencyExpansion.Classification>();
    var result=plan.applyAsync(Map.of(r.get(1).identity(),b,r.get(0).identity(),a));assertFalse(result.isDone());
    b.complete(success("test:B1","test:B2","test:B3"));assertFalse(result.isDone());
    a.complete(success("test:A1","test:A2"));assertEquals(6,result.join().size());
    assertEquals(Set.of("test:A1/test:B1","test:A1/test:B2","test:A1/test:B3","test:A2/test:B1","test:A2/test:B2","test:A2/test:B3"),pairs(result.join()));
  }
  @Test void reusingOneCompletionStageDoesNotCorrelateIndependentOccurrences() {
    var original=source();((KimConceptImpl)original.getSemantics().getTraits().get(1)).setName("test:A");
    var plan=DependencyExpansion.prepare(original,PreparedDependencyExpansionTest::declaration);var r=plan.requests();
    var stage=new CompletableFuture<DependencyExpansion.Classification>();var result=plan.applyAsync(Map.of(r.get(0).identity(),stage,r.get(1).identity(),stage));
    stage.complete(success("test:A1","test:A2"));assertEquals(4,pairs(result.join()).size());
  }
  @Test void completedPayloadMutationCannotChangeALaterAsynchronousProduct() {
    var plan=DependencyExpansion.prepare(source(),PreparedDependencyExpansionTest::declaration);var r=plan.requests();
    var a=new CompletableFuture<DependencyExpansion.Classification>();var b=new CompletableFuture<DependencyExpansion.Classification>();
    var result=plan.applyAsync(Map.of(r.get(0).identity(),a,r.get(1).identity(),b));
    var candidate=predicate("test:B1");var closure=new DependencyExpansion.Classification(DependencyExpansion.Status.SUCCESS,List.of(candidate));b.complete(closure);
    candidate.setName("wrong");((KimConceptImpl)closure.concreteClosure().getFirst()).setName("wrong-accessor");
    a.complete(success("test:A1"));assertEquals(Set.of("test:A1/test:B1"),pairs(result.join()));
  }
  @Test void asynchronousUnknownAndFailedRemainTypedEvenWhenAnotherFactorIsEmpty() {
    var plan=DependencyExpansion.prepare(source(),PreparedDependencyExpansionTest::declaration);var r=plan.requests();
    for(var status:List.of(DependencyExpansion.Status.UNKNOWN,DependencyExpansion.Status.FAILED)) {
      var later=new CompletableFuture<DependencyExpansion.Classification>();
      var result=plan.applyAsync(Map.of(r.get(0).identity(),CompletableFuture.completedFuture(success()),r.get(1).identity(),later));
      assertFalse(result.isDone());later.complete(new DependencyExpansion.Classification(status,List.of()));
      var error=assertThrows(CompletionException.class,result::join);var typed=assertInstanceOf(DependencyExpansion.ClassificationException.class,error.getCause());
      assertEquals(status,typed.status());assertEquals(r.get(1).identity(),typed.identity());
    }
  }
  @Test void asynchronousExecutionFailureIsNotClassificationAbsence() {
    var plan=DependencyExpansion.prepare(source(),PreparedDependencyExpansionTest::declaration);var r=plan.requests();
    var failure=new IllegalArgumentException("classifier failed");
    var result=plan.applyAsync(Map.of(r.get(0).identity(),CompletableFuture.completedFuture(success()),r.get(1).identity(),CompletableFuture.failedFuture(failure)));
    assertSame(failure,assertThrows(CompletionException.class,result::join).getCause());
  }
  @Test void prerequisiteCancellationPropagatesPromptlyWithoutCancellingBorrowedStages() {
    var plan=DependencyExpansion.prepare(source(),PreparedDependencyExpansionTest::declaration);var r=plan.requests();
    var pending=new CompletableFuture<DependencyExpansion.Classification>();var cancelled=new CompletableFuture<DependencyExpansion.Classification>();
    var result=plan.applyAsync(Map.of(r.get(0).identity(),pending,r.get(1).identity(),cancelled));cancelled.cancel(false);
    assertTrue(result.isCancelled());assertThrows(CancellationException.class,result::join);assertFalse(pending.isDone());
  }
  @Test void consumerCancellationDoesNotCancelBorrowedPrerequisites() {
    var plan=DependencyExpansion.prepare(source(),PreparedDependencyExpansionTest::declaration);var r=plan.requests();
    var a=new CompletableFuture<DependencyExpansion.Classification>();var b=new CompletableFuture<DependencyExpansion.Classification>();
    var result=plan.applyAsync(Map.of(r.get(0).identity(),a,r.get(1).identity(),b));result.cancel(false);
    assertFalse(a.isDone());assertFalse(b.isDone());a.complete(success("test:A1"));b.complete(success("test:B1"));assertTrue(result.isCancelled());
  }
  @Test void invalidAsyncKeysAreRejectedBeforeCallbackRegistration() {
    var plan=DependencyExpansion.prepare(source(),PreparedDependencyExpansionTest::declaration);var stage=new TrackingFuture();
    assertThrows(IllegalArgumentException.class,()->plan.applyAsync(Map.of(new DependencyExpansion.RequestIdentity(plan.attemptId(),List.of("wrong")),stage)));
    assertEquals(0,stage.registrations.get());
  }
  @Test void nullCompletedValueIsNotASuccessfulEmptyClosure() {
    var plan=DependencyExpansion.prepare(source(),PreparedDependencyExpansionTest::declaration);var r=plan.requests();
    var result=plan.applyAsync(Map.of(r.get(0).identity(),CompletableFuture.completedFuture(success()),r.get(1).identity(),CompletableFuture.completedFuture(null)));
    assertInstanceOf(NullPointerException.class,assertThrows(CompletionException.class,result::join).getCause());
  }
  @Test void exceptionalPrerequisiteWaitsForOtherCapturesWithoutChoosingRollbackPolicy() {
    var plan=DependencyExpansion.prepare(source(),PreparedDependencyExpansionTest::declaration);var r=plan.requests();
    var pending=new CompletableFuture<DependencyExpansion.Classification>();var failure=new IllegalStateException("failed");
    var result=plan.applyAsync(Map.of(r.get(0).identity(),CompletableFuture.failedFuture(failure),r.get(1).identity(),pending));
    assertFalse(result.isDone());pending.complete(success());
    assertSame(failure,assertThrows(CompletionException.class,result::join).getCause());
  }
  private static class TrackingFuture extends CompletableFuture<DependencyExpansion.Classification> {
    final AtomicInteger registrations=new AtomicInteger();
    @Override public CompletableFuture<DependencyExpansion.Classification> whenComplete(BiConsumer<? super DependencyExpansion.Classification,? super Throwable> action) {
      registrations.incrementAndGet();return super.whenComplete(action);
    }
  }
}
