package org.integratedmodelling.klab.api.lang.kim;

import java.util.List;

import org.integratedmodelling.klab.api.collections.Literal;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.lang.Contextualizable;

public interface KimModelStatement extends KimActiveStatement {

    KimConcept getReinterpretingRole();

    List<KimObservable> getDependencies();

    List<KimObservable> getObservables();

    /**
     * Data type of primary observable meant to discriminate void and non-semantic models from the
     * semantic ones. Can be either of VOID, CONCEPT, NUMBER, TEXT or BOOLEAN.
     * 
     * @return
     */
    Artifact.Type getType();

    List<String> getResourceUrns();

    boolean isLearningModel();

    boolean isInterpreter();

    boolean isInstantiator();

    String getName();

    Literal getInlineValue();

    /**
     * Contextualizer or processor(s) given after 'using'
     * 
     * @return computables or an empty list
     */
    List<Contextualizable> getContextualization();

    String getDocstring();

    /**
     * Normally true, it will return false in models that were expressed as non-semantic operations,
     * using the 'number', 'text', etc. keywords. These are also, by default, private and are used
     * only directly by name.
     * 
     * @return
     */
    boolean isSemantic();
}
