package org.integratedmodelling.klab.api.lang.kim;

import org.integratedmodelling.klab.api.collections.Literal;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.lang.Contextualizable;

import java.util.List;

public interface KimModel extends KimActiveStatement {

    List<KimObservable> getDependencies();

    List<KimObservable> getObservables();

    /**
     * True if any observable is NOTHING or there are other errors visible at the syntactic level. Does not
     * guarantee that the model will be usable.
     *
     * @return true if inactive
     */
    boolean isInactive();

    /**
     * Data type of primary observable meant to discriminate void and non-semantic models from the semantic
     * ones. Can be either of VOID, CONCEPT, NUMBER, TEXT or BOOLEAN.
     *
     * @return
     */
    Artifact.Type getType();

    List<String> getResourceUrns();

    boolean isLearningModel();

    String getUrn();

    /**
     * Contextualizer or processor(s) given after 'using'
     *
     * @return computables or an empty list
     */
    List<Contextualizable> getContextualization();

    String getDocstring();

    String getProjectName();

}
