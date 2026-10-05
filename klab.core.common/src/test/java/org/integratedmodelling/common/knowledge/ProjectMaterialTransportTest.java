package org.integratedmodelling.common.knowledge;

import static org.junit.jupiter.api.Assertions.*;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectMaterial;
import org.junit.jupiter.api.Test;

class ProjectMaterialTransportTest {
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
