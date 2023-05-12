package org.integratedmodelling.klab.api.knowledge;

import org.integratedmodelling.klab.api.exceptions.KIllegalArgumentException;

/**
 * All knowledge in k.LAB has a URN and is serializable. Methods in derived classes only use the
 * <code>getXxxx</code> naming pattern for serializable fields, to ensure easy serialization to JSON
 * and the like; everything else is expected to be handled through the reasoner service, with
 * optional caching if latency is significant.
 * 
 * @author ferd
 *
 */
public interface Knowledge extends KlabAsset {

    public enum KnowledgeClass {
        CONCEPT, OBSERVABLE, MODEL, INSTANCE, RESOURCE
    }

    /**
     * Used to streamline code in resolution. Would be unnecessary if Java only had a switch on
     * classes.
     * 
     * @param knowledge
     * @return
     */
    public static KnowledgeClass classify(Knowledge knowledge) {

        if (knowledge instanceof Concept) {
            return KnowledgeClass.CONCEPT;
        } else if (knowledge instanceof Observable) {
            return KnowledgeClass.OBSERVABLE;
        } else if (knowledge instanceof Model) {
            return KnowledgeClass.MODEL;
        } else if (knowledge instanceof Instance) {
            return KnowledgeClass.INSTANCE;
        } else if (knowledge instanceof Resource) {
            return KnowledgeClass.RESOURCE;
        }

        throw new KIllegalArgumentException("cannot classify " + knowledge + " as knowledge");
    }

}
