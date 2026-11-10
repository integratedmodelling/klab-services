package org.integratedmodelling.klab.services.resources.persistence;

import static org.junit.jupiter.api.Assertions.*;
import org.integratedmodelling.klab.api.lang.kim.impl.KimModelImpl;
import org.junit.jupiter.api.Test;

class LearningModelCatalogExclusionTest {
  @Test void learnersNeverProducePredictiveDescriptorsOrContactTheReasoner() {
    var model = new KimModelImpl();
    model.setLearningModel(true);
    model.setLearnedResourceId("trained");
    assertTrue(new ModelDescriptorFactory(null).inferModels(model, null).isEmpty());
  }
}
