package org.integratedmodelling.klab.api.lang.kim;

import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Resolvable;
import org.integratedmodelling.klab.api.lang.Contextualizable;

import java.util.List;

public interface KimModel extends KlabStatement, Resolvable {

//    /**
//     * Name for a model is taken from the primary observable. If that is named, the name of the observable
//     * becomes that of a model. Otherwise it should extract a sensible name and append -resolver or
//     * -instantiator.
//     *
//     * @return
//     */
//    String getName();

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

    /**
     * The URN for a model is namespacePath.modelName
     *
     * @return
     */
    String getUrn();

    /**
     * Contextualizer or processor(s) given after 'using'
     *
     * @return computables or an empty list
     */
    List<Contextualizable> getContextualization();

    /**
     * Extentual coverage of this specific model, if specified. If null, coverage defaults to any specified
     * for the namespace. If even that is null, coverage is understood as universal.
     *
     * @return
     */
    Geometry getCoverage();

    String getDocstring();

    String getProjectName();

}
