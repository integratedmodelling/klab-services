package org.integratedmodelling.klab.services.reasoner.functors;

import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabFunction;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.reasoner.internal.ObservableBuilder;

/**
 * Functor family for type checking and inspection, used by filters in observation strategies
 */
@Library(name = "type")
public class TypeFunctors {

    private final Reasoner reasoner;

    public TypeFunctors(ReasonerService reasoner) {
        this.reasoner = reasoner;
    }

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

    @KlabFunction(name = "collective", version = Version.CURRENT, description = "Check if an observable is " +
            "concrete", type = {Artifact.Type.BOOLEAN})
    public boolean isCollective(Semantics semantics) {
        return semantics.asConcept().isCollective();
    }

    @KlabFunction(name = "arity.single", version = Version.CURRENT, description = "Check if an observable is " +
            "concrete", type = {Artifact.Type.CONCEPT})
    public Semantics changeArityToSingle(Semantics semantics) {

        if (semantics.asConcept().isCollective()) {
            ObservableBuilder builder = new ObservableBuilder(semantics.asConcept(), reasoner.serviceScope(), (ReasonerService) reasoner);
            builder.collective(false);
            return builder.build();
        }

        return semantics;
    }

    @KlabFunction(name = "arity.collective", version = Version.CURRENT, description = "Check if an observable is " +
            "concrete", type = {Artifact.Type.CONCEPT})
    public Semantics changeArityToCollective(Semantics semantics) {

        if (!semantics.asConcept().isCollective()) {
            ObservableBuilder builder = new ObservableBuilder(semantics.asConcept(), reasoner.serviceScope(), (ReasonerService) reasoner);
            builder.collective(true);
            return builder.build();
        }

        return semantics;
    }

}
