package org.integratedmodelling.cli.views;

import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.view.modeler.views.AuthenticationView;

public class CLIAuthenticationView extends CLIView implements AuthenticationView {
  @Override
  public void notifyUser(UserIdentity identity) {}
}
