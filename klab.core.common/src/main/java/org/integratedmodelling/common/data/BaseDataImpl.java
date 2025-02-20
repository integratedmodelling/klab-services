package org.integratedmodelling.common.data;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.common.data.Instance;

/**
 * The base implementation for a {@link Data} interface, used when the observation it describes is
 * not a quality. For those the system will build an iterating one of the correct type.
 */
public class BaseDataImpl implements Data {

  protected final Instance instance;
  private final String semantics;
  private final Geometry geometry;
  private final String name;
  private boolean empty = false;
  private Map<Integer, String> dataKey;
  private Metadata metadata = Metadata.create();
  private SpaceFillingCurve spaceFillingCurve;

  public BaseDataImpl(Instance instance) {
    this.instance = instance;
    this.spaceFillingCurve = SpaceFillingCurve.valueOf(instance.getFillingCurve().toString());
    this.semantics = instance.getObservable().toString();
    this.geometry =
        GeometryRepository.INSTANCE.get(instance.getGeometry().toString(), Geometry.class);
    this.name = instance.getName().toString();
  }

  public BaseDataImpl(Observable observable, Geometry geometry, String name, Instance instance) {
    this.semantics = observable.getUrn();
    this.geometry = geometry;
    this.name = name;
    this.instance = instance;
    this.spaceFillingCurve = SpaceFillingCurve.D1_LINEAR;
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
  public List<Data> children() {
    if (instance.getInstances() == null || instance.getInstances().isEmpty()) {
      return List.of();
    }
    return instance.getInstances().stream().map(BaseDataImpl::create).toList();
  }

  @Override
  public SpaceFillingCurve fillCurve() {
    return spaceFillingCurve;
  }

  @Override
  public Map<Integer, String> dataKey() {
    return dataKey;
  }

  @Override
  public long size() {
    return instance.getInstances().size();
  }

  @Override
  public boolean hasStates() {
    return false;
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

  @Override
  public String toString() {
    return "BaseDataImpl{" + semantics + '\'' + ", instance=" + instance + '}';
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

    if (instance.getDoubleData() != null) {
        return new DoubleDataImpl(instance);
    } else if (instance.getFloatData() != null) {
      throw new KlabUnimplementedException("GAAAH");
//      return new FloatDataImpl(instance);
    } else if (instance.getIntData() != null) {
      if (instance.getDataKey() != null) {
        throw new KlabUnimplementedException("GEEEEH");
      }
      return new IntDataImpl(instance);
    } else if (instance.getLongData() != null) {
      return new LongDataImpl(instance);
    }

    return new BaseDataImpl(instance);
  }
}
