package org.integratedmodelling.klab.api.view.modeler.views.controllers;

import org.integratedmodelling.klab.api.view.ViewController;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.annotations.UIViewController;
import org.integratedmodelling.klab.api.view.modeler.views.CLITerminal;

@UIViewController(value = UIReactor.Type.CLIConsole, viewType = CLITerminal.class)
public interface CLITerminalController extends ViewController<CLITerminal> {
}
