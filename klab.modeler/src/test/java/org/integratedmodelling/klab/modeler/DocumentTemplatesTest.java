package org.integratedmodelling.klab.modeler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.junit.jupiter.api.Test;

class DocumentTemplatesTest {

  @Test
  void everySupportedDocumentTypeHasAClientSideTemplate() throws IOException {
    var declarations =
        Map.of(
            ProjectStorage.ResourceType.ONTOLOGY, "ontology test.asset",
            ProjectStorage.ResourceType.MODEL_NAMESPACE, "namespace test.asset",
            ProjectStorage.ResourceType.STRATEGY, "strategies test.asset",
            ProjectStorage.ResourceType.BEHAVIOR, "behavior test.asset",
            ProjectStorage.ResourceType.BEHAVIOR_COMPONENT, "component test.asset",
            ProjectStorage.ResourceType.APPLICATION, "app test.asset",
            ProjectStorage.ResourceType.SCRIPT, "script test.asset",
            ProjectStorage.ResourceType.TESTCASE, "testcase test.asset");

    for (var entry : declarations.entrySet()) {
      var rendered = DocumentTemplates.render(entry.getKey(), "test.asset");
      assertTrue(rendered.contains(entry.getValue()), entry.getKey().name());
      assertFalse(rendered.contains("{urn}"), entry.getKey().name());

      try (var input = DocumentTemplates.renderUrl(entry.getKey(), "test.asset").openStream()) {
        assertTrue(
            new String(input.readAllBytes(), StandardCharsets.UTF_8).contains(entry.getValue()),
            entry.getKey().name());
      }

      var editedSource = entry.getValue() + "\n// edited\n";
      try (var input =
          DocumentTemplates.sourceUrl(entry.getKey(), "test.asset", editedSource).openStream()) {
        assertEquals(
            editedSource,
            new String(input.readAllBytes(), StandardCharsets.UTF_8),
            entry.getKey().name());
      }
    }
  }
}
