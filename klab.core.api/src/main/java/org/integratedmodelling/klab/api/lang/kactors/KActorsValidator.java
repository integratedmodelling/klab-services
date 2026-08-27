package org.integratedmodelling.klab.api.lang.kactors;

import java.util.List;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.lang.kim.ObservableValidator;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/** Default k.Actors validator, including validation of embedded semantic literals. */
public class KActorsValidator extends ObservableValidator implements KActorsVisitor.Validator {

  @Override
  public List<Notification> validateObservable(
      KimObservable observable, KActorsVisitor.KActorsContext context) {
    return super.validateObservable(observable);
  }

  @Override
  public List<Notification> validateConcept(
      KimConcept concept, KActorsVisitor.KActorsContext context) {
    return super.validateConcept(concept);
  }
}
