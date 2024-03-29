package org.integratedmodelling.cli;

import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.view.modeler.views.ServicesView;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;

@Command(name = "services", mixinStandardHelpOptions = true, description = {"List, select and control " +
                                                                                    "services.",
                                                                            "Services can be started " +
                                                                                    "locally or connected " +
                                                                                    "from the k.LAB network.",
                                                                            "Service discovery is supported" +
                                                                                    " according to " +
                                                                                    "credentials.", ""},
         subcommands = {
                 org.integratedmodelling.cli.Services.List.class,
                 org.integratedmodelling.cli.Services.Connect.class})
public class Services  {

    @Command(name = "list", mixinStandardHelpOptions = true, description = {"List the services available",
                                                                            "Colors show services connected" +
                                                                                    " (yellow), online and " +
                                                                                    "available (green) or " +
                                                                                    "offline (grey).",
                                                                            "The default service for " +
                                                                                    "requests is indicated " +
                                                                                    "with an asterisk."})
    static class List implements Runnable {

        int zio = 0;
        @Spec
        CommandSpec commandSpec;

        @Option(names = {"-v", "--verbose"}, defaultValue = "false", description = {
                "Display status and capabilities from services"}, required = false)
        boolean verbose = false;

        @Option(names = {"-rs", "--reasoners"}, defaultValue = "false", description = {
                "List all reasoner services."}, required = false)
        boolean reasoners = false;

        @Option(names = {"-rv", "--resolvers"}, defaultValue = "false", description = {
                "List all resolver services."}, required = false)
        boolean resolvers = false;

        @Option(names = {"-rn", "--runtimes"}, defaultValue = "false", description = {
                "List all runtime services."}, required = false)
        boolean runtimes = false;

        @Option(names = {"-rr", "--resources"}, defaultValue = "false", description = {
                "List all resource services."}, required = false)
        boolean resources = false;

        @Option(names = {"-c", "--community"}, defaultValue = "false", description = {
                "List all community services."}, required = false)
        boolean community = false;

        @Override
        public void run() {

            PrintWriter out = commandSpec.commandLine().getOut();

            /*
             * TODO Print generic info about the service scope and the discovery strategy
             *  installed.
             */

            for (var serviceType : new KlabService.Type[]{KlabService.Type.REASONER,
                                                          KlabService.Type.RESOURCES,
                                                          KlabService.Type.RESOLVER,
                                                          KlabService.Type.RUNTIME,
                                                          KlabService.Type.COMMUNITY,
                                                          KlabService.Type.ENGINE}) {

                if (serviceType == KlabService.Type.ENGINE) {
                    // TODO describe the engine
                } else {

                    boolean first = true;
                    for (var service :
                            KlabCLI.INSTANCE.engine().serviceScope().getServices(serviceType.classify())) {

                        if (reasoners && serviceType != KlabService.Type.REASONER
                                || resolvers && serviceType != KlabService.Type.RESOLVER
                                || resources && serviceType != KlabService.Type.RESOURCES
                                || runtimes && serviceType != KlabService.Type.RUNTIME
                                || community && serviceType != KlabService.Type.COMMUNITY) {
                            continue;
                        }

                        /*
                         * TODO tag each service with a keyword or parameter so that it can be easily
                         *  referenced using connect. Keep the services dictionary in the superclass.
                         */

                        if (first) {
//                            out.println(serviceType);
                            // TODO number for selection; highlight the name of the "current" service in each category
                            out.println("  " + Utils.Paths.getLast(service.getClass().getName(), '.') + ": "
                                    + service.getServiceName() + " "
                                    + " ["
                                    + (service.status().isAvailable() ? "available" : "not available")
                                    + (service instanceof ServiceClient client && client.isLocal() ? "," + "local" : "")
                                    + "] "
                                    + service.getUrl() + ServicesAPI.CAPABILITIES
                            );
                            if (verbose) {
                                out.println(Utils.Strings.indent(service.capabilities().toString(), 6));
                            }
                        }
                        first = false;
                    }
                }
            }
        }
    }

    @Command(name = "connect", mixinStandardHelpOptions = true, description = {"Connect to an available " +
                                                                                       "service",
                                                                               "Makes the service available" +
                                                                                       " for requests."})
    static class Connect implements Runnable {

        @Option(names = {"-d", "--default"}, defaultValue = "false", description = {
                "Make the connected service also the default to answer requests."}, required = false)
        boolean makeDefault = false;

        @Override
        public void run() {
            // TODO Auto-generated method stub

        }

    }

}
