package org.integratedmodelling.klab.api.lang;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/**
 * All statements in k.LAB-supported languages are serializables. The resource service maintains the catalog
 * of available projects and resources, managing the transfer of pre-parsed projects with their behaviors and
 * namespaces to the semantic and resolver services.
 *
 * @author mario
 */
public interface Statement extends Serializable {

    /**
     * Each main type of statement exposes a visit() method that takes a specialized visitor descending from
     * this tag interface.
     *
     * @author Ferd
     */
    abstract interface Visitor {

    }

    /**
     * @return the first line number
     * @deprecated use offset in document and length; add transient document() to retrieve the object it's
     * part of
     */
    @Deprecated
    int getFirstLine();

    /**
     * @return the last line number
     */
    @Deprecated
    int getLastLine();

    /**
     * @return the start offset in the document
     */
    @Deprecated
    int getFirstCharOffset();

    /**
     * @return the last offset in the document
     */
    @Deprecated
    int getLastCharOffset();

    /**
     * @return the annotations
     */
    List<Annotation> getAnnotations();

    /**
     * @return the reason for deprecation
     */
    String getDeprecation();

    /**
     * @return true if deprecated
     */
    boolean isDeprecated();

    /**
     * @return the source code
     */
    String sourceCode();

    /**
     * Any errors, warnings or info are reported as notifications. Check error notifications to see if the
     * statement is legal.
     *
     * @return
     */
    Collection<Notification> getNotifications();

    /**
     * @return the metadata
     */
    Metadata getMetadata();

    /**
     * To be specialized downstream.
     *
     * @param visitor
     */
    public void visit(Visitor visitor);
}
