package org.integratedmodelling.cli;

import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.scope.SessionScope;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;

@Command(name = "session", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
        "Commands to find, access and manipulate resources.",
        ""}, subcommands = {Session.List.class, Session.New.class, Session.Connect.class})
public class Session {

    @Command(name = "new", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "Create a new session and make it current.", ""}, subcommands = {})
    public static class New implements Runnable {

        @ParentCommand
        Session parent;

        @Spec
        CommandSpec commandSpec;

        @Parameters(description = {"Name of the session being created.",
                                   "If not passed, a new name will be created."}, defaultValue =
                Parameters.NULL_VALUE)
        String name;

        @Override
        public void run() {

            PrintWriter out = commandSpec.commandLine().getOut();

            if (name == null) {
                name = Utils.Names.shortUUID();
            }

            if (KlabCLI.INSTANCE.modeler().session(name) != null) {
                out.println(Ansi.AUTO.string("Session @|red " + name + "|@ already exists!"));
            } else {
                SessionScope session = KlabCLI.INSTANCE.modeler().session(name);
                out.println(Ansi.AUTO.string("Session @|green " + session.getName() + "|@ created and " +
                        "selected."));
            }
        }

    }

    @Command(name = "connect", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "Connect to an existing session.", ""}, subcommands = {})
    public static class Connect implements Runnable {

        @Spec
        CommandSpec commandSpec;

        @Parameters(description = {"Name of the session to connect to."})
        String name;

        @Override
        public void run() {

            PrintWriter out = commandSpec.commandLine().getOut();

            SessionScope session = KlabCLI.INSTANCE.modeler().session(name);
            if (session == null) {
                out.println(Ansi.AUTO.string("Session @|red " + name + "|@ does not exist!"));
            } else {
                //                KlabCLI.INSTANCE.engine().setDefaultSession(session);
                out.println(Ansi.AUTO.string("Session @|green " + name + "|@ is now the default session"));
            }
        }
    }

    @Command(name = "list", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "List and describe currently active sessions.", ""}, subcommands = {})
    public static class List implements Runnable {

        @ParentCommand
        Session parent;

        @Override
        public void run() {
            // TODO Auto-generated method stub
            System.out.println("Hola");
        }

    }

}
