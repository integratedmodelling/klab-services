package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.junit.jupiter.api.Test;

class BeanIngestionPreflightTest {
  private static KimOntologyImpl ontology(String urn) {
    var bean = new KimOntologyImpl(); bean.setUrn(urn); bean.setSourceCode("ontology " + urn);
    return bean;
  }
  private static WorldviewImpl world(KimOntology... ontologies) {
    var world = new WorldviewImpl(); world.setOntologies(List.of(ontologies)); return world;
  }

  @Test void currentStructuredErrorsOwnTheGateInsteadOfStaleAggregateFlags() {
    var bean = ontology("imod"); var worldview = world(bean);
    worldview.setEmpty(true);
    assertTrue(BeanIngestionPreflight.errors(worldview).isEmpty());
    assertTrue(WorldviewLoadingSupport.loadable(worldview, false));
    worldview.setEmpty(false);
    bean.getNotifications().add(Notification.error("fresh source error"));
    assertFalse(BeanIngestionPreflight.errors(worldview).isEmpty());
    assertFalse(WorldviewLoadingSupport.loadable(worldview, true));
  }

  @Test void missingImportsAreRejectedAndPresentAuthoritativeImportsAreAccepted() {
    var bean = ontology("imod"); bean.setImportedOntologies(List.of("earth"));
    assertTrue(BeanIngestionPreflight.errors(world(bean)).stream()
        .anyMatch(n -> n.getMessage().contains("imported ontology is unavailable")));
    assertTrue(BeanIngestionPreflight.errors(world(bean, ontology("earth"))).isEmpty());
  }

  @Test void referencesResolveAgainstCurrentSourceBeansAndChangedBatchOverlays() {
    var alpha = ontology("alpha"); var beta = ontology("beta");
    var declaration = new KimConceptStatementImpl(); declaration.setUrn("Derived"); declaration.getType().add(SemanticType.SUBJECT);
    var reference = new KimConceptImpl(); reference.setName("beta:Known");
    declaration.setDeclaredParent(reference); alpha.getStatements().add(declaration);
    var known = new KimConceptStatementImpl(); known.setUrn("Known"); known.getType().add(SemanticType.SUBJECT); beta.getStatements().add(known);
    assertTrue(BeanIngestionPreflight.errors(List.of(alpha), List.of(world(alpha, beta))).isEmpty());
    var replacement = ontology("beta");
    assertTrue(BeanIngestionPreflight.errors(List.of(alpha, replacement), List.of(world(alpha, beta)))
        .stream().anyMatch(n -> n.getMessage().contains("Undefined concept: beta:Known")));
  }

  @Test void errorsOnUnchangedOldSourcesDoNotRejectFreshValidatedReplacementBeans() {
    var old = ontology("imod"); old.getNotifications().add(Notification.error("old source error"));
    var fresh = ontology("imod");
    assertTrue(BeanIngestionPreflight.errors(List.of(fresh), List.of(world(old))).isEmpty());
  }

  @Test void authoritativeImportedBeanErrorsBlockTheRequestedClosureButUnrelatedErrorsDoNot() {
    var changed = ontology("alpha"); changed.setImportedOntologies(List.of("beta"));
    var dependency = ontology("beta"); dependency.getNotifications().add(Notification.error("current beta error"));
    assertTrue(BeanIngestionPreflight.errors(List.of(changed), List.of(world(changed, dependency)))
        .stream().anyMatch(n -> n.getMessage().equals("current beta error")));
    changed.setImportedOntologies(List.of());
    assertTrue(BeanIngestionPreflight.errors(List.of(changed), List.of(world(changed, dependency))).isEmpty());
  }

  @Test void frozenSourcesAndRootSentinelSurviveLaterSourceMutation() {
    var source = ontology("imod"); var statement = new KimConceptStatementImpl(); statement.setUrn("Before");
    source.getStatements().add(statement);
    var frozen = BeanIngestionPreflight.snapshot(world(source));
    source.setSourceCode("later source"); statement.setUrn("After");
    assertEquals("ontology imod", frozen.getOntologies().getFirst().getSourceCode());
    assertEquals("Before", frozen.getOntologies().getFirst().getStatements().getFirst().getUrn());
    assertSame(KimOntology.rootDomain, frozen.getOntologies().getFirst().getDomain());
    assertTrue(WorldviewLoadingSupport.loadable(frozen, false));
  }

  private static KimObservationStrategiesImpl strategy(String reference) {
    var document = new KimObservationStrategiesImpl(); document.setUrn("policy"); document.setSourceCode("strategies policy");
    var concept = new KimConceptImpl(); concept.setName(reference);
    var observable = new KimObservableImpl(); observable.setSemantics(concept);
    var alternative = new KimObservationPlanImpl.MatchAlternativeImpl(); alternative.setObservable(observable);
    var selection = new KimObservationPlanImpl.StrategySelectionImpl(); selection.setAlternatives(List.of(alternative));
    var strategy = new KimObservationStrategyImpl(); strategy.setUrn("observe"); strategy.setSelection(selection);
    document.getStatements().add(strategy); return document;
  }

  @Test void strategyReferencesMustExistAndTheirCurrentDependencyErrorsAreCollected() {
    var dependency = ontology("earth");
    var known = new KimConceptStatementImpl(); known.setUrn("Known"); known.getType().add(SemanticType.SUBJECT); dependency.getStatements().add(known);
    assertTrue(BeanIngestionPreflight.errors(List.of(strategy("missing:Thing")), List.of(world(dependency)))
        .stream().anyMatch(n -> n.getMessage().contains("Undefined strategy concept: missing:Thing")));
    dependency.getNotifications().add(Notification.error("current dependency error"));
    assertTrue(BeanIngestionPreflight.errors(List.of(strategy("earth:Known")), List.of(world(dependency)))
        .stream().anyMatch(n -> n.getMessage().equals("current dependency error")));
  }

  @Test void suppliedNestedStatementAndConceptErrorsCannotDisappearDuringTraversal() {
    var ontology = ontology("imod");
    var declaration = new KimConceptStatementImpl(); declaration.setUrn("Known");
    var child = new KimConceptStatementImpl(); child.setUrn("Child");
    child.getNotifications().add(Notification.error("nested declaration error"));
    declaration.getChildren().add(child);
    var concept = new KimConceptImpl(); concept.setName("imod:Known");
    concept.getNotifications().add(Notification.create("nested concept error", Notification.Level.SystemError));
    declaration.setDeclaredInherent(concept); ontology.getStatements().add(declaration);
    var errors = BeanIngestionPreflight.errors(world(ontology));
    assertTrue(errors.stream().anyMatch(n -> n.getMessage().equals("nested declaration error")));
    assertTrue(errors.stream().anyMatch(n -> n.getMessage().equals("nested concept error") && n.getLevel() == Notification.Level.SystemError));
  }

  @Test void currentAuthorityFailuresGateButCurrentValidatedBindingsSupersedeOldMessages() {
    var ontology = ontology("imod");
    var root = new KimConceptStatementImpl(); root.setUrn("Taxon"); root.setNamespace("imod");
    root.getType().add(SemanticType.IDENTITY); root.setAuthorityRequired("TAXA");
    root.getAuthorityParameters().put("urn", "provider.taxa"); ontology.getStatements().add(root);
    var snapshot = world(ontology);
    var message = "Authority TAXA at imod:Taxon: Missing authority provider descriptor";
    snapshot.getNotifications().add(Notification.error(message));
    assertTrue(BeanIngestionPreflight.errors(List.of(ontology), List.of(snapshot))
        .stream().anyMatch(n -> n.getMessage().equals(message)));
    var descriptor = new Extensions.AuthorityDescriptor("provider.taxa", Version.create("1.0.0"), true, true, List.of(), List.of());
    snapshot.setAuthorityBindings(List.of(new Worldview.AuthorityBinding("TAXA", "imod:Taxon", "imod", 0, 0,
        SemanticValidationRequest.sourceHash(ontology.getSourceCode()), descriptor, "component", Version.create("1.0.0"), null)));
    assertTrue(BeanIngestionPreflight.errors(List.of(ontology), List.of(snapshot)).isEmpty());
    ontology.setSourceCode("later authority source");
    assertFalse(BeanIngestionPreflight.errors(List.of(ontology), List.of(snapshot)).isEmpty());
  }

  private static KimObservationStrategiesImpl importedStrategy(String urn, String... imports) {
    var document = new KimObservationStrategiesImpl(); document.setUrn(urn);
    document.setSourceCode("strategies " + urn); document.setImports(List.of(imports)); return document;
  }

  @Test void explicitStrategyImportsRequireStrategyDocumentsAndNeverAnOntologyWithTheSameUrn() {
    var policy = importedStrategy("policy", "defaults");
    var defaults = importedStrategy("defaults");
    var snapshot = world(ontology("imod")); snapshot.setObservationStrategies(List.of(defaults));
    assertTrue(BeanIngestionPreflight.errors(List.of(policy), List.of(snapshot)).isEmpty());
    snapshot.setObservationStrategies(List.of());
    assertTrue(BeanIngestionPreflight.errors(List.of(policy), List.of(snapshot)).stream()
        .anyMatch(n -> n.getMessage().equals("Authoritative strategy import is unavailable: defaults")));
    snapshot.setOntologies(List.of(ontology("imod"), ontology("defaults")));
    assertFalse(BeanIngestionPreflight.errors(List.of(policy), List.of(snapshot)).isEmpty());
  }

  @Test void strategyImportCyclesTerminateAndTransitiveCurrentErrorsRejectTheWholeClosure() {
    var policy = importedStrategy("policy", "defaults");
    var defaults = importedStrategy("defaults", "policy", "shared");
    var shared = importedStrategy("shared");
    var snapshot = world(ontology("imod")); snapshot.setObservationStrategies(List.of(policy, defaults, shared));
    assertTrue(BeanIngestionPreflight.errors(List.of(policy), List.of(snapshot)).isEmpty());
    shared.getNotifications().add(Notification.error("transitive strategy error"));
    var errors = BeanIngestionPreflight.errors(List.of(policy), List.of(snapshot));
    assertEquals(1, errors.stream().filter(n -> n.getMessage().equals("transitive strategy error")).count());
  }

  @Test void changedStrategyBeansOverlayOldErrorsButCannotHideTheirNewTransitiveImports() {
    var old = importedStrategy("defaults"); old.getNotifications().add(Notification.error("old strategy error"));
    var policy = importedStrategy("policy", "defaults");
    var fresh = importedStrategy("defaults");
    var snapshot = world(ontology("imod")); snapshot.setObservationStrategies(List.of(old));
    assertTrue(BeanIngestionPreflight.errors(List.of(policy, fresh), List.of(snapshot)).isEmpty());
    fresh.setImports(List.of("missing"));
    assertTrue(BeanIngestionPreflight.errors(List.of(policy, fresh), List.of(snapshot)).stream()
        .anyMatch(n -> n.getMessage().equals("Authoritative strategy import is unavailable: missing")));
  }
}
