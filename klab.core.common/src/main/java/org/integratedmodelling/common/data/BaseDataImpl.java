package org.integratedmodelling.common.data;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.common.data.Instance;

/**
 * The base implementation for a {@link Data} interface, used when the observation it describes is
 * not a quality.
 */
public class BaseDataImpl implements Data {

  private final Instance instance;
  private final String semantics;
  private final Geometry geometry;
  private final String name;
  private boolean empty = false;
  private Map<Integer, String> dataKey;
  private Metadata metadata = Metadata.create();

  public BaseDataImpl(Observable observable, Geometry geometry, String name, Instance instance) {
    this.semantics = observable.getUrn();
    this.geometry = geometry;
    this.name = name;
    this.instance = instance;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public String semantics() {
    return semantics;
  }

  @Override
  public Geometry geometry() {
    return geometry;
  }

  @Override
  public Metadata metadata() {
    return metadata;
  }

  @Override
  public boolean empty() {
    return empty;
  }

  @Override
  public List<Notification> notifications() {
    return List.of();
  }

  @Override
  public List<Data> states() {
    return List.of();
  }

  @Override
  public List<Data> objects() {
    if (instance.getInstances() == null || instance.getInstances().isEmpty()) {
      return List.of();
    }
    return instance.getInstances().stream().map(BaseDataImpl::create).toList();
  }

  @Override
  public FillCurve fillCurve() {
    return null;
  }

  @Override
  public Map<Integer, String> dataKey() {
    return dataKey;
  }

  @Override
  public long size() {
    return 0;
  }

  public void setEmpty(boolean empty) {
    this.empty = empty;
  }

  public Map<Integer, String> getDataKey() {
    return dataKey;
  }

  public void setDataKey(Map<Integer, String> dataKey) {
    this.dataKey = dataKey;
  }

  public Metadata getMetadata() {
    return metadata;
  }

  public void setMetadata(Metadata metadata) {
    this.metadata = metadata;
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

  public static Data create(Instance instance) {

    //        var Observable = scope.getService(Reasoner.class).resolveObservable(instance.)

    if (instance.getDoubleData() != null) {}

    return null; // FAAAK
  }
}
