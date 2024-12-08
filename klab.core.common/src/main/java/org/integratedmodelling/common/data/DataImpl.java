package org.integratedmodelling.common.data;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.io.InputStream;
import java.util.List;

public class DataImpl implements Data  {

    @Override
    public boolean isEmpty() {
        return false;
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

    public void copyTo(InputStream dataStream) {

    }
}
