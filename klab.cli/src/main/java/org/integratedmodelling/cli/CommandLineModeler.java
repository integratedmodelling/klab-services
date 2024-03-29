package org.integratedmodelling.cli;

import org.integratedmodelling.klab.api.view.modeler.views.ResourcesNavigator;
import org.integratedmodelling.klab.api.view.modeler.views.controllers.*;
import org.integratedmodelling.klab.modeler.ModelerImpl;
import org.integratedmodelling.klab.modeler.views.*;

/**
 * Command-line driven modeler to replace {@link KlabCLI}. Each modeler view should have its command class and
 * defer command execution to view actions. The run() method in each view class should produce a synopsis of
 * the state of the view, synthetic by default and extensive if --verbose is passed.
 * <p>
 * The main command classes should be:
 * <p>
 * resources, services, statistics, report, distribution, knowledge, events, debug .
 * <p>
 * plus a set command for settings and maybe an engine subclass for sessions/context control (or these could
 * be at top level as currently, TBD)
 */
public class CommandLineModeler extends ModelerImpl {

    public CommandLineModeler() {
        super();
        // register basic view advisors for each class.
        viewController(ResourcesNavigatorController.class).registerView(new ResourcesNavigatorAdvisor());
        viewController(ContextInspectorController.class).registerView(new ContextInspectorAdvisor());
        viewController(AuthenticationViewController.class).registerView(new AuthenticationViewAdvisor());
        viewController(ServicesViewController.class).registerView(new ServicesViewAdvisor());
        viewController(ContextViewController.class).registerView(new ContextViewAdvisor());
        // TODO others
    }

    public static void main(String args) {
        // settings
        // setup
        // start the REPL
    }
}
