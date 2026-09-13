package org.integratedmodelling.klab.services.resources.lang;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.languages.OntologySyntaxImpl;
import org.integratedmodelling.languages.WorldviewStandaloneSetup;
import org.integratedmodelling.languages.api.ParsedObject;
import org.integratedmodelling.languages.validation.BasicObservableValidationScope;
import org.junit.jupiter.api.Test;

/** Diagnostic inventory, not an assertion that every accepted clause is implemented. */
class WorldviewClauseAuditTest {
  @Test
  void reportRealParserBeanAndAdapterCoverage() throws Exception {
    var parser = new WorldviewStandaloneSetup().createInjectorAndDoEMFRegistration()
        .getInstance(IParser.class);
    var parsed = parser.parse(new StringReader("""
        ontology audit in domain root version 1.0.0;
        thing Entity;
        attribute State;
        quantity Measure;
        thing Specialized is audit:Entity within audit:Entity;
        thing Local is Entity;
        thing Qualified inherits audit:State;
        process Affected affects audit:Measure;
        process Created creates audit:Measure;
        attribute Applicable applies to audit:Entity;
        relationship Connection links audit:Entity to audit:Entity;
        thing Emergent emerges from audit:Entity within audit:Entity;
        attribute Implication implies audit:Entity within audit:Entity;
        thing Required requires identity audit:State;
        thing RequiredRealm requires realm audit:State;
        thing RequiredExtent requires extent audit:Entity;
        thing RequiredAttribute requires attribute audit:State;
        thing Authorized requires authority TEST;
        attribute Described describes audit:Measure as 0 to 10;
        attribute BooleanDescription describes audit:Measure as true;
        attribute ConceptDescription describes audit:Measure as audit:State;
        attribute Increasing increases with audit:Measure;
        attribute Decreasing decreases with audit:Measure;
        attribute Marked marks audit:Measure;
        class Classified classifies audit:Measure;
        ordering Discrete discretizes audit:Measure;
        deniable attribute Positive deniable as Negative;
        abstract thing Parent has disjoint children Child within audit:Entity;
        """));
    var diagnostics = new ArrayList<String>();
    parsed.getSyntaxErrors().forEach(n -> diagnostics.add(n.getSyntaxErrorMessage().getMessage()));
    assertTrue(diagnostics.isEmpty(), diagnostics.toString());
    var root = (org.integratedmodelling.languages.worldview.Ontology) parsed.getRootASTElement();
    var bean = new OntologySyntaxImpl(root, new BasicObservableValidationScope()) {
      @Override protected void logWarning(ParsedObject target, EObject object,
          EStructuralFeature feature, String message) { diagnostics.add(message); }
      @Override protected void logError(ParsedObject target, EObject object,
          EStructuralFeature feature, String message) { diagnostics.add(message); }
    };
    var adapted = LanguageAdapter.INSTANCE.adaptOntology(bean, "audit", List.of(), 0L);
    assertEquals(root.getConcepts().size(), adapted.getStatements().size());
    var report = new ArrayList<String>();
    report.add("Declaration | bean parent/inherits/applies/links/emergence/requires/describes | Kim parent/inherits/applies/links/emergence/requires/describes/affects/creates/within");
    for (int i = 0; i < bean.getConceptDeclarations().size(); i++) {
      var b = bean.getConceptDeclarations().get(i);
      var k = adapted.getStatements().get(i);
      report.add(b.getName() + " | " + (b.getDeclaredParent() != null) + "/"
          + b.getInheritedPredicates().size() + "/" + b.getAppliesTo().size() + "/"
          + b.getRelationshipSources().size() + "/" + b.getEmergesFrom().size() + "/"
          + b.getRequirements().size() + "/" + b.getDescribes().size() + " | "
          + (k.getDeclaredParent() != null) + "/" + k.getTraitsInherited().size() + "/"
          + k.getAppliesTo().size() + "/" + k.getSubjectsLinked().size() + "/"
          + k.getEmergenceTriggers().size() + "/" + k.getRequiredIdentities().size() + "/"
          + k.getObservablesDescribed().size() + "/" + k.getQualitiesAffected().size() + "/"
          + k.getObservablesCreated().size() + "/" + (k.getDeclaredInherent() != null));
    }
    report.addAll(diagnostics);
    Files.createDirectories(Path.of("target"));
    Files.write(Path.of("target/worldview-clause-audit.txt"), report);
  }
}
