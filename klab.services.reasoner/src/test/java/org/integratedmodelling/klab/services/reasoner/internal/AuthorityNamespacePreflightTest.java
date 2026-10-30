package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.junit.jupiter.api.Test;

class AuthorityNamespacePreflightTest {
  private WorldviewImpl fixture(String authority, String namespace, String reference) {
    var ontology = new KimOntologyImpl(); ontology.setUrn("w"); ontology.setSourceCode("source " + authority);
    var root = new KimConceptStatementImpl(); root.setUrn("Root"); root.setNamespace("w");
    root.getType().add(SemanticType.IDENTITY); root.setAuthorityRequired(authority);
    root.getAuthorityParameters().put("urn", "provider.test@1.0.0");
    root.getAuthorityParameters().put("codelists", Map.of("catalog", namespace));
    ontology.getStatements().add(root);
    var alias = new KimConceptStatementImpl(); alias.setUrn("Alias"); alias.setNamespace("w");
    alias.getType().add(SemanticType.IDENTITY); alias.setAlias(true);
    var parent = new KimConceptImpl(); parent.setName(reference); parent.getType().add(SemanticType.NOTHING);
    alias.setDeclaredParent(parent); ontology.getStatements().add(alias);
    var world = new WorldviewImpl(); world.getOntologies().add(ontology);
    var descriptor = new Extensions.AuthorityDescriptor("provider.test", Version.create("1.0.0"), true, true, List.of(), List.of());
    world.setAuthorityBindings(List.of(new Worldview.AuthorityBinding(authority, "w:Root", "w", 0, 0,
        SemanticValidationRequest.sourceHash(ontology.getSourceCode()), descriptor, "component", Version.create("1.0.0"), null)));
    return world;
  }

  @Test void landAndUnNamespacesAdmitProvisionalIdentitiesWithoutResolvingProviderCodes() {
    for (var pair : List.of(List.of("LAND", "land.igbp", "ClosedShrubland"), List.of("UN.M49", "un.m49", "Spain"))) {
      var world = fixture(pair.get(0), pair.get(1), pair.get(1)+":"+pair.get(2));
      assertTrue(BeanIngestionPreflight.errors(world).isEmpty());
      var sourceParent = world.getOntologies().getFirst().getStatements().getLast().getDeclaredParent();
      assertTrue(sourceParent.is(SemanticType.NOTHING), "validation must not modify submitted beans");
      var owned = BeanIngestionPreflight.snapshot(world);
      var parent = owned.getOntologies().getFirst().getStatements().getLast().getDeclaredParent();
      assertTrue(parent.is(SemanticType.IDENTITY)); assertFalse(parent.is(SemanticType.NOTHING));
    }
    // This is deliberately a shape/type admission, not an existence grant. Host tests reject it.
    assertTrue(BeanIngestionPreflight.errors(fixture("LAND", "land.igbp", "land.igbp:NoSuchEntry")).isEmpty());
  }

  @Test void missingAndStaleBindingsCannotGrantNamespaceOrType() {
    var unbound = fixture("LAND", "land.igbp", "land.igbp:ClosedShrubland");
    unbound.setAuthorityBindings(List.of()); assertFalse(BeanIngestionPreflight.errors(unbound).isEmpty());
    var stale = fixture("LAND", "land.igbp", "land.igbp:ClosedShrubland");
    ((KimOntologyImpl) stale.getOntologies().getFirst()).setSourceCode("changed source");
    assertFalse(BeanIngestionPreflight.errors(stale).isEmpty());
    assertTrue(BeanIngestionPreflight.snapshot(stale).getOntologies().getFirst().getStatements().getLast()
        .getDeclaredParent().is(SemanticType.NOTHING));
  }

  @Test void directAuthorityTerminalsRequireTheExactCurrentBindingAndBoundedCodeSyntax() {
    for (String terminal : List.of("UN.M49:[https://unstats.un.org/classifications/geo/M49/724]", "UN.M49:[001]"))
      assertTrue(BeanIngestionPreflight.errors(fixture("UN.M49", "un_geography", terminal)).isEmpty(), terminal);
    for (String terminal : List.of("UN.UNKNOWN:[724]", "UN.M49:[unclosed", "UN.M49:[bad\\q]", "UN.M49:"))
      assertFalse(BeanIngestionPreflight.errors(fixture("UN.M49", "un_geography", terminal)).isEmpty(), terminal);
    var unbound = fixture("UN.M49", "un_geography", "UN.M49:[001]");
    unbound.setAuthorityBindings(List.of());
    assertFalse(BeanIngestionPreflight.errors(unbound).isEmpty());
  }

  @Test void rootAndProviderChangesInvalidateBinding() {
    for (String change : List.of("root", "provider", "version", "catalog")) {
      var world = fixture("LAND", "land.igbp", "land.igbp:ClosedShrubland");
      var root = (KimConceptStatementImpl)world.getOntologies().getFirst().getStatements().getFirst();
      if (change.equals("root")) root.setUrn("OtherRoot");
      else if (change.equals("catalog")) {
        // A legitimate catalog change changes the source hash as well as the transported map.
        root.getAuthorityParameters().put("codelists", Map.of("other", "land.igbp"));
        ((KimOntologyImpl)world.getOntologies().getFirst()).setSourceCode("changed catalog");
      } else root.getAuthorityParameters().put("urn", change.equals("version")?"provider.test@2.0.0":"other.provider@1.0.0");
      assertFalse(BeanIngestionPreflight.errors(world).isEmpty(), change);
    }
  }

  @Test void malformedAndUnboundNamesStayUndefined() {
    for (String reference : List.of("land.other:ClosedShrubland", "land.igbp:", "land.igbp:A:B", "land.igbp:bad name"))
      assertFalse(BeanIngestionPreflight.errors(fixture("LAND", "land.igbp", reference)).isEmpty(), reference);
    for (String namespace : List.of("Bad", "land..igbp", "land:igbp"))
      assertFalse(BeanIngestionPreflight.errors(fixture("LAND", namespace, namespace+":Alias")).isEmpty(), namespace);
  }

  @Test void mappedNamespaceCannotShadowAnOrdinaryOntology() {
    var world = fixture("LAND", "land.igbp", "land.igbp:ClosedShrubland");
    var ordinary = new KimOntologyImpl(); ordinary.setUrn("land.igbp"); ordinary.setSourceCode("ordinary");
    world.getOntologies().add(ordinary);
    assertFalse(BeanIngestionPreflight.errors(world).isEmpty());
    var known = new KimConceptStatementImpl(); known.setUrn("ClosedShrubland"); known.getType().add(SemanticType.SUBJECT);
    ordinary.getStatements().add(known);
    // Existing ordinary names retain their declared type; no authority identity is substituted.
    assertTrue(BeanIngestionPreflight.snapshot(world).getOntologies().getFirst().getStatements().getLast()
        .getDeclaredParent().is(SemanticType.NOTHING));
  }

  @Test void currentOverlayCannotBorrowOldValidatedNamespace() {
    var old = fixture("LAND", "land.igbp", "land.igbp:ClosedShrubland");
    var replacement = new KimOntologyImpl(); replacement.setUrn("w"); replacement.setSourceCode("replacement");
    replacement.getStatements().add(old.getOntologies().getFirst().getStatements().getLast());
    assertFalse(BeanIngestionPreflight.errors(List.of(replacement), List.of(old)).isEmpty());
  }
}
