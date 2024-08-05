package org.integratedmodelling.klab.api.services.resolver;

import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;

import java.util.concurrent.Future;

/**
 * The observation future that tracks resolution in the runtime and the return value of
 * {@link org.integratedmodelling.klab.api.scope.ContextScope#observe(Object...)}.
 * <p>
 * Resolution tasks may have children, which are accounted for in the resolution graph in the digital twin so
 * there is no getChildren() method. A task isn't complete until all of its children are.
 */
public interface ResolutionTask extends Future<Observation> {

    /**
     * The observation task ID is the same as the ID of the root observation being made.
     *
     * @return
     */
    long getId();

    /**
     * Typed get() for fluency.
     *
     * @param observationClass
     * @param <T>
     * @return
     */
    default <T extends Observation> T get(Class<T> observationClass) {
        try {
            var obs = get();
            if (obs != null && observationClass.isAssignableFrom(obs.getClass())) {
                return (T) get();
            }
        } catch (Exception e) {
            throw new KlabInternalErrorException(e);
        }
        return null;
    }

}
