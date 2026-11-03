package org.integratedmodelling.common.data.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.kim.impl.KlabDocumentImpl;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimObservationStrategiesImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.junit.jupiter.api.Test;

/** DTO serializer compatibility only: does not invoke/claim Resources parser acceptance. */
class SourcePreviewWireTest {
  @Test
  void sourcePreviewIdentityFieldsMatchProductionDocumentDtos() throws Exception {
    var mapper = JacksonConfiguration.newObjectMapper();
    var actual = mapper.createArrayNode();
    List<KlabDocumentImpl<?>> documents = List.of(new KimNamespaceImpl(), new KimOntologyImpl(),
        new KimObservationStrategiesImpl(), new KActorsBehaviorImpl());
    List<String> kinds = List.of("NAMESPACE", "ONTOLOGY", "OBSERVATION_STRATEGY_DOCUMENT", "BEHAVIOR");
    for (int i = 0; i < documents.size(); i++) {
      var document = documents.get(i);
      assertTrue(KlabAsset.KnowledgeClass.valueOf(kinds.get(i)).getAssetClass().isInstance(document));
      document.setUrn("test.preview"); document.setServiceId("resources-fixture");
      document.setSourceCode("DTO-only source, not parser acceptance\n");
      var serialized = mapper.readTree(Utils.Json.asString(document));
      var row = actual.addObject(); row.put("kind", kinds.get(i));
      var fields = row.putObject("document");
      for (var name : List.of("@CLASS", "urn", "serviceId", "sourceCode", "notifications")) {
        fields.set(name, serialized.path(name));
      }
      assertEquals(document.getClass().getName(), fields.path("@CLASS").asText());
    }
    Files.write(Path.of("target/source-preview-wire-actual.json"),
        mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(actual));
    try (var expected = getClass().getResourceAsStream("/source-preview-wire.json")) {
      assertEquals(mapper.readTree(expected), actual);
    }
  }
}
