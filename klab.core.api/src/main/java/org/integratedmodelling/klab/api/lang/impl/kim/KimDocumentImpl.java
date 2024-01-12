package org.integratedmodelling.klab.api.lang.impl.kim;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.lang.Statement;
import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class KimDocumentImpl<T extends Statement> implements KlabDocument<T> {
    private String urn;
    private Version version;
    private Metadata metadata = Metadata.create();
//    private List<Annotation> annotations = new ArrayList<>();
    private long creationTimestamp;
    private long lastUpdateTimestamp;
    private boolean inactive;
    private List<Notification> notifications = new ArrayList<>();
    private String projectName;
    private String sourceCode;

    @Override
    public String getUrn() {
        return this.urn;
    }

    @Override
    public Version getVersion() {
        return this.version;
    }

    @Override
    public Metadata getMetadata() {
        return this.metadata;
    }
//
//    @Override
//    public List<Annotation> getAnnotations() {
//        return this.annotations;
//    }

    @Override
    public long getCreationTimestamp() {
        return this.creationTimestamp;
    }

    @Override
    public long getLastUpdateTimestamp() {
        return this.lastUpdateTimestamp;
    }

    @Override
    public boolean isInactive() {
        return this.inactive;
    }
    @Override
    public Collection<Notification> getNotifications() {
        return this.notifications;
    }

    @Override
    public String getProjectName() {
        return this.projectName;
    }

    @Override
    public String getSourceCode() {
        return this.sourceCode;
    }

    public void setUrn(String urn) {
        this.urn = urn;
    }

    public void setVersion(Version version) {
        this.version = version;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }
//
//    public void setAnnotations(List<Annotation> annotations) {
//        this.annotations = annotations;
//    }

    public void setCreationTimestamp(long creationTimestamp) {
        this.creationTimestamp = creationTimestamp;
    }

    public void setLastUpdateTimestamp(long lastUpdateTimestamp) {
        this.lastUpdateTimestamp = lastUpdateTimestamp;
    }

    public void setInactive(boolean inactive) {
        this.inactive = inactive;
    }

    public void setNotifications(List<Notification> notifications) {
        this.notifications = notifications;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }
}