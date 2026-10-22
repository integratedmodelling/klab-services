package org.integratedmodelling.klab.services.application.ui;

import java.util.Map;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.runtime.kactors.ApplicationBase;
import org.integratedmodelling.klab.runtime.kactors.view.UiPresentation;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;

/** Authorization and runtime boundary, kept separate from transport and instance retention. */
public interface UiApplicationLauncher {
  record Published(
      String urn,
      String project,
      String resourcesServiceId,
      KActorsBehavior behavior,
      UiPresentation presentation,
      Map<String, String> stylesheets) {}

  Published resolve(String urn, String locale, EngineAuthorization user);

  void authorize(Published application, EngineAuthorization user);

  ApplicationBase start(Published application, EngineAuthorization user);

  default org.integratedmodelling.klab.runtime.kactors.view.UiToolkitLease selectToolkit() {
    return org.integratedmodelling.klab.runtime.kactors.view.UiToolkitLease.quasar();
  }
}
