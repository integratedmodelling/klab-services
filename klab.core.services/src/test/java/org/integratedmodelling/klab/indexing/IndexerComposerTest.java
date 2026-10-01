package org.integratedmodelling.klab.indexing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.ReferenceManager;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticMatch;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.junit.jupiter.api.Test;

class IndexerComposerTest {
  @Test void sameFamilyIdentitiesCannotCrowdObservableHeadsOutOfCompletions() throws Exception {
    try (var fixture = new Fixture()) {
      fixture.add("test:Oak", SemanticType.IDENTITY, SemanticType.PREDICATE);
      var oak = fixture.reasoner.resolveConcept("test:Oak");
      when(fixture.reasoner.lexicalRoot(oak)).thenReturn(oak);
      for (int i = 0; i < 120; i++) {
        String id = "test:Identity" + i;
        fixture.add(id, SemanticType.IDENTITY, SemanticType.PREDICATE);
        when(fixture.reasoner.lexicalRoot(fixture.reasoner.resolveConcept(id))).thenReturn(oak);
      }
      fixture.add("test:Native", SemanticType.IDENTITY, SemanticType.PREDICATE);
      var nativeIdentity = fixture.reasoner.resolveConcept("test:Native");
      when(fixture.reasoner.lexicalRoot(nativeIdentity)).thenReturn(nativeIdentity);
      fixture.add("test:Tree", SemanticType.SUBJECT);
      when(fixture.reasoner.satisfiable(org.mockito.ArgumentMatchers.any())).thenReturn(true);
      when(fixture.reasoner.resolveConcept(org.mockito.ArgumentMatchers.startsWith("("))).thenReturn(oak);
      when(fixture.reasoner.resolveObservable(org.mockito.ArgumentMatchers.anyString())).thenAnswer(call -> {
        String declaration = call.getArgument(0);
        var concept = declaration.endsWith("test:Tree")
            ? fixture.reasoner.resolveConcept("test:Tree") : oak;
        var observable = new org.integratedmodelling.common.knowledge.ObservableImpl();
        observable.setUrn(declaration); observable.setSemantics(concept); return observable;
      });
      fixture.refresh();
      var initial = new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchRequest();
      initial.setRequestId(1); initial.setQueryString("oak"); initial.setMaxResults(30);
      var session = new SemanticSearchSession(fixture.reasoner, fixture.index::query, initial);
      session.handle(initial, 42);
      var selection = new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchRequest();
      selection.setSearchMode(org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchRequest.Mode.SELECT);
      selection.setRequestId(2); selection.setMatchesRequestId(1);
      selection.setSelectedMatchId("test:Oak"); selection.setMaxResults(30);
      var selected = session.handle(selection, 42);
      assertTrue(selected.getErrors().isEmpty(), selected.getErrors().toString());
      var ids = selected.getMatches().stream().map(SemanticMatch::getId).toList();
      assertTrue(ids.contains("test:Tree"), ids.toString());
      assertTrue(ids.contains("test:Native"), ids.toString());
      assertFalse(ids.stream().anyMatch(id -> id.startsWith("test:Identity") || id.equals("test:Oak")));
    }
  }

  @Test void cancellationStopsTheScanBetweenCandidates() throws Exception {
    try (var fixture = new Fixture()) {
      for (int i = 0; i < 20; i++) fixture.add("test:Quality" + i, SemanticType.QUALITY);
      fixture.refresh();
      var scope = SemanticScope.root(); scope.lexicalRealm.clear();
      var checks = new java.util.concurrent.atomic.AtomicInteger();
      scope.candidateFilter = concept -> { checks.incrementAndGet(); return true; };
      scope.searchCancelled = () -> checks.get() > 0;
      assertThrows(java.util.concurrent.CancellationException.class, () -> fixture.index.query("", scope, 30));
      assertEquals(1, checks.get());
    }
  }
  @Test void limitIsAppliedAfterSemanticFilteringAcrossAllPages() throws Exception {
    try (var fixture = new Fixture()) {
      for (int i = 0; i < 1100; i++) fixture.add("test:Subject" + i, SemanticType.SUBJECT);
      fixture.add("test:Elevation", SemanticType.QUALITY);
      fixture.refresh();
      var scope = new SemanticScope();
      scope.logicalRealm.add(SemanticScope.Constraint.of(SemanticType.QUALITY));
      assertEquals(List.of("test:Elevation"), fixture.index.query("", scope, 1).stream().map(SemanticMatch::getId).toList());
    }
  }

  @Test void blankQueryHonorsLimitAndSkipsDuplicateIdentifiers() throws Exception {
    try (var fixture = new Fixture()) {
      fixture.add("test:Elevation", SemanticType.QUALITY);
      fixture.add("test:Elevation", SemanticType.QUALITY);
      for (int i = 0; i < 120; i++) fixture.add("test:Quality" + i, SemanticType.QUALITY);
      fixture.refresh();
      var matches = fixture.index.query("", SemanticScope.root(), 30);
      assertEquals(30, matches.size());
      assertEquals(30, matches.stream().map(SemanticMatch::getId).distinct().count());
    }
  }

  @Test void predicateValidationRunsBeforeTheCandidateLimit() throws Exception {
    try (var fixture = new Fixture()) {
      for (int i = 0; i < 120; i++) fixture.add("test:Subject" + i, SemanticType.SUBJECT);
      fixture.add("test:Elevation", SemanticType.QUALITY);
      fixture.refresh();
      var scope = SemanticScope.root();
      scope.lexicalRealm.clear();
      scope.candidateFilter = concept -> concept.is(SemanticType.QUALITY);
      assertEquals(List.of("test:Elevation"), fixture.index.query("", scope, 1).stream().map(SemanticMatch::getId).toList());
    }
  }

  @Test void normalizedPrefixFindsQualityAfterManyInvalidHeadConcepts() throws Exception {
    try (var fixture = new Fixture()) {
      for (int i = 0; i < 120; i++) fixture.add("test:Subject" + i, SemanticType.SUBJECT);
      fixture.add("test:Elevation", SemanticType.QUALITY);
      fixture.add("data:Normalized", SemanticType.ATTRIBUTE);
      var normalized = new ConceptImpl(); normalized.setUrn("data:Normalized");
      normalized.getType().addAll(List.of(SemanticType.PREDICATE, SemanticType.ATTRIBUTE));
      when(fixture.reasoner.resolveConcept("data:Normalized")).thenReturn(normalized);
      when(fixture.reasoner.satisfiable(org.mockito.ArgumentMatchers.any())).thenReturn(true);
      when(fixture.reasoner.resolveObservable(org.mockito.ArgumentMatchers.anyString())).thenAnswer(call -> {
        String declaration = call.getArgument(0);
        var concept = declaration.equals("data:Normalized test:Elevation")
            ? fixture.reasoner.resolveConcept("test:Elevation")
            : declaration.contains(" ") ? null : fixture.reasoner.resolveConcept(declaration);
        if (concept == null) return null;
        var observable = new org.integratedmodelling.common.knowledge.ObservableImpl();
        observable.setUrn(declaration); observable.setSemantics(concept); return observable;
      });
      fixture.refresh();
      var initial = new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchRequest();
      initial.setRequestId(1); initial.setQueryString("normalized"); initial.setMaxResults(30);
      var session = new SemanticSearchSession(fixture.reasoner, fixture.index::query, initial);
      var matches = session.handle(initial, 42);
      assertTrue(matches.getMatches().stream().anyMatch(m -> m.getId().equals("data:Normalized")));
      var selection = new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchRequest();
      selection.setSearchMode(org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchRequest.Mode.SELECT);
      selection.setRequestId(2); selection.setMatchesRequestId(1);
      selection.setSelectedMatchId("data:Normalized"); selection.setMaxResults(30);
      var selected = session.handle(selection, 42);
      assertEquals("data:Normalized", selected.getDeclaration());
      assertTrue(selected.getMatches().stream().anyMatch(m -> m.getId().equals("test:Elevation")));
      assertTrue(selected.getErrors().isEmpty(), selected.getErrors().toString());
    }
  }

  static class Fixture implements AutoCloseable {
    final Reasoner reasoner = mock(Reasoner.class);
    final Indexer index;
    final IndexWriter writer;
    Fixture() throws Exception {
      var scope = mock(Scope.class);
      when(scope.getService(Reasoner.class)).thenReturn(reasoner);
      index = new Indexer(scope);
      writer = (IndexWriter) field("writer");
    }
    Object field(String name) throws Exception {
      var field = Indexer.class.getDeclaredField(name); field.setAccessible(true); return field.get(index);
    }
    void add(String id, SemanticType type, SemanticType... categories) throws Exception {
      var concept = new ConceptImpl(); concept.setUrn(id);
      concept.getType().add(type);
      concept.getType().addAll(categories.length == 0 ? List.of(SemanticType.OBSERVABLE) : List.of(categories));
      when(reasoner.resolveConcept(id)).thenReturn(concept);
      var document = new Document();
      document.add(new StringField("id", id, Field.Store.YES));
      document.add(new TextField("name", id.substring(id.indexOf(':') + 1), Field.Store.YES));
      document.add(new StoredField("vmtype", SemanticMatch.Type.CONCEPT.ordinal()));
      document.add(new StoredField("vctype", type.ordinal()));
      document.add(new StoredField("smtype", concept.getType().stream()
          .map(value -> Integer.toString(value.ordinal())).collect(java.util.stream.Collectors.joining(","))));
      writer.addDocument(document);
    }
    void refresh() throws Exception { ((ReferenceManager<?>) field("searcherManager")).maybeRefreshBlocking(); }
    @Override public void close() throws Exception {
      ((AutoCloseable) field("nrtReopenThread")).close();
      ((AutoCloseable) field("searcherManager")).close();
      writer.close(); ((AutoCloseable) field("index")).close();
    }
  }
}
