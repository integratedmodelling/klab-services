package org.integratedmodelling.klab.runtime.language;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.junit.jupiter.api.Test;

class KimVisitorsTest {

  @Test
  void defaultKimValidatorAppliesObservableRulesToConcepts() {
    var invalid = concept("demo:Quality");
    invalid.setType(EnumSet.of(SemanticType.QUALITY));
    invalid.setCollective(true);

    var visitor = new KimObservableVisitor();
    visitor.visit(invalid);

    assertEquals(1, visitor.getNotifications().size());
    assertTrue(visitor.getNotifications().getFirst().getMessage().contains("each"));
  }

  @Test
  void observableTraversalDelegatesConceptsToResolverAndValidatorOnce() {
    var leaf = concept("demo:Leaf");
    var root = new KimConceptImpl();
    root.setObservable(leaf);
    root.setTraits(List.of(leaf));
    root.setParent(root); // malformed cycles must not recurse forever
    var observable = new KimObservableImpl();
    observable.setSemantics(root);
    observable.setModelReference("demo.model");

    var validated = new AtomicInteger();
    var visitor =
        new KimObservableVisitor(
            new KimObservableVisitor.LenientValidator() {
              @Override
              public List<Notification> validateConcept(
                  KimConcept concept, KimObservableVisitor.Context context) {
                validated.incrementAndGet();
                return List.of(Notification.info("validated"));
              }
            },
            (urn, knowledgeClass, context) -> "resolved:" + urn);

    visitor.visit(observable);

    assertEquals(List.of(root, leaf), visitor.getConcepts());
    assertEquals(2, validated.get());
    assertEquals(2, visitor.getNotifications().size());
    assertTrue(
        visitor.getReferences().stream()
            .anyMatch(
                ref ->
                    ref.urn().equals("demo:Leaf")
                        && ref.knowledgeClass() == KlabAsset.KnowledgeClass.CONCEPT
                        && ref.resolved().equals("resolved:demo:Leaf")));
    assertTrue(
        visitor.getReferences().stream()
            .anyMatch(
                ref ->
                    ref.urn().equals("demo.model")
                        && ref.knowledgeClass() == KlabAsset.KnowledgeClass.MODEL));
  }

  @Test
  void namespaceTraversalCoversImportsModelsAndSharedSemantics() {
    var leaf = concept("demo:Quality");
    var observable = new KimObservableImpl();
    observable.setSemantics(leaf);
    var model = new KimModelImpl();
    model.setObservables(List.of(observable));
    var namespace = new KimNamespaceImpl();
    namespace.setImports(Map.of("imported.namespace", List.of()));
    namespace.setStatements(List.of(model));

    var models = new AtomicInteger();
    var visitor =
        new KimNamespaceVisitor(
            new KimNamespaceVisitor.LenientValidator() {
              @Override
              public List<Notification> validateModel(
                  org.integratedmodelling.klab.api.lang.kim.KimModel model,
                  KimObservableVisitor.Context context) {
                models.incrementAndGet();
                return List.of();
              }
            },
            null);

    visitor.visit(namespace);

    assertEquals(1, models.get());
    assertEquals(List.of(observable), visitor.getObservables());
    assertEquals(List.of(leaf), visitor.getConcepts());
    assertTrue(
        visitor.getReferences().stream()
            .anyMatch(
                ref ->
                    ref.urn().equals("imported.namespace")
                        && ref.knowledgeClass() == KlabAsset.KnowledgeClass.NAMESPACE));
  }

  @Test
  void ontologyTraversalCoversImportsDomainAndNestedConceptStatements() {
    var domain = concept("demo:Domain");
    var declared = concept("demo:Declared");
    var child = new KimConceptStatementImpl();
    child.setObservablesCreated(List.of(declared));
    var parent = new KimConceptStatementImpl();
    parent.setChildren(List.of(child));
    var ontology = new KimOntologyImpl();
    ontology.setDomain(domain);
    ontology.setImportedOntologies(List.of("base.ontology"));
    ontology.setStatements(List.of(parent));

    var visitor = new KimOntologyVisitor();
    visitor.visit(ontology);

    assertEquals(List.of(domain, declared), visitor.getConcepts());
    assertEquals(
        2,
        visitor.getStatements().stream()
            .filter(org.integratedmodelling.klab.api.lang.kim.KimConceptStatement.class::isInstance)
            .count());
    assertTrue(
        visitor.getReferences().stream()
            .anyMatch(
                ref ->
                    ref.urn().equals("base.ontology")
                        && ref.knowledgeClass() == KlabAsset.KnowledgeClass.ONTOLOGY));
  }

  @Test
  void strategyTraversalCoversFiltersOperationsAndServiceCalls() {
    var filterConcept = concept("demo:Filter");
    var operationConcept = concept("demo:Operation");
    var operationObservable = new KimObservableImpl();
    operationObservable.setSemantics(operationConcept);
    var filter = new KimObservationStrategyImpl.FilterImpl();
    filter.setMatch(filterConcept);
    filter.setFunctions(List.of(new ServiceCallImpl("demo.filter")));
    var operation = new KimObservationStrategyImpl.OperationImpl();
    operation.setObservable(operationObservable);
    operation.setFunctions(List.of(new ServiceCallImpl("demo.operation")));
    var strategy = new KimObservationStrategyImpl();
    strategy.setFilters(List.of(List.of(filter)));
    strategy.setOperations(List.of(operation));
    var document = new KimObservationStrategiesImpl();
    document.setStatements(List.of(strategy));

    var visitor = new KimObservationStrategyDocumentVisitor();
    visitor.visit(document);

    assertEquals(List.of(filterConcept, operationConcept), visitor.getConcepts());
    assertEquals(2, visitor.getServiceCalls().size());
    assertEquals(
        Set.of("demo.filter", "demo.operation"),
        visitor.getReferences().stream()
            .filter(ref -> ref.knowledgeClass() == KlabAsset.KnowledgeClass.SERVICE_IMPLEMENTATION)
            .map(KimObservableVisitor.Reference::urn)
            .collect(java.util.stream.Collectors.toSet()));
  }

  private static KimConceptImpl concept(String name) {
    var ret = new KimConceptImpl();
    ret.setName(name);
    return ret;
  }
}
