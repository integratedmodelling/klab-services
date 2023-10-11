package org.integratedmodelling.kcli;

import groovyjarjarpicocli.CommandLine.Help.Ansi;
import org.integratedmodelling.kcli.engine.Engine;
import org.integratedmodelling.kcli.functional.FunctionalCommand;
import org.integratedmodelling.klab.Version;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.DescriptionType;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.configuration.Configuration;
import org.integratedmodelling.klab.utilities.Utils;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.File;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.function.Function;

@Command(name = "reason", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
        "Commands to find, access and manipulate semantic knowledge.",
        ""}, subcommands = {Reasoner.Children.class, Reasoner.Parents.class, Reasoner.Traits.class,
                            Reasoner.Type.class,
                            Reasoner.Strategy.class, Reasoner.Export.class})
public class Reasoner {


    @Command(name = "parents", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "List the asserted parent hierarchy of a concept."}, subcommands = {})
    public static class Parents extends FunctionalCommand {

        @Spec
        CommandSpec commandSpec;

        @Parameters
        java.util.List<String> observables;

        @Override
        public void run() {

            PrintWriter out = commandSpec.commandLine().getOut();
            PrintWriter err = commandSpec.commandLine().getErr();

            var urn = Utils.Strings.join(observables, " ");
            var reasoner = Engine.INSTANCE.getCurrentUser()
                    .getService(org.integratedmodelling.klab.api.services.Reasoner.class);
            Concept concept = reasoner.resolveConcept(urn);
            if (concept == null) {
                err.println("Concept " + urn + " not found");
            } else {
                out.println(concept.getUrn());
                printRelated(out, concept, reasoner::parents, 3);
            }
        }
    }

    public static void printRelated(PrintWriter out, Concept concept, Function<Concept,
            Collection<Concept>> producer
            , int offset) {
        String spaces = Utils.Strings.spaces(offset);
        for (var child : producer.apply(concept)) {
            out.println(spaces + child.getUrn());
            printRelated(out, child, producer, offset + 3);
        }
    }

    @Command(name = "strategy", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "Compute and visualize the observation strategy for an observable in the current context."},
             subcommands = {})
    public static class Strategy extends FunctionalCommand {

        @Spec
        CommandSpec commandSpec;

        @Option(names = {"-c", "--context"}, defaultValue = Parameters.NULL_VALUE, description = {
                "Choose a context for the observation (default is the current context)"}, required = false)
        String context;

        @Option(names = {"-w", "--within"}, defaultValue = Parameters.NULL_VALUE, description = {
                "Choose an observation to become the context of the observation.",
                "Use a dot to select the root subject if there is one."}, required = false)
        String within;

        @Option(names = {"-g", "--geometry"}, defaultValue = Parameters.NULL_VALUE, description = {
                "Specify a focal geometry for the context."}, required = false)
        String geometry;

        @Parameters
        java.util.List<String> observables;

        @Option(names = {"-a", "--acknowledgement"}, defaultValue = "false", description = {
                "Force a direct observable to represent the acknowledgement of the observable."}, required
                = false)
        boolean acknowledge;

        @Override
        public void run() {

            PrintWriter out = commandSpec.commandLine().getOut();
            PrintWriter err = commandSpec.commandLine().getErr();

            ContextScope ctx = context == null ? Engine.INSTANCE.getCurrentContext(true) :
                               Engine.INSTANCE.getContext(context);

            if (within != null) {
                // TODO find the context observation and switch the context to it. If a dot,
                // must have a single root subject
            }

            var urn = Utils.Strings.join(observables, " ");
            var reasoner = ctx.getService(org.integratedmodelling.klab.api.services.Reasoner.class);
            var observable = reasoner.resolveObservable(urn);

            if (observable == null) {
                err.println(Ansi.AUTO.string("URN @|red " + urn + "|@ does not resolve to a valid " +
                        "observable"));
                return;
            }

            if (acknowledge) {
                if (!observable.getDescriptionType().isInstantiation()) {
                    err.println(Ansi.AUTO.string("Cannot acknowledge something that is not countable"));
                    return;
                }
                observable = observable.builder(ctx).as(DescriptionType.ACKNOWLEDGEMENT).buildObservable();
            }

            out.println(Ansi.AUTO.string("Observation strategies for @|bold " + observable.getDescriptionType().name().toLowerCase()
                    + "|@ of @|green " + observable.getUrn() + "|@:"));
            for (var strategy : reasoner.inferStrategies(observable, ctx)) {
                out.println(Utils.Strings.indent(strategy.toString(),
                        Utils.Strings.fillUpLeftAligned(strategy.getRank() + ".",
                                " ", 4)));
            }
        }
    }

    @Command(name = "children", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "List the asserted child hierarchy of a concept."}, subcommands = {})
    public static class Children extends FunctionalCommand {

        @Spec
        CommandSpec commandSpec;

        @Parameters
        java.util.List<String> observables;

        @Override
        public void run() {

            PrintWriter out = commandSpec.commandLine().getOut();
            PrintWriter err = commandSpec.commandLine().getErr();

            var urn = Utils.Strings.join(observables, " ");
            var reasoner = Engine.INSTANCE.getCurrentUser()
                    .getService(org.integratedmodelling.klab.api.services.Reasoner.class);
            Concept concept = reasoner.resolveConcept(urn);
            if (concept == null) {
                err.println("Concept " + urn + " not found");
            } else {
                out.println(concept.getUrn());
                printRelated(out, concept, reasoner::children, 3);
            }
        }
    }


    @Command(name = "type", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "List the syntactic types of a concept."}, subcommands = {})
    public static class Type extends FunctionalCommand {

        @Spec
        CommandSpec commandSpec;

        @Parameters
        java.util.List<String> observables;

        @Override
        public void run() {

            PrintWriter out = commandSpec.commandLine().getOut();
            PrintWriter err = commandSpec.commandLine().getErr();

            var urn = Utils.Strings.join(observables, " ");
            var reasoner = Engine.INSTANCE.getCurrentUser()
                    .getService(org.integratedmodelling.klab.api.services.Reasoner.class);
            Concept concept = reasoner.resolveConcept(urn);
            if (concept == null) {
                err.println("Concept " + urn + " not found");
            } else {
                out.println("   " + concept.getType());
            }
        }
    }

    @Command(name = "traits", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "List all the traits in a concept."}, subcommands = {})
    public static class Traits extends FunctionalCommand {

        @Spec
        CommandSpec commandSpec;

        @Option(names = {"-i", "--inherited"}, defaultValue = "false", description = {
                "Include inherited traits"}, required = false)
        boolean inherited = false;

        @Parameters
        java.util.List<String> observables;

        @Override
        public void run() {

            PrintWriter out = commandSpec.commandLine().getOut();
            PrintWriter err = commandSpec.commandLine().getErr();

            var urn = Utils.Strings.join(observables, " ");
            var reasoner = Engine.INSTANCE.getCurrentUser()
                    .getService(org.integratedmodelling.klab.api.services.Reasoner.class);
            Concept concept = reasoner.resolveConcept(urn);
            if (concept == null) {
                err.println("Concept " + urn + " not found");
            } else {
                for (Concept c : inherited ? reasoner.traits(concept) : reasoner.directTraits(concept)) {
                    out.println(Ansi.AUTO.string("   @|yellow " + c + "|@ " + c.getType()));
                }
            }
        }

    }

    @Command(name = "export", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "export a namespace to an OWL ontology."}, subcommands = {})
    public static class Export extends FunctionalCommand {

        @Spec
        CommandSpec commandSpec;

        @Option(names = {"-o", "--output"}, description = {
                "Directory to output the results to"}, required = false, defaultValue = Parameters.NULL_VALUE)
        private File output;

        @Parameters
        String namespace;

        @Override
        public void run() {

            PrintWriter out = commandSpec.commandLine().getOut();
            PrintWriter err = commandSpec.commandLine().getErr();
            var reasoner = Engine.INSTANCE.getCurrentUser()
                    .getService(org.integratedmodelling.klab.api.services.Reasoner.class);

            if (reasoner instanceof org.integratedmodelling.klab.api.services.Reasoner.Admin) {

                if (((org.integratedmodelling.klab.api.services.Reasoner.Admin) reasoner).exportNamespace(namespace,
                        output == null ? Configuration.INSTANCE.getDefaultExportDirectory() : output)) {
                    out.println("Namespace " + namespace + " written to OWL ontologies in "
                            + (output == null ? Configuration.INSTANCE.getDefaultExportDirectory() : output));
                } else {
                    err.println("Export of namespace " + namespace + " failed");
                }
            } else {
                err.println("Reasoner does not offer administration services in this scope");
            }

        }

    }

}