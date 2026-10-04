package org.integratedmodelling.klab.services.resources.persistence;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.exceptions.KlabStorageException;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Projection;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Shape;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.runtime.scale.space.ShapeImpl;

/** Versioned, lossless descriptor payload; reasoner objects are reconstructed, never serialized. */
final class ModelReferenceCodec {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  static {
    JacksonConfiguration.configureObjectMapperForKlabTypes(MAPPER);
    MAPPER.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
    MAPPER.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
  }

  static String encode(ModelReference model) {
    try {
      ObjectNode root = MAPPER.createObjectNode();
      root.put("version", 1);
      ObjectNode data = MAPPER.valueToTree(model);
      data.remove(java.util.List.of("shape", "observableConcept"));
      root.set("model", data);
      if (model.getObservableConcept() != null) root.put("concept", model.getObservableConcept().getUrn());
      if (model.getShape() != null) root.put("shape",
          ShapeImpl.promote(model.getShape()).getStandardizedGeometry().toText());
      return MAPPER.writeValueAsString(root);
    } catch (Exception e) {
      throw new KlabStorageException(e);
    }
  }

  static ModelReference decode(String payload, Reasoner reasoner) {
    try {
      var root = MAPPER.readTree(payload);
      if (root.path("version").asInt() != 1) throw new IllegalArgumentException("Unsupported model descriptor version");
      var model = MAPPER.treeToValue(root.get("model"), ModelReference.class);
      if (root.hasNonNull("shape")) model.setShape(Shape.create(root.get("shape").asText(), Projection.getLatLon()));
      if (reasoner != null && root.hasNonNull("concept")) model.setObservableConcept(reasoner.resolveConcept(root.get("concept").asText()));
      return model;
    } catch (Exception e) {
      throw new KlabStorageException(e);
    }
  }
}
