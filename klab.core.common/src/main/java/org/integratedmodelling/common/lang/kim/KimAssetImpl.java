package org.integratedmodelling.common.lang.kim;

import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.kim.KimAsset;

import java.util.ArrayList;
import java.util.List;

public class KimAssetImpl implements KimAsset {

    private int offsetInDocument;
    private int length;
    private List<Annotation> annotations = new ArrayList<>();
    private String deprecation;
    private boolean deprecated;


    public KimAssetImpl() {}

    public KimAssetImpl(KimAssetImpl other) {
        this.offsetInDocument = other.offsetInDocument;
        this.length = other.length;
        this.annotations.addAll(other.annotations);
        this.deprecated = other.deprecated;
        this.deprecation = other.deprecation;
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
}
