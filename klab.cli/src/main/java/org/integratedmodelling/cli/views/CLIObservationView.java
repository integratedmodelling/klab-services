package org.integratedmodelling.cli.views;

import org.integratedmodelling.cli.KlabCLI;
import org.integratedmodelling.common.knowledge.KnowledgeRepository;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.api.view.modeler.views.ContextView;
import org.integratedmodelling.klab.api.view.modeler.views.controllers.ContextViewController;
import picocli.CommandLine;

import java.io.PrintWriter;

@CommandLine.Command(name = "context", mixinStandardHelpOptions = true, version = Version.CURRENT,
                     description = {
                             "Commands to create, access and manipulate contexts.",
                             ""}, subcommands = {CLIObservationView.List.class, CLIObservationView.New.class,
                                                 CLIObservationView.Connect.class,
                                                 CLIObservationView.Observe.class})
public class CLIObservationView extends CLIView implements ContextView {

    private static ContextViewController controller;

    public CLIObservationView() {
        controller = KlabCLI.INSTANCE.modeler().viewController(ContextViewController.class);
        controller.registerView(this);
    }

    @CommandLine.Command(name = "new", mixinStandardHelpOptions = true, version = Version.CURRENT,
                         description = {
                                 "Create a new context and make it current.", ""}, subcommands = {})
    public static class New implements Runnable {

        @CommandLine.ParentCommand
        CLIObservationView parent;

        @CommandLine.Spec
        CommandLine.Model.CommandSpec commandSpec;

        @CommandLine.Parameters(description = {"Name of the context being created.",
                                               "If not passed, a new name will be created."}, defaultValue
                                        = CommandLine.Parameters.NULL_VALUE)
        String name;

        // TODO add geometry option and instance parameters
        @CommandLine.Parameters(description = {"A known geometry identifier or geometry specification.",
                                               "If not passed, the context will have an empty geometry."},
                                defaultValue = CommandLine.Parameters.NULL_VALUE)
        String geometry;

        @Override
        public void run() {

            PrintWriter out = commandSpec.commandLine().getOut();

            if (name == null) {
                name = Utils.Names.shortUUID();
            }

            Geometry geom = null;

            if (geometry != null) {
                //				geom = Geometries.getGeometry(geometry);
                //				if (geom == null) {
                //					try {
                //						geom = Geometry.create(geometry);
                //					} catch (Throwable t) {
                //						out.println(Ansi.AUTO.string("Invalid geometry specification: @|red
                //						" + geometry + "|@"));
                //					}
                //				}
            }

            //			boolean isnew = Engine.INSTANCE.getCurrentSession() == null;
            //			SessionScope session = Engine.INSTANCE.getCurrentSession(true, Engine.INSTANCE
            //			.getCurrentUser());
            //			if (isnew) {
            //				out.println(
            //						Ansi.AUTO.string("No active session: created new session @|green " +
            //						session.getName() + "|@"));
            //			}
            //
            //			ContextScope context = session.getContext(name);
            //
            //			if (context != null) {
            //				out.println(Ansi.AUTO.string("Context @|red " + name + "|@ already exists!"));
            //			} else {
            //				context = session.createContext(name, geom == null ? Geometry.EMPTY : geom);
            //				Engine.INSTANCE.setCurrentContext(context);
            //				out.println(Ansi.AUTO.string("Context @|green " + context.getName() + "|@
            //				created and selected."));
            //			}

        }

    }

    @CommandLine.Command(name = "connect", mixinStandardHelpOptions = true, version = Version.CURRENT,
                         description = {
                                 "Connect to an existing context.", ""}, subcommands = {})
    public static class Connect implements Runnable {

        @CommandLine.ParentCommand
        CLIObservationView parent;

        @Override
        public void run() {
            // TODO Auto-generated method stub
            System.out.println("Hola");
        }

    }

    @CommandLine.Command(name = "observe", mixinStandardHelpOptions = true, version = Version.CURRENT,
                         description = {
                                 "Make an observation of the passed resolvable URN.", ""}, subcommands = {})
    public static class Observe implements Runnable {

        @CommandLine.Spec
        CommandLine.Model.CommandSpec commandSpec;

        @CommandLine.Option(names = {"-a", "--add"}, defaultValue = "false",
                            description = {"Add to existing context as a parallel observation"}, required =
                                    false)
        boolean addToContext = false;

        @CommandLine.Option(names = {"-c", "--context"}, defaultValue = CommandLine.Parameters.NULL_VALUE,
                            description = {
                                    "Choose a context for the observation (default is the current context)"}, required = false)
        private String context;

        @CommandLine.Option(names = {"-w", "--within"}, defaultValue = CommandLine.Parameters.NULL_VALUE,
                            description = {
                                    "Choose an observation to become the context of the observation.",
                                    "Use a dot to select the root subject if there is one."}, required =
                                    false)
        private String within;

        @CommandLine.Option(names = {"-g", "--geometry"}, defaultValue = CommandLine.Parameters.NULL_VALUE,
                            description = {
                                    "Specify a geometry for the new observation (must be a " +
                                            "countable/substantial)."}, required
                                    = false)
        private String geometry;

        @CommandLine.Parameters
        java.util.List<String> observables;


        // TODO option to observe in a sub-context

        @Override
        public void run() {

            PrintWriter out = commandSpec.commandLine().getOut();
            PrintWriter err = commandSpec.commandLine().getErr();

            String urn = Utils.Strings.join(observables, " ");
            var resources = KlabCLI.INSTANCE.user().getService(ResourcesService.class);
            var resolvable = resources.resolve(urn, KlabCLI.INSTANCE.user());
            var results = KnowledgeRepository.INSTANCE.ingest(resolvable, KlabCLI.INSTANCE.user());

            // TODO this is only for root observations
            if (!results.isEmpty() && results.getFirst() instanceof KlabAsset asset) {
                out.println(CommandLine.Help.Ansi.AUTO.string("Observation of @|yellow " + urn + "|@ " +
                        "started in "
                        + asset.getUrn()));
                KlabCLI.INSTANCE.modeler().observe(asset, addToContext);
            } else {
                err.println(CommandLine.Help.Ansi.AUTO.string("Can't resolve URN @|yellow " + urn + "|@ to " +
                        "observable knowledge"));
            }
        }

    }

    @CommandLine.Command(name = "list", mixinStandardHelpOptions = true, version = Version.CURRENT,
                         description = {
                                 "List and describe currently active contexts.", ""}, subcommands = {})
    public static class List implements Runnable {

        @CommandLine.ParentCommand
        CLIObservationView parent;

        // TODO option to list the context tree for the current context

        @Override
        public void run() {
            // TODO Auto-generated method stub
            System.out.println("Hola");
        }

    }

    @Override
    public void engineStatusChanged(Engine.Status status) {

    }
}
