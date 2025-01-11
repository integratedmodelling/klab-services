package org.integratedmodelling.common.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.PrimitiveIterator;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.common.data.Instance;

/**
 * Implementation wrapping an {@link org.integratedmodelling.klab.common.data.Instance} for
 * marshalling across Avro endpoints.
 *
 * <p>Constructor taking an {@link org.integratedmodelling.klab.common.data.Instance} is to expose
 * an existing data packet from a service. Constructor taking an {@link InputStream} creates the
 * {@link org.integratedmodelling.klab.common.data.Instance} and can send it over to a service.
 */
public class DataImpl implements Data {

  private final Instance instance;

  public DataImpl(Instance instance) {
    this.instance = instance;
  }

  @Override
  public PrimitiveIterator.OfLong getFillCurve(FillCurve curve) {
    return null;
  }

  @Override
  public boolean isEmpty() {
    return instance == null || instance.getEmpty();
  }

  @Override
  public Artifact.Type getArtifactType() {
    return null;
  }

  @Override
  public List<Notification> getNotifications() {
    return List.of();
  }

  @Override
  public int getObjectCount() {
    return instance.getInstances().size();
  }

  @Override
  public int getStateCount() {
    return instance.getStates().size();
  }

  @Override
  public Scale getObjectScale(int i) {
    return Scale.create(Geometry.create(instance.getInstances().get(i).getGeometry().toString()));
  }

  @Override
  public String getObjectName(int i) {
    return instance.getInstances().get(i).getName().toString();
  }

  @Override
  public Metadata getObjectMetadata(int i) {
    return null;
  }

  @Override
  public Concept getSemantics() {
    return null;
  }

  @Override
  public Metadata getMetadata() {
    return null;
  }

  public Instance asInstance() {
    return this.instance;
  }

  public void copyTo(OutputStream dataStream) {
    try {
      var encoder = EncoderFactory.get().binaryEncoder(dataStream, null);
      var writer = new SpecificDatumWriter<>(Instance.class);
      writer.write(this.instance, encoder);
      encoder.flush();
    } catch (IOException e) {
      throw new KlabIOException(e);
    }
  }
}
