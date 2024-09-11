package org.integratedmodelling.klab.services.reasoner.functors;

import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabFunction;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;

/**
 * Functor family for type checking and inspection, used by filters in observation strategies
 */
@Library(name = "type")
public class TypeFunctors {

    @KlabFunction(name = "concrete", version = Version.CURRENT, description = "Check if an observable is " +
            "concrete", type = {Artifact.Type.BOOLEAN})
    public boolean isConcrete(Semantics semantics) {
        return !semantics.isAbstract();
    }

    @KlabFunction(name = "abstract", version = Version.CURRENT, description = "Check if an observable is " +
            "concrete", type = {Artifact.Type.BOOLEAN})
    public boolean isAbstract(Semantics semantics) {
        return semantics.isAbstract();
    }

}
