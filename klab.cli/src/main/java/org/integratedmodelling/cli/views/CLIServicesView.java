package org.integratedmodelling.cli.views;

import org.checkerframework.checker.units.qual.K;
import org.integratedmodelling.cli.KlabCLI;
import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.engine.distribution.RunningInstance;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.view.modeler.views.ServicesView;
import org.integratedmodelling.klab.api.view.modeler.views.controllers.ServicesViewController;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicReference;

@CommandLine.Command(name = "services",
                     mixinStandardHelpOptions = true,
                     description = {"List, select and control services.", "Services can be started locally " +
                             "or connected from the k"
                             + ".LAB network.", "Service discovery is supported according to credentials.",
                                    ""},
                     subcommands = {org.integratedmodelling.cli.views.CLIServicesView.Connect.class,
                                    CLIServicesView.Resources.class, CLIServicesView.Runtime.class})
public class CLIServicesView extends CLIView implements Runnable, ServicesView {

    private static ServicesViewController controller;
    private static AtomicReference<Engine.Status> status = new AtomicReference<>();

    public CLIServicesView() {
        controller = KlabCLI.INSTANCE.modeler().viewController(ServicesViewController.class);
        controller.registerView(this);
    }

    @CommandLine.Spec
    CommandLine.Model.CommandSpec commandSpec;

    @CommandLine.Option(names = {"-v", "--verbose"},
                        defaultValue = "false",
                        description = {"Display status and capabilities from services"},
                        required = false)
    boolean verbose = false;

    @CommandLine.Option(names = {"-rs", "--reasoners"},
                        defaultValue = "false",
                        description = {"List all reasoner services."},
                        required = false)
    boolean reasoners = false;

    @CommandLine.Option(names = {"-rv", "--resolvers"},
                        defaultValue = "false",
                        description = {"List all resolver services."},
                        required = false)
    boolean resolvers = false;

    @CommandLine.Option(names = {"-rn", "--runtimes"},
                        defaultValue = "false",
                        description = {"List all runtime services."},
                        required = false)
    boolean runtimes = false;

    @CommandLine.Option(names = {"-rr", "--resources"},
                        defaultValue = "false",
                        description = {"List all resource services."},
                        required = false)
    boolean resources = false;

    @CommandLine.Option(names = {"-c", "--community"},
                        defaultValue = "false",
                        description = {"List all community services."},
                        required = false)
    boolean community = false;

    @Override
    public void run() {

        PrintWriter out = commandSpec.commandLine().getOut();

        /*
         * TODO Print generic info about the service scope and the discovery strategy
         *  installed.
         */

        for (var serviceType : new KlabService.Type[]{KlabService.Type.REASONER, KlabService.Type.RESOURCES
                , KlabService.Type.RESOLVER, KlabService.Type.RUNTIME, KlabService.Type.COMMUNITY,
                                                      KlabService.Type.ENGINE}) {

            if (serviceType == KlabService.Type.ENGINE) {
                // TODO describe the engine
            } else {

                boolean first = true;
                for (var service : KlabCLI.INSTANCE.engine().serviceScope().getServices(
                        serviceType.classify())) {

                    if (reasoners && serviceType != KlabService.Type.REASONER || resolvers && serviceType != KlabService.Type.RESOLVER || resources && serviceType != KlabService.Type.RESOURCES || runtimes && serviceType != KlabService.Type.RUNTIME || community && serviceType != KlabService.Type.COMMUNITY) {
                        continue;
                    }

                    /*
                     * TODO tag each service with a keyword or parameter so that it can be easily
                     *  referenced using connect. Keep the services dictionary in the superclass.
                     */

                    if (first) {
                        //                            out.println(serviceType);
                        // TODO number for selection; highlight the name of the "current" service in
                        //  each category
                        out.println("  " + Utils.Paths.getLast(
                                service.getClass().getName(),
                                '.') + ": " + service.getServiceName() + " " + " [" + (service.status().isAvailable() ? "available" : "not available") + (service instanceof ServiceClient client && client.isLocal() ? "," + "local" : "") + "] " + service.getUrl() + ServicesAPI.CAPABILITIES);
                        if (verbose) {
                            out.println(Utils.Strings.indent(Utils.Json.printAsJson(
                                    service.capabilities(KlabCLI.INSTANCE.engine().getUsers().get(0))), 6));
                        }
                    }
                    first = false;
                }
            }
        }
    }

    public static class ServiceHandler {

        /**
         * TODO add optional arguments to fill in the entire request on the CLI by passing the schema id and
         *  parameters. The help should list all parameters with a description (currently missing).
         *
         * @param serviceType
         */
        protected static void importFromSchema(KlabService.Type serviceType, boolean help,
                                               String suggestedUrn, java.util.List<String> arguments) {

            if (help) {

                return;
            }

            var service = KlabCLI.INSTANCE.user().getService(serviceType.classify());
            if (service != null) {
                KlabCLI.INSTANCE.importWithSchema(service, suggestedUrn, arguments);
            }
        }

        /**
         * TODO add optional arguments to fill in the entire request on the CLI by passing the schema id and
         *  parameters. The help should list all parameters with a description (currently missing).
         *
         * @param serviceType
         */
        protected static void exportFromSchema(KlabService.Type serviceType, boolean help,
                                               java.util.List<String> arguments) {

            if (help) {
                return;
            }

            var service = KlabCLI.INSTANCE.user().getService(serviceType.classify());
            if (service != null) {
                KlabCLI.INSTANCE.exportWithSchema(service, arguments);
            }
        }


    }

    // TODO help should be custom and show the available schemata
    // TODO enable inline definitions
    @CommandLine.Command(name = "resources",
                         subcommands = {Resources.Import.class, Resources.Export.class},
                         mixinStandardHelpOptions = true,
                         description = {"Connect to an " + "available " + "service", "Makes the service " +
                                 "available" + " for " + "requests."})
    public static class Resources extends ServiceHandler {

        @CommandLine.Option(names = {"-h", "--help"},
                            defaultValue = "false",
                            description = {"Display available import schemata"},
                            required = false)
        boolean help = false;

        @CommandLine.Command(name = "import",
                             mixinStandardHelpOptions = false,
                             description = {"Connect to an " + "available " + "service", "Makes the service" +
                                     " available" + " for " + "requests."})
        public static class Import implements Runnable {

            @CommandLine.Option(names = {"-h", "--help"},
                                defaultValue = "false",
                                description = {"Display available import schemata"},
                                required = false)
            boolean help = false;

            @CommandLine.Option(names = {"-u", "--urn"},
                                defaultValue = "X:X:X:X",
                                description = {"Pass suggested URN for import (result may differ)"},
                                required = false)
            String urn;

            @CommandLine.Parameters
            java.util.List<String> arguments;

            @Override
            public void run() {
                importFromSchema(KlabService.Type.RESOURCES, help, urn, arguments);
            }
        }

        @CommandLine.Command(name = "export",
                             mixinStandardHelpOptions = false,
                             description = {"Connect to an " + "available " + "service", "Makes the service" +
                                     " available" + " for " + "requests."})
        public static class Export implements Runnable {

            @CommandLine.Option(names = {"-h", "--help"},
                                defaultValue = "false",
                                description = {"Display available import schemata"},
                                required = false)
            boolean help = false;

            @CommandLine.Parameters
            java.util.List<String> arguments;

            @Override
            public void run() {
                exportFromSchema(KlabService.Type.RESOURCES, help, arguments);
            }
        }
    }

    // TODO help should be custom and show the available schemata
    // TODO enable inline definitions
    @CommandLine.Command(name = "runtime",
                         subcommands = {Runtime.Import.class, Runtime.Export.class},
                         mixinStandardHelpOptions = true,
                         description = {"Connect to an " + "available " + "service", "Makes the service " +
                                 "available" + " for " + "requests."})
    static class Runtime extends ServiceHandler {

        @CommandLine.Command(name = "import",
                             mixinStandardHelpOptions = false,
                             description = {"Connect to an " + "available " + "service", "Makes the service" +
                                     " available" + " for " + "requests."})
        public static class Import implements Runnable {

            @CommandLine.Option(names = {"-h", "--help"},
                                defaultValue = "false",
                                description = {"Display available import schemata"},
                                required = false)
            boolean help = false;

            @CommandLine.Option(names = {"-u", "--urn"},
                                defaultValue = "X:X:X:X",
                                description = {"Pass suggested URN for import (result may differ)"},
                                required = false)
            String urn;

            @CommandLine.Parameters
            java.util.List<String> arguments;

            @Override
            public void run() {
                importFromSchema(KlabService.Type.RUNTIME, help, urn, arguments);
            }
        }

        @CommandLine.Command(name = "export",
                             mixinStandardHelpOptions = false,
                             description = {"Connect to an " + "available " + "service", "Makes the service" +
                                     " available" + " for " + "requests."})
        public static class Export implements Runnable {

            @CommandLine.Option(names = {"-h", "--help"},
                                defaultValue = "false",
                                description = {"Display available import schemata"},
                                required = false)
            boolean help = false;

            @CommandLine.Parameters
            java.util.List<String> arguments;

            @Override
            public void run() {
                exportFromSchema(KlabService.Type.RUNTIME, help, arguments);
            }
        }

    }


    @CommandLine.Command(name = "connect",
                         mixinStandardHelpOptions = true,
                         description = {"Connect to an " + "available " + "service", "Makes the service " +
                                 "available" + " for " + "requests."})
    static class Connect implements Runnable {

        @CommandLine.Option(names = {"-d", "--default"},
                            defaultValue = "false",
                            description = {"Make the connected service also the default to answer requests."},
                            required = false)
        boolean makeDefault = false;

        @Override
        public void run() {
            // TODO Auto-generated method stub

        }

    }

    @Override
    public void servicesConfigurationChanged(KlabService.ServiceCapabilities service) {

    }

    @Override
    public void notifyServiceStatus(KlabService.ServiceStatus status) {

    }

    @Override
    public void engineStatusChanged(Engine.Status status) {
        this.status.set(status);
    }
}
