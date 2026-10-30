package org.integratedmodelling.common.runtime;

import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;

/** Carries the client's default observer preference to the runtime without changing shared scopes. */
public final class ObserverSubmissionPolicy {
  private ObserverSubmissionPolicy() {}

  public static ContextScope apply(ContextScope scope, Settings settings) {
    return settings != null
            && Boolean.TRUE.equals(settings.get(Setting.DO_NOT_CREATE_A_DEFAULT_OBSERVER, Boolean.class))
            && !Boolean.TRUE.equals(scope.getConstraint(
                ResolutionConstraint.Type.DoNotCreateDefaultObserver, Boolean.class))
        ? scope.withResolutionConstraints(
            ResolutionConstraint.of(ResolutionConstraint.Type.DoNotCreateDefaultObserver, true))
        : scope;
  }
}
