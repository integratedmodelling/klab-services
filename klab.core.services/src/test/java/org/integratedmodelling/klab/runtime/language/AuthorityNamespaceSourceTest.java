package org.integratedmodelling.klab.runtime.language;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.junit.jupiter.api.Test;

class AuthorityNamespaceSourceTest {
  private KimOntologyImpl source(String namespace, String reference) {
    var ontology = new KimOntologyImpl(); ontology.setUrn("w");
    var root = new KimConceptStatementImpl(); root.setUrn("Root"); root.getType().add(SemanticType.IDENTITY);
    root.setAuthorityRequired("TEST"); root.getAuthorityParameters().put("urn", "provider.test");
    root.getAuthorityParameters().put("codelists", Map.of("catalog",namespace)); ontology.getStatements().add(root);
    var alias = new KimConceptStatementImpl(); alias.setUrn("Alias"); alias.setAlias(true); alias.getType().add(SemanticType.IDENTITY);
    var parent = new KimConceptImpl(); parent.setName(reference); parent.getType().add(SemanticType.NOTHING);
    alias.setDeclaredParent(parent); ontology.getStatements().add(alias); return ontology;
  }
  @Test void sourceRecognizesDeclaredNamespaceWithoutGrantingProviderCodeExistence() {
    for (String namespace : List.of("land.igbp","un.m49")) {
      var source = source(namespace, namespace+":Entry"); var visitor = new KimOntologyVisitor(); visitor.visit(source);
      assertTrue(visitor.getNotifications().isEmpty(), visitor.getNotifications().toString());
      assertTrue(source.getStatements().getLast().getDeclaredParent().is(SemanticType.NOTHING));
    }
  }
  @Test void authoritativeCallerCanRestrictProvisionalSourceRecognition() {
    var source = source("land.igbp", "land.igbp:Entry");
    var validator = new KimWorldviewValidator(new WorldviewDeclarationRegistry(List.of(source)), urn -> false);
    var visitor = new KimOntologyVisitor(validator, null); visitor.visit(source);
    assertTrue(visitor.getNotifications().stream().anyMatch(n -> n.getMessage().startsWith("Undefined concept:")));
    assertTrue(visitor.getNotifications().stream().anyMatch(n -> n.getMessage().contains("unknown or semantically inconsistent")));
  }
  @Test void undeclaredMalformedAndOrdinaryNamespacesAreNotAuthorityPlaceholders() {
    for (String name : List.of("other:Entry","land.igbp:","land.igbp:A:B","w:Entry")) {
      var visitor = new KimOntologyVisitor(); visitor.visit(source("land.igbp",name));
      assertFalse(visitor.getNotifications().isEmpty(),name);
    }
  }
}
