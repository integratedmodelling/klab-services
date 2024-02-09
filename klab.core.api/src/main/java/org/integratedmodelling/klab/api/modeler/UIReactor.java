package org.integratedmodelling.klab.api.modeler;

import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.services.KlabService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Anything that reacts to UI events implements this interface.
 */
public interface UIReactor {

    /**
     * The UI only reacts to these events, which contain their allowed payload class(es) for validation. If
     * the payload classes are multiple, the payload in the corresponding message/method must be an array of
     * objects of those classes, in order.
     * <p>
     * Some of these events go from the UI to the engine, others the other way around, some even both ways.
     * This should be indicated somehow, e.g. with a first parameter enum.
     */
    enum UIEvent {

        EngineStarting(UserIdentity.class),
        EngineAvailable(UserIdentity.class),
        EngineUnavailable(UserIdentity.class),
        ServiceStarting(KlabService.ServiceCapabilities.class),
        ServiceAvailable(KlabService.ServiceCapabilities.class),
        ServiceUnavailable(KlabService.ServiceCapabilities.class),
        /**
         * Document add requested contains type of document, URN and a map of parameters
         */
        DocumentAddRequest(KlabAsset.KnowledgeClass.class, String.class, Map.class),
        DocumentDeleteRequest(KlabDocument.class),
        DocumentSelected(KlabDocument.class),
        DocumentPositionChanged(KlabDocument.class, Integer.class),
        WorkspaceChanged(String.class);

        List<Class<?>> payloadClasses = new ArrayList<>();

        UIEvent(Class<?>... payloadClass) {
            for (var cls : payloadClass) {
                this.payloadClasses.add(cls);
            }
        }
    }

    Modeler getModeler();

    /**
     * React to the passed UI event. The payload class is validated to be of the expected type before this is
     * called (an array if multiple). This is a catch-all method that is called only if there are no
     * specifically annotated methods in the reactor.
     *
     * @param event
     * @param payload
     */
    void onEvent(UIEvent event, Object payload);
}
