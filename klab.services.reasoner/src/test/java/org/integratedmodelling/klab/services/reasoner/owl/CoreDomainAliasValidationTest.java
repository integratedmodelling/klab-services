package org.integratedmodelling.klab.services.reasoner.owl;
import org.integratedmodelling.klab.services.reasoner.internal.CoreOntology;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.lang.reflect.Proxy;
import java.util.*;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.services.reasoner.owl.OWL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.semanticweb.owlapi.apibinding.OWLManager;

class CoreDomainAliasValidationTest {
  @TempDir Path directory;
  final List<String> warnings = new ArrayList<>();
  final Scope scope = (Scope) Proxy.newProxyInstance(getClass().getClassLoader(),
      new Class<?>[]{Scope.class}, (self, method, args) -> {
        if (method.getName().equals("warn"))
          warnings.add(Arrays.deepToString(args));
        return null;
      });
  OWL fixture() {
    var owl = new OWL(scope); owl.manager = OWLManager.createOWLOntologyManager();
    var ontology = owl.requireOntology("odo"); var axioms = new ArrayList<Axiom>();
    for (var name : List.of("Domain", "PhysicalDomain", "KnowledgeDomain", "NormativeDomain", "OtherDomain"))
      axioms.add(Axiom.ClassAssertion(name, EnumSet.of(SemanticType.DOMAIN, SemanticType.PREDICATE)));
    for (var name : List.of("PhysicalDomain", "KnowledgeDomain", "NormativeDomain", "OtherDomain"))
      axioms.add(Axiom.SubClass("odo:Domain", name));
    ontology.define(axioms); return owl;
  }
  KimConceptStatementImpl alias(String name, String target) {
    var result = new KimConceptStatementImpl(); result.setNamespace("w"); result.setUrn(name);
    result.setType(EnumSet.of(SemanticType.DOMAIN, SemanticType.PREDICATE));
    result.setUpperConceptDefined(target); return result;
  }
  List<String> validate(OWL owl, KimConceptStatementImpl... aliases) {
    var root = new KimOntologyImpl(); root.setUrn("w"); root.getStatements().addAll(List.of(aliases));
    new CoreOntology(directory.toFile(), owl, false).validateRootDomain(root, scope);
    return warnings.stream().filter(s -> s.contains("fundamental type DOMAIN")).toList();
  }
  @Test void genericAndThreeSpecializedAliasesDoNotReassignFundamentalDomain() {
    assertTrue(validate(fixture(), alias("Domain", "odo:Domain"),
        alias("Physical", "odo:PhysicalDomain"), alias("Knowledge", "odo:KnowledgeDomain"),
        alias("Normative", "odo:NormativeDomain")).isEmpty());
  }
  @Test void duplicateFundamentalAliasesPreserveTheDisabledBlanketWarningPolicy() {
    var messages = validate(fixture(), alias("First", "odo:Domain"), alias("Second", "odo:Domain"));
    assertTrue(messages.isEmpty());
    assertTrue(warnings.stream().noneMatch(s -> s.contains("being reassigned")));
  }
  @Test void specializedAliasesDoNotEnableTheDisabledMissingSlotWarning() {
    var messages = validate(fixture(), alias("Physical", "odo:PhysicalDomain"),
        alias("Knowledge", "odo:KnowledgeDomain"), alias("Normative", "odo:NormativeDomain"));
    assertTrue(messages.isEmpty());
    assertTrue(warnings.stream().noneMatch(s -> s.contains("is not assigned")));
  }
  @Test void anEmptyRootDoesNotEmitDisabledBlanketMissingTypeWarnings() {
    validate(fixture());
    assertTrue(warnings.isEmpty(), warnings.toString());
  }
  @Test void resolvedCanonicalIdentityWorksThroughDifferentAliasSpelling() {
    var owl=fixture(); owl.requireOntology("alias").addDelegateConcept("PhysicalKind", "alias", owl.getConcept("odo:PhysicalDomain"));
    assertTrue(validate(owl, alias("Domain", "odo:Domain"), alias("Physical", "alias:PhysicalKind")).isEmpty());
  }
  @Test void namesAloneCannotMakeAnUnrecognizedSubtypeCanonical() {
    var messages=validate(fixture(), alias("Domain", "odo:Domain"), alias("Physical", "odo:OtherDomain"));
    assertEquals(1,messages.size()); assertTrue(messages.getFirst().contains("instead of a canonical"));
  }
  @Test void unknownEndpointStillReportsItsActiveCanonicalKindViolation() {
    var messages=validate(fixture(), alias("Domain", "odo:Missing"));
    assertEquals(1,messages.size()); assertTrue(messages.getFirst().contains("instead of a canonical"));
    assertTrue(warnings.stream().noneMatch(s->s.contains("is not assigned")));
  }
  @Test void specializedDeclarationOrderDoesNotAffectFundamentalClaim() {
    assertTrue(validate(fixture(), alias("Normative", "odo:NormativeDomain"),
        alias("Knowledge", "odo:KnowledgeDomain"), alias("Physical", "odo:PhysicalDomain"),
        alias("Domain", "odo:Domain")).isEmpty());
  }
  private KimConceptStatementImpl typedAlias(String name, String target, SemanticType... types) {
    var result = alias(name, target);
    result.setType(EnumSet.copyOf(List.of(types)));
    return result;
  }
  private OWL genericFixture() {
    var owl = fixture();
    var ontology = owl.requireOntology("odo");
    var axioms = new ArrayList<Axiom>();
    for (var name : List.of("Quality", "Extent", "Time", "Space", "OtherExtent", "Mass"))
      axioms.add(Axiom.ClassAssertion(name, EnumSet.of(SemanticType.OBSERVABLE,
          name.equals("Quality") || name.equals("Mass") ? SemanticType.QUALITY : SemanticType.EXTENT)));
    ontology.define(axioms);
    return owl;
  }
  @Test void canonicalGenericAliasesAndSpecificQualityWorkInEitherOrder() {
    var aliases = new KimConceptStatementImpl[]{
        typedAlias("Q", "odo:Quality", SemanticType.QUALITY, SemanticType.OBSERVABLE),
        typedAlias("E", "odo:Extent", SemanticType.EXTENT, SemanticType.OBSERVABLE),
        typedAlias("T", "odo:Time", SemanticType.EXTENT, SemanticType.OBSERVABLE),
        typedAlias("S", "odo:Space", SemanticType.EXTENT, SemanticType.OBSERVABLE),
        typedAlias("M", "odo:Mass", SemanticType.MASS, SemanticType.QUALITY, SemanticType.OBSERVABLE)};
    validate(genericFixture(), aliases);
    assertTrue(warnings.isEmpty(), warnings.toString());
    Collections.reverse(Arrays.asList(aliases));
    validate(genericFixture(), aliases);
    assertTrue(warnings.isEmpty(), warnings.toString());
  }
  @Test void genericAliasesUseResolvedIdentityAndRejectUnknownOrWrongTargets() {
    var owl = genericFixture();
    owl.requireOntology("alias").addDelegateConcept("Clock", "alias", owl.getConcept("odo:Time"));
    validate(owl, typedAlias("T", "alias:Clock", SemanticType.EXTENT));
    assertTrue(warnings.isEmpty(), warnings.toString());
    validate(owl, typedAlias("Q", "odo:Missing", SemanticType.QUALITY),
        typedAlias("E", "odo:OtherExtent", SemanticType.EXTENT),
        typedAlias("Q2", "odo:Time", SemanticType.QUALITY));
    assertEquals(3, warnings.size());
    assertTrue(warnings.stream().allMatch(s -> s.contains("instead of a canonical")));
  }
  @Test void genuinelyAmbiguousGenericAndSpecificKindsStillWarn() {
    validate(genericFixture(),
        typedAlias("Both", "odo:Quality", SemanticType.QUALITY, SemanticType.EXTENT),
        typedAlias("Specific", "odo:Mass", SemanticType.MASS, SemanticType.LENGTH, SemanticType.QUALITY),
        typedAlias("Missing", "odo:Quality", SemanticType.OBSERVABLE));
    assertEquals(3, warnings.size());
    assertEquals(2, warnings.stream().filter(s -> s.contains("ambiguous")).count());
    assertTrue(warnings.stream().anyMatch(s -> s.contains("can't establish")));
  }
}
