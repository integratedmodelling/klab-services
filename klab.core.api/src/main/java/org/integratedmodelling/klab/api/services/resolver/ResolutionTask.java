package org.integratedmodelling.klab.api.services.resolver;

import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;

import java.util.concurrent.Future;

/**
 * The observation future that tracks resolution in the runtime.
 * <p>
 * TODO add methods to subscribe to task-related notifications at client side. These could use messaging or
 *  polling.
 */
public interface ResolutionTask extends Future<Observation> {

    /**
     * The observation task ID is the same as the ID of the root observation being made.
     *
     * @return
     */
    String getId();

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