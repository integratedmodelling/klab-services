package org.integratedmodelling.klab.api.lang.kim;

import org.integratedmodelling.klab.api.lang.Annotation;

import java.util.List;

/**
 * Anything originating from a document with source code. Not limited to k.IM but works for all the supported
 * languages.
 */
public interface KimAsset {
    List<Annotation> getAnnotations();

    String getDeprecation();

    boolean isDeprecated();

    int getOffsetInDocument();

    int getLength();
}
