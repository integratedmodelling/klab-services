package org.integratedmodelling.klab.services.resources.lang;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.junit.jupiter.api.Test;

class WorldviewAuthorityValidatorTest {
  final Extensions.AuthorityDescriptor descriptor = new Extensions.AuthorityDescriptor(
      "provider.taxa", Version.create("1.0.0"), true, true, List.of("GENUS"), List.of());

  Extensions.ComponentDescriptor component(String id) {
    var component = mock(Extensions.ComponentDescriptor.class);
    when(component.id()).thenReturn(id);
    when(component.version()).thenReturn(Version.create("2.0.0"));
    when(component.authorities()).thenReturn(List.of(descriptor));
    return component;
  }
  KimConceptStatementImpl statement(String local, String id) {
    var statement = new KimConceptStatementImpl();
    statement.setUrn(id);
    statement.setNamespace("test");
    statement.setAuthorityRequired(local);
    statement.getType().add(SemanticType.IDENTITY);
    statement.getAuthorityParameters().put("urn", "provider.taxa");
    return statement;
  }
  WorldviewImpl worldview(KimConceptStatementImpl... statements) {
    var worldview = new WorldviewImpl();
    worldview.setUrn("test");
    var ontology = new KimOntologyImpl();
    ontology.setUrn("test");
    ontology.getStatements().addAll(List.of(statements));
    worldview.getOntologies().add(ontology);
    return worldview;
  }
  @Test void codelistNamespacesHaveIdentitySyntaxAndCannotShadowOntologies() {
    var root = statement("TAXA", "Identity");
    root.getAuthorityParameters().put("codelists", Map.of("species", "taxonomy.species"));
    var worldview = worldview(root);
    WorldviewAuthorityValidator.validate(worldview, List.of(component("taxa")));
    assertEquals(1, worldview.getAuthorityBindings().size());
    var scope = new WorldviewValidationScope(worldview);
    assertEquals(org.integratedmodelling.languages.api.SemanticSyntax.Type.IDENTITY,
        scope.getConceptDescriptor("taxonomy.species:FelisCatus").mainType());
    scope.clearNamespace("test");
    assertNotEquals(org.integratedmodelling.languages.api.SemanticSyntax.Type.IDENTITY,
        scope.getConceptDescriptor("taxonomy.species:FelisCatus").mainType());
    root.getAuthorityParameters().put("codelists", Map.of("species", "test"));
    WorldviewAuthorityValidator.validate(worldview, List.of(component("taxa")));
    assertTrue(worldview.getAuthorityBindings().isEmpty());
  }

  @Test void nestedDeclarationCarriesSelectedDescriptorAndLocationWithoutParameters() {
    var parent = statement(null, "Parent");
    var child = statement("TAXA", "Species");
    child.setOffsetInDocument(40); child.setLength(80);
    child.getAuthorityParameters().put("password", "secret");
    parent.getChildren().add(child);
    var worldview = worldview(parent);
    WorldviewAuthorityValidator.validate(worldview, List.of(component("component.taxa")));
    assertFalse(worldview.isEmpty());
    var binding = worldview.getAuthorityBindings().getFirst();
    assertEquals("TAXA", binding.localId());
    assertEquals("test:Species", binding.rootIdentity());
    assertEquals(40, binding.sourceOffset());
    assertEquals(descriptor, binding.provider());
    assertEquals(List.of("GENUS"), binding.provider().subAuthorities());
    assertFalse(binding.toString().contains("secret"));
    assertNull(binding.reasonerUrl());
  }
  @Test void missingAmbiguousAndInvalidRootsAreRejected() {
    for (var components : List.of(List.<Extensions.ComponentDescriptor>of(),
        List.of(component("a"), component("b")))) {
      var worldview = worldview(statement("TAXA", "Species"));
      WorldviewAuthorityValidator.validate(worldview, components);
      assertTrue(worldview.isEmpty()); assertTrue(worldview.getAuthorityBindings().isEmpty());
      assertFalse(worldview.getNotifications().isEmpty());
    }
    var invalid = statement("TAXA", "Species"); invalid.getType().clear();
    var worldview = worldview(invalid);
    WorldviewAuthorityValidator.validate(worldview, List.of(component("a")));
    assertTrue(worldview.isEmpty()); assertTrue(worldview.getAuthorityBindings().isEmpty());
  }
  @Test void duplicateNamesRemoveBothBindingsAndRemovedDeclarationsDoNotLinger() {
    var worldview = worldview(statement("TAXA", "Species"), statement("TAXA", "Other"));
    WorldviewAuthorityValidator.validate(worldview, List.of(component("a")));
    assertTrue(worldview.isEmpty()); assertTrue(worldview.getAuthorityBindings().isEmpty());
    worldview = worldview(statement("TAXA", "Species"));
    WorldviewAuthorityValidator.validate(worldview, List.of(component("a")));
    assertEquals(1, worldview.getAuthorityBindings().size());
    worldview.getOntologies().clear();
    WorldviewAuthorityValidator.validate(worldview, List.of(component("a")));
    assertTrue(worldview.getAuthorityBindings().isEmpty());
  }
  @Test void explicitVersionDoesNotSelectAnotherVersion() {
    var declaration = statement("TAXA", "Species");
    declaration.getAuthorityParameters().put("urn", "provider.taxa@2.0.0");
    var worldview = worldview(declaration);
    WorldviewAuthorityValidator.validate(worldview, List.of(component("a")));
    assertTrue(worldview.isEmpty());
  }
}
