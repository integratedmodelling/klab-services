package org.integratedmodelling.klab.modeler.views;

import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.view.modeler.views.RuntimeView;

/** TODO implement data model to replicate the actions when the Eclipse modeler is done. */
public class RuntimeViewAdvisor extends BaseViewAdvisor implements RuntimeView {

  @Override
  public void notifyNewDigitalTwin(ContextScope scope, RuntimeService service) {}

  @Override
  public void notifyDigitalTwinModified(DigitalTwin digitalTwin, Message change) {}

  @Override
  public void notifyObservationSubmission(
      Observation observation, ContextScope contextScope, RuntimeService service) {}

  @Override
  public void notifyObservationSubmissionAborted(
      Observation observation, ContextScope contextScope, RuntimeService service) {}

  @Override
  public void notifyObservationSubmissionFinished(
      Observation observation, ContextScope contextScope, RuntimeService service) {}

  @Override
  public void notifyContextObservationResolved(
      Observation observation, ContextScope contextScope, RuntimeService service) {}

  @Override
  public void notifyObserverResolved(
      Observation observation, ContextScope contextScope, RuntimeService service) {}
}
