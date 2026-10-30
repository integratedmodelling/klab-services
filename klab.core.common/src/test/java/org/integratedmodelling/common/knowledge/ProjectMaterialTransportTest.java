package org.integratedmodelling.common.knowledge;

import static org.junit.jupiter.api.Assertions.*;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectMaterial;
import org.junit.jupiter.api.Test;

class ProjectMaterialTransportTest {
  @Test void transportsDirectoryListingsThroughTheSameMaterialContract() throws Exception {
    var mapper = JacksonConfiguration.newObjectMapper();
    var root = ProjectMaterial.coordinates("example/");
    var folder = ProjectMaterial.coordinates("example/assets/");
    root.getEntries().add(folder);
    folder.getEntries().add(new ProjectMaterial("example", "assets/style.css", null));
    var restored = mapper.readValue(mapper.writeValueAsString(root), ProjectMaterial.class);
    assertTrue(restored.isDirectory());
    assertEquals("example/", restored.getUrn());
    assertThrows(IllegalArgumentException.class, () -> ProjectMaterial.coordinates("example//"));
    assertEquals("example/assets/", restored.getEntries().getFirst().getUrn());
    assertTrue(restored.getEntries().getFirst().isDirectory());
    assertNull(restored.getEntries().getFirst().getEntries().getFirst().getContent());
    for (var reserved : java.util.List.of("src", "apps", "resources", "META-INF", "docs"))
      assertThrows(IllegalArgumentException.class, () -> ProjectMaterial.coordinates("example/" + reserved + "/"));
  }
  @Test void preservesBinaryContentAndCanonicalCoordinatesInTheCrudPayload() throws Exception {
    var mapper = JacksonConfiguration.newObjectMapper();
    var value = new ProjectMaterial("example", "review/figure 1.bin", new byte[]{0, -1, 42});
    var restored = mapper.readValue(mapper.writeValueAsString(value), ProjectMaterial.class);
    assertEquals(value.getUrn(), restored.getUrn()); assertArrayEquals(value.getContent(), restored.getContent());
    assertEquals(KlabAsset.KnowledgeClass.ADDITIONAL_MATERIAL, KlabAsset.classify(restored));
    assertEquals(ProjectMaterial.class, KlabAsset.KnowledgeClass.ADDITIONAL_MATERIAL.getAssetClass());
    assertThrows(Exception.class, () -> mapper.readValue("{\"projectName\":\"example\",\"path\":\"../secret\"}", ProjectMaterial.class));
  }
}
