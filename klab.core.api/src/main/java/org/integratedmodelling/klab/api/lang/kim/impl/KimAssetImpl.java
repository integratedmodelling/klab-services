package org.integratedmodelling.klab.api.lang.kim.impl;

import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.kim.KimAsset;

import java.util.ArrayList;
import java.util.List;

public class KimAssetImpl implements KimAsset, java.io.Serializable {

    @java.io.Serial private static final long serialVersionUID = 1L;

    private int offsetInDocument;
    private int length;
    private List<Annotation> annotations = new ArrayList<>();
    private String deprecation;
    private boolean deprecated;
    private String projectName;
    private String serviceId;

    public KimAssetImpl() {}

    public KimAssetImpl(KimAssetImpl other) {
        this.offsetInDocument = other.offsetInDocument;
        this.length = other.length;
        this.annotations.addAll(KimSyntaxCopy.copy(other.annotations));
        this.deprecated = other.deprecated;
        this.deprecation = other.deprecation;
        this.projectName = other.projectName;
        this.serviceId = other.serviceId;
    }

    @Override
    public List<Annotation> getAnnotations() {
        return this.annotations;
    }

    @Override
    public String getDeprecation() {
        return this.deprecation;
    }

    @Override
    public boolean isDeprecated() {
        return this.deprecated;
    }

    public void setAnnotations(List<Annotation> annotations) {
        this.annotations = annotations;
    }

    public void setDeprecation(String deprecation) {
        this.deprecation = deprecation;
    }

    public void setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    @Override
    public int getOffsetInDocument() {
        return offsetInDocument;
    }

    public void setOffsetInDocument(int offsetInDocument) {
        this.offsetInDocument = offsetInDocument;
    }

    @Override
    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }
}
