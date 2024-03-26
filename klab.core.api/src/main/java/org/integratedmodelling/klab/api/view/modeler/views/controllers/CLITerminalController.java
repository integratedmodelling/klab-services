package org.integratedmodelling.klab.api.view.modeler.views.controllers;

import org.integratedmodelling.klab.api.view.ViewController;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.annotations.UIView;
import org.integratedmodelling.klab.api.view.modeler.views.CLITerminal;

@UIView(UIReactor.Type.CLIConsole)
public interface CLITerminalController extends ViewController<CLITerminal> {
}
