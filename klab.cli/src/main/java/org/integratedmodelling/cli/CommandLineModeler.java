package org.integratedmodelling.cli;

import org.integratedmodelling.klab.modeler.ModelerImpl;

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

    public static void main(String args) {
        // settings
        // setup
        // start the REPL
    }
}
