package org.integratedmodelling.klab.api.view.modeler.views;

import org.integratedmodelling.klab.api.view.ViewController;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.annotations.UIView;

@UIView(UIReactor.Type.CLIConsole)
public interface CLITerminalController extends ViewController<CLITerminal> {
}
