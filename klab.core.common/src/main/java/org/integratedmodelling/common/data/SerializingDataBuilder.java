package org.integratedmodelling.common.data;

import java.util.*;
import java.util.stream.Collectors;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.common.data.Instance;

/**
 * The builder to use when the digital twin is not available locally and a data package is prepared
 * for a remote client to consume. Wraps an Avro-enabled {@link Instance}.
 */
public class SerializingDataBuilder implements Data.Builder {

    private final Instance.Builder builder;
    private Instance.Builder parentBuilder;
    private final Geometry geometry;
    private Data.FillCurve fillCurve;
    private Map<Object, Integer> objectKey;
    private int objectCounter = 1;
    private String adapter;

    public SerializingDataBuilder(String name, Data input, Geometry geometry) {
        this.builder = Instance.newBuilder();
        this.builder.setName(name);
        this.builder.setGeometry(geometry.encode());
        this.builder.setNotifications(new ArrayList<>());
        this.builder.setMetadata(new LinkedHashMap<>());
        this.builder.setInstances(null);
        this.builder.setDoubleData(null);
        this.builder.setFloatData(null);
        this.builder.setIntData(null);
        this.builder.setLongData(null);
        this.builder.setDataKey(null);
        this.geometry = geometry;
    }

    private SerializingDataBuilder(String name, Data input, Geometry geometry, Instance.Builder parentBuilder) {
        this(name, input, geometry);
        this.parentBuilder = parentBuilder;
    }

    @Override
    public Data.Builder notification(Notification notification) {
        var nBuilder = org.integratedmodelling.klab.common.data.Notification.newBuilder();
        //
        this.builder.getNotifications().add(nBuilder.build());
        return this;
    }

    @Override
    public List<Data.Builder> getObjects() {
    return List.of();
    }

    @Override
    public Data.Builder adapter(String adapterId) {
        this.adapter = adapterId;
        return this;
    }

    @Override
    public Data.Builder metadata(String key, Object value) {
        builder.getMetadata().put(key, Utils.Data.asString(value));
        return this;
    }

    @Override
    public Data.Builder state(String observable) {
        return new SerializingDataBuilder(observable, /* TODO DIOPORCO INPUT */ null, this.geometry, this.builder);
    }

    @Override
    public Data.Builder object(String name, Observable observable, Geometry geometry) {
        return new SerializingDataBuilder(name, /* TODO DIOPORCO INPUT */ null, geometry, this.builder);
    }

    @Override
    public <T extends Storage.Scanner> T scanner(Class<T> scannerClass) {
        return null;
    }

    @Override
    public <T extends Storage.Scanner> T scanner(String identifier, Class<T> scannerClass) {
        return null;
    }

    @Override
    public Observation getObservation() {
        return null;
    }

    //  @Override
    public Data build() {
        if (objectKey != null) {
            builder.setDataKey(
                    objectKey.entrySet().stream()
                            .map(e -> Map.entry(e.getKey().toString(), e.getValue().toString()))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        }
        var instance = builder.build();
        if (parentBuilder != null) {
            if (parentBuilder.getInstances() == null) {
                parentBuilder.setInstances(new ArrayList<>());
            }
            parentBuilder.getInstances().add(instance);
        }
        return BaseDataImpl.create(instance);
    }
}
