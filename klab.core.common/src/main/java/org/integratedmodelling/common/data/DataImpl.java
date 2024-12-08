package org.integratedmodelling.common.data;

import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.common.data.Instance;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Implementation wrapping an {@link org.integratedmodelling.klab.common.data.Instance} for marshalling across
 * Avro endpoints.
 * <p>
 * Constructor taking an {@link org.integratedmodelling.klab.common.data.Instance} is to expose an existing
 * data packet from a service. Constructor taking an {@link InputStream} creates the
 * {@link org.integratedmodelling.klab.common.data.Instance} and can send it over to a service.
 */
public class DataImpl implements Data {

    private final Instance instance;

    public DataImpl(InputStream inputStream) {
        try {
            var decoder = DecoderFactory.get().binaryDecoder(inputStream, null);
            var reader = new SpecificDatumReader<>(Instance.class);
            this.instance = reader.read(null, decoder);
        } catch (IOException e) {
            throw new KlabIOException(e);
        }
    }

    public DataImpl(Instance instance) {
        this.instance = instance;
    }

    static {
        // TODO read the AVRO schema and instantiate a Datum reader
    }

    @Override
    public boolean isEmpty() {
        return instance == null; /* TODO need to check the instance */
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
        return 0;
    }

    @Override
    public int getStateCount() {
        return 0;
    }

    @Override
    public Scale getObjectScale(int i) {
        return null;
    }

    @Override
    public String getObjectName(int i) {
        return "";
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
