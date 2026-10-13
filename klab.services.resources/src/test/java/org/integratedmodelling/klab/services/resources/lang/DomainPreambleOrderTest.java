package org.integratedmodelling.klab.services.resources.lang;

import org.integratedmodelling.klab.runtime.language.WorldviewValidationScope;
import org.integratedmodelling.klab.runtime.language.LanguageAdapter;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.util.*;
import org.eclipse.emf.ecore.*;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.languages.OntologySyntaxImpl;
import org.integratedmodelling.languages.WorldviewStandaloneSetup;
import org.integratedmodelling.languages.api.ParsedObject;
import org.integratedmodelling.languages.worldview.Ontology;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.runtime.language.KimOntologyVisitor;
import org.junit.jupiter.api.Test;

/** Real parser/adapter/bean checks; no service or stale workspace resolver is needed. */
class DomainPreambleOrderTest {
  private static final String ROOT = "ontology imod in domain root version 1.0; "
      + "abstract domain Physical is core odo:PhysicalDomain; "
      + "abstract domain Knowledge is core odo:KnowledgeDomain; domain Life is imod:Physical;";
  private static final String LOCAL = "ontology biology using imod in domain biology:Biology version 1.0; "
      + "domain Biology is imod:Knowledge about imod:Life;";

  private final Map<String, KimOntology> imported = new HashMap<>();

  private WorldviewValidationScope scope() {
    var scope = new WorldviewValidationScope();
    var root = adapt(ROOT, scope);
    scope.addNamespace(root);
    imported.put(root.getUrn(), root);
    return scope;
  }

  private void warmBiology(WorldviewValidationScope scope) {
    scope.addNamespace(adapt("ontology biology using imod in domain imod:Physical version 1.0; "
        + "domain Biology is imod:Knowledge about imod:Life;", scope));
  }

  @Test void freshPreambleCannotSelectItsLaterLocalDomain() {
    var source = adapt(LOCAL, scope());
    var issue = validate(source).stream()
        .filter(n -> n.getMessage().equals("Concept is referenced before its declaration: biology:Biology"))
        .findFirst().orElseThrow();
    assertSource(issue, LOCAL, "biology:Biology");
  }

  @Test void oldSnapshotCannotHideLaterPreambleDeclarationOrItsLocation() {
    var scope = scope();
    warmBiology(scope);
    var source = adapt(LOCAL, scope);
    var issue = validate(source).stream()
        .filter(n -> n.getMessage().equals("Concept is referenced before its declaration: biology:Biology"))
        .findFirst().orElseThrow();
    assertSource(issue, LOCAL, "biology:Biology");
    var reference = reference(source.getDomain(), "biology:Biology");
    assertNotNull(reference);
    assertEquals(LOCAL.indexOf("biology:Biology"), reference.getOffsetInDocument());
    assertEquals("biology:Biology".length(), reference.getLength());
  }

  @Test void anOldDeletedDomainIsStillUndefinedAtItsCurrentSourceLocation() {
    var scope = scope();
    warmBiology(scope);
    var text = "ontology biology using imod in domain biology:Biology version 1.0; "
        + "domain Different is imod:Physical;";
    var issue = validate(adapt(text, scope)).stream()
        .filter(n -> n.getMessage().equals("Undefined concept: biology:Biology"))
        .findFirst().orElseThrow();
    assertSource(issue, text, "biology:Biology");
  }

  @Test void anEarlierImportedNamedDomainAndItsInheritedSpecializationRemainValid() {
    var scope = scope();
    var domains = adapt("ontology domains using imod in domain imod:Physical version 1.0; "
        + "domain Biology is imod:Knowledge about imod:Life;", scope);
    scope.addNamespace(domains);
    imported.put(domains.getUrn(), domains);
    var text = "ontology biology using imod,domains in domain domains:Biology version 1.0; "
        + "domain Specialized is domains:Biology;";
    var source = adapt(text, scope);
    assertTrue(validate(source).isEmpty());
    var selected = reference(source.getDomain(), "domains:Biology");
    assertEquals(text.indexOf("domains:Biology"), selected.getOffsetInDocument());
    assertEquals("domains:Biology".length(), selected.getLength());
  }

  @Test void anEarlierLocalDomainCanBeSpecializedAfterAnImportedPreamble() {
    var text = "ontology biology using imod in domain imod:Physical version 1.0; "
        + "domain Earlier is imod:Knowledge about imod:Life; domain Later is biology:Earlier;";
    var source = adapt(text, scope());
    assertTrue(validate(source).isEmpty());
    var parent = reference(source.getStatements().getLast().getDeclaredParent(), "biology:Earlier");
    assertEquals(text.indexOf("biology:Earlier"), parent.getOffsetInDocument());
    assertEquals("biology:Earlier".length(), parent.getLength());
  }

  private void assertSource(Notification issue, String text, String name) {
    assertEquals(text.indexOf(name), issue.getLexicalContext().getOffsetInDocument());
    assertEquals(name.length(), issue.getLexicalContext().getLength());
    assertEquals("biology", issue.getLexicalContext().getDocumentUrn());
    assertEquals("imod", issue.getLexicalContext().getProjectUrn());
  }

  private KimConcept reference(KimConcept concept, String name) {
    if (concept == null) return null;
    if (name.equals(concept.getName())) return concept;
    var found = reference(concept.getObservable(), name);
    if (found != null) return found;
    for (var about : concept.getAbout()) {
      found = reference(about, name);
      if (found != null) return found;
    }
    return null;
  }

  private List<Notification> validate(KimOntology source) {
    var visitor = new KimOntologyVisitor(null, (urn, type, context) -> {
      if (type == KlabAsset.KnowledgeClass.ONTOLOGY) return imported.get(urn);
      for (var ontology : imported.values()) for (var statement : ontology.getStatements())
        if ((ontology.getUrn() + ":" + statement.getUrn()).equals(urn)) return statement;
      return null;
    });
    visitor.visit(source);
    return visitor.getNotifications();
  }

  private KimOntology adapt(String text, WorldviewValidationScope scope) {
    var parser = new WorldviewStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
    var parsed = parser.parse(new StringReader(text));
    assertFalse(parsed.hasSyntaxErrors());
    var syntax = new OntologySyntaxImpl((Ontology) parsed.getRootASTElement(), scope) {
      @Override protected void logWarning(ParsedObject p, EObject o, EStructuralFeature f, String m) {}
      @Override protected void logError(ParsedObject p, EObject o, EStructuralFeature f, String m) { fail(m); }
    };
    return LanguageAdapter.INSTANCE.adaptOntology(syntax, "imod", List.of(), 0L);
  }
}
