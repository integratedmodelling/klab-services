package org.integratedmodelling.klab.runtime.language;

import java.util.List;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.lang.kim.ObservableValidator;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/** Default k.IM validator, including the common observable and concept rules. */
public class KimValidator extends ObservableValidator implements KimObservableVisitor.Validator {

  @Override
  public List<Notification> validateObservable(
      KimObservable observable, KimObservableVisitor.Context context) {
    return super.validateObservable(observable);
  }

  @Override
  public List<Notification> validateConcept(
      KimConcept concept, KimObservableVisitor.Context context) {
    return super.validateConcept(concept);
  }
}
