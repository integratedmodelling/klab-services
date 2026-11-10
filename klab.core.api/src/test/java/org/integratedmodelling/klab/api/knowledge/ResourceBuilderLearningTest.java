package org.integratedmodelling.klab.api.knowledge;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ResourceBuilderLearningTest {
  @Test void preservesHistoryPayloadPathsAndEachOutputExactlyOnce() {
    var old = Resource.builder("local:test:training:model").build();
    var resource = Resource.builder(old.getUrn()).addHistory(old).addLocalResourcePath("trained.bin")
        .withOutput("prediction", Artifact.Type.NUMBER).build();
    assertEquals(1, resource.getHistory().size());
    assertSame(old, resource.getHistory().getFirst());
    assertEquals(1, resource.getOutputs().size());
    assertEquals("trained.bin", resource.getLocalFiles().getFirst().getPath());
  }
}
