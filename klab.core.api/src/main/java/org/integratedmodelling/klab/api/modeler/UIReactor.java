package org.integratedmodelling.klab.api.modeler;

import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.services.KlabService;

/**
 * Anything that reacts to UI events implements this interface.
 */
public interface UIReactor {

    /**
     * The UI only reacts to these events, which contain their allowed payload class for validation.
     */
    enum UIEvent {

        EngineStarting(UserIdentity.class),
        EngineAvailable(UserIdentity.class),
        EngineUnavailable(UserIdentity.class),
        ServiceStarting(KlabService.ServiceCapabilities.class),
        ServiceAvailable(KlabService.ServiceCapabilities.class),
        ServiceUnavailable(KlabService.ServiceCapabilities.class);

        Class<?> payloadClass;

        UIEvent(Class<?> payloadClass) {
            this.payloadClass = payloadClass;
        }

    }

    Modeler getModeler();

    /**
     * React to the passed UI event. The payload class is validated to be of the expected type before this is
     * called.
     *
     * @param event
     * @param payload
     */
    void onEvent(UIEvent event, Object payload);
}
