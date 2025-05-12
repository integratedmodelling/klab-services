package org.integratedmodelling.klab.api.view.modeler.views;

import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.view.View;

public interface RuntimeView extends View {

    void notifyNewDigitalTwin(ContextScope scope, RuntimeService service);

    void notifyDigitalTwinModified(DigitalTwin digitalTwin, Message change);

    void notifyObservationSubmission(Observation observation, ContextScope contextScope, RuntimeService service);

    void notifyObservationSubmissionAborted(Observation observation, ContextScope contextScope, RuntimeService service);

    void notifyObservationSubmissionFinished(Observation observation, ContextScope contextScope, RuntimeService service);

    void notifyContextObservationResolved(Observation observation, ContextScope contextScope, RuntimeService service);

    void notifyObserverResolved(Observation observation, ContextScope contextScope, RuntimeService service);
}
