package org.integratedmodelling.cli;

import org.integratedmodelling.common.authentication.Authentication;
import org.integratedmodelling.klab.api.authentication.ExternalAuthenticationCredentials;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.utils.Utils;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.PrintWriter;

@Command(name = "credentials", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
        "Commands to find, access and manipulate resources.",
        "Use 'credentials describe' to describe the known authentication schemata and their parameters"},
         subcommands = {Credentials.List.class, Credentials.Set.class, Credentials.Describe.class,
                        Credentials.Delete.class})
public class Credentials {


    @Command(name = "list", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "List installed credentials.",
            ""}, subcommands = {})
    public static class List implements Runnable {

        @CommandLine.Spec
        CommandLine.Model.CommandSpec commandSpec;

        @CommandLine.Option(names = {"-r", "--resources"}, defaultValue = "false", description = {
                "Handle credentials in the resources service"
        }, required = false)
        private boolean resources = false;

        // TODO other services

        @Override
        public void run() {
            for (var info :
                    Authentication.INSTANCE.getCredentialInfo(KlabCLI.INSTANCE.engine().serviceScope())) {
                commandSpec.commandLine().getOut().println(info);
            }
        }

    }

    @Command(name = "describe", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "Describe the supported authentication schemata.",
            ""}, subcommands = {})
    public static class Describe implements Runnable {

        @CommandLine.Spec
        CommandLine.Model.CommandSpec commandSpec;

        @Override
        public void run() {

            /*
            FIXME these should come from the schemata in ExternalAuthenticationCredentials
             */

            PrintWriter out = commandSpec.commandLine().getOut();
            out.println("Currently accepted schemata:");
            out.println(CommandLine.Help.Ansi.AUTO.string("[-s basic] -h <hostname[:port]> <username> " +
                    "<password>"));
            out.println(CommandLine.Help.Ansi.AUTO.string("-s oidc -h <hostname[:port]> <grant_type> " +
                    "<client_id> <client_secrets> <provider_id>"));
            out.println(CommandLine.Help.Ansi.AUTO.string("-s key -h <hostname[:port]> <key>"));
        }

    }

    @Command(name = "remove", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "Delete credentials for a scheme/host pair",
            ""}, subcommands = {})
    public static class Delete implements Runnable {

        @CommandLine.Option(names = {"-s", "--scheme"}, defaultValue = "basic", description = {
                "The authentication scheme: one of basic (default), oidc, s3 or key"
        }, required = false)
        private String scheme;

        @CommandLine.Option(names = {"-r", "--resources"}, defaultValue = "false", description = {
                "Handle credentials in the resources service"
        }, required = false)
        private boolean resources = false;

        // TODO other services

        @CommandLine.Option(names = {"-h", "--host"}, description = {
                "The host URL that the credentials refer to"
        }, required = true)
        private String host;

        @Override
        public void run() {
            // TODO Auto-generated method stub
            System.out.println("Hola");
        }

    }

    @Command(name = "set", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "Add a new set of credentials for a host and protocol.",
            ""}, subcommands = {})
    public static class Set implements Runnable {

        @CommandLine.Option(names = {"-s", "--scheme"}, defaultValue = "basic", description = {
                "The authentication scheme: one of basic (default), oidc, s3 or key"
        }, required = false)
        private String scheme;

        @CommandLine.Option(names = {"-h", "--host"}, description = {
                "The host URL that the credentials refer to"
        }, required = true)
        private String host;

        @CommandLine.Option(names = {"-r", "--resources"}, defaultValue = "false", description = {
                "Handle credentials in the resources service"
        }, required = false)
        private boolean resources = false;

        // TODO other services

        @CommandLine.Spec
        CommandLine.Model.CommandSpec commandSpec;

        /**
         *
         */
        @CommandLine.Parameters(description = {"Credentials arguments matching the chosen scheme. Use " +
                                                       "'credentials <scheme>' to list the argument types."})
        private java.util.List<String> arguments;

        @Override
        public void run() {

            String[] params = ExternalAuthenticationCredentials.parameterKeys.get(scheme);
            if (params == null) {
                throw new KlabIllegalArgumentException("unrecognized authorization scheme");
            }
            if (arguments.size() != params.length) {
                throw new KlabValidationException("expecting " + params.length + " arguments for scheme " +
                        scheme);
            }

            // TODO provision for interactive input of key params if no args are given

            ExternalAuthenticationCredentials credentials = new ExternalAuthenticationCredentials();

            credentials.setScheme(scheme);
            credentials.setId(Utils.Names.shortUUID());
            credentials.setPrivileges(Authentication.INSTANCE.getDefaultPrivileges(KlabCLI.INSTANCE.engine().serviceScope()));

            for (String arg : arguments) {
                credentials.getCredentials().add(arg);
            }

            if (resources) {
                var rs = KlabCLI.INSTANCE.engine().serviceScope().getService(ResourcesService.class);
                if (rs != null) {
                    if (rs.addCredentials(host, credentials, KlabCLI.INSTANCE.engine().serviceScope()) == null) {
                        commandSpec.commandLine().getErr().println("Failed to add external credentials");
                    }
                }
            } else {
                if (Authentication.INSTANCE.addExternalCredentials(host, credentials,
                        KlabCLI.INSTANCE.engine().serviceScope()) == null) {
                    commandSpec.commandLine().getErr().println("Failed to add external credentials");
                }
            }
        }
    }

}
