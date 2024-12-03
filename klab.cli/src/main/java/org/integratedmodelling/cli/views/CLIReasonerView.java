package org.integratedmodelling.cli.views;

import org.integratedmodelling.cli.KlabCLI;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.DescriptionType;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Function;

@Command(name = "reason", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
        "Commands to find, access and manipulate semantic knowledge.",
        ""}, subcommands = {CLIReasonerView.Children.class, CLIReasonerView.Parents.class, CLIReasonerView.Traits.class,
                            CLIReasonerView.Type.class, CLIReasonerView.BaseConcept.class, CLIReasonerView.Compatible.class,
                            CLIReasonerView.Strategy.class, CLIReasonerView.Export.class})
public class CLIReasonerView {


    @Command(name = "parents", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "List the asserted parent hierarchy of a concept."}, subcommands = {})
    public static class Parents implements Runnable {

        @Spec
        CommandSpec commandSpec;

        @Parameters
        java.util.List<String> observables;

        @Override
        public void run() {

            PrintWriter out = commandSpec.commandLine().getOut();
            PrintWriter err = commandSpec.commandLine().getErr();

            var urn = Utils.Strings.join(observables, " ");
            var reasoner = KlabCLI.INSTANCE.modeler().currentUser()
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

    @Command(name = "explain", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "Compute and visualize the observation strategy for an observable in the current context."},
             subcommands = {})
    public static class Strategy implements Runnable {

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

            ContextScope ctx = context == null ? KlabCLI.INSTANCE.modeler().getCurrentContext() :
                               KlabCLI.INSTANCE.modeler().openNewContext(context);

            if (within != null) {
                // TODO find the context observation and switch the context to it. If a dot,
                // must have a single root subject
            }

            var urn = Utils.Strings.join(observables, " ");
            var reasoner = ctx.getService(org.integratedmodelling.klab.api.services.Reasoner.class);
            var observable = reasoner.resolveObservable(urn);

            if (observable == null) {
                err.println(CommandLine.Help.Ansi.AUTO.string("URN @|red " + urn + "|@ does not resolve to " +
                        "a valid " +
                        "observable"));
                return;
            }

            if (acknowledge) {
                if (!observable.getDescriptionType().isInstantiation()) {
                    err.println(CommandLine.Help.Ansi.AUTO.string("Cannot acknowledge something that is not" +
                            " countable"));
                    return;
                }
                observable = observable.builder(ctx).as(DescriptionType.ACKNOWLEDGEMENT).build();
            }

            out.println(CommandLine.Help.Ansi.AUTO.string("Observation strategies for @|bold " + observable.getDescriptionType().name().toLowerCase()
                    + "|@ of @|green " + observable.getUrn() + "|@:"));
            //            for (var strategy : reasoner.inferStrategies(observable, ctx)) {
            //                out.println(Utils.Strings.indent(strategy.toString(),
            //                        Utils.Strings.fillUpLeftAligned(strategy.getCost() + ".",
            //                                " ", 4)));
            //            }
        }
    }

    @Command(name = "children", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "List the asserted child hierarchy of a concept."}, subcommands = {})
    public static class Children implements Runnable {

        @Spec
        CommandSpec commandSpec;

        @Parameters
        java.util.List<String> observables;

        @Override
        public void run() {

            PrintWriter out = commandSpec.commandLine().getOut();
            PrintWriter err = commandSpec.commandLine().getErr();

            var urn = Utils.Strings.join(observables, " ");
            var reasoner = KlabCLI.INSTANCE.modeler().currentUser()
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


    @Command(name = "compatible", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "Check if two concepts are compatible, optionally in context."}, subcommands = {})
    public static class Compatible implements Runnable {

        @Spec
        CommandSpec commandSpec;

        @Parameters
        java.util.List<String> arguments;
        @Override
        public void run() {

            PrintWriter out = commandSpec.commandLine().getOut();
            PrintWriter err = commandSpec.commandLine().getErr();

            java.util.List<java.util.List<String>> tokens = new ArrayList<>();

            var current = new ArrayList<String>();
            for (var token : arguments) {
                if (token.equals(",")) {
                    tokens.add(current);
                    current = new ArrayList<>();
                } else {
                    current.add(token);
                }
            }
            tokens.add(current);

            var urns = tokens.stream().map(l -> Utils.Strings.join(l, " ")).toList();
            var patterns = urns.stream().anyMatch(urn -> urn.contains("$"));

            var reasoner = KlabCLI.INSTANCE.modeler().currentUser().getService(Reasoner.class);
            var concepts = urns.stream().map(reasoner::resolveConcept).toList();
            if (concepts.size() < 2) {
                err.println("Not enough arguments for compatibility check. Use commas to separate 2 or 3 " +
                        "definitions.");
            } else {

                var distance = concepts.size() == 2 ?
                               (patterns ?
                                    reasoner.match(concepts.get(0), concepts.get(1)) :
                                    reasoner.compatible(concepts.get(0), concepts.get(1))) :
                               reasoner.contextuallyCompatible(concepts.get(0), concepts.get(1),
                                       concepts.get(2));

                out.println("Compatibility check  " + (distance ? "SUCCESSFUL" : "UNSUCCESSFUL") + " " +
                        "between " + concepts.get(0) + " and " + concepts.get(1) + (concepts.size() == 2 ?
                                                                                    "" :
                                                                                    (" in context of " + concepts.get(3))));

            }
        }
    }

    @Command(name = "base", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "Display the declared base concept for a concept."}, subcommands = {})
    public static class BaseConcept implements Runnable {

        @Spec
        CommandSpec commandSpec;

        @Parameters
        java.util.List<String> observables;

        @Override
        public void run() {

            PrintWriter out = commandSpec.commandLine().getOut();
            PrintWriter err = commandSpec.commandLine().getErr();

            var urn = Utils.Strings.join(observables, " ");
            var reasoner = KlabCLI.INSTANCE.modeler().currentUser()
                                           .getService(org.integratedmodelling.klab.api.services.Reasoner.class);
            Concept concept = reasoner.resolveConcept(urn);
            if (concept == null) {
                err.println("Concept " + urn + " not found");
            } else {
                if (concept.is(SemanticType.TRAIT)) {
                    out.println(CommandLine.Help.Ansi.AUTO.string("Base parent trait: @|green " + reasoner.baseParentTrait(concept).getUrn() + "|@"));
                } else {
                    out.println(CommandLine.Help.Ansi.AUTO.string("Base observable: @|green " + reasoner.baseParentTrait(concept).getUrn() + "|@"));
                }
            }
        }
    }

    @Command(name = "type", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "List the syntactic types of a concept."}, subcommands = {})
    public static class Type implements Runnable {

        @Spec
        CommandSpec commandSpec;

        @Parameters
        java.util.List<String> observables;

        @Override
        public void run() {

            PrintWriter out = commandSpec.commandLine().getOut();
            PrintWriter err = commandSpec.commandLine().getErr();

            var urn = Utils.Strings.join(observables, " ");
            var reasoner = KlabCLI.INSTANCE.modeler().currentUser()
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
    public static class Traits implements Runnable {

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
            var reasoner = KlabCLI.INSTANCE.modeler().currentUser()
                                           .getService(org.integratedmodelling.klab.api.services.Reasoner.class);
            Concept concept = reasoner.resolveConcept(urn);
            if (concept == null) {
                err.println("Concept " + urn + " not found");
            } else {
                for (Concept c : inherited ? reasoner.traits(concept) : reasoner.directTraits(concept)) {
                    out.println(CommandLine.Help.Ansi.AUTO.string("   @|yellow " + c + "|@ " + c.getType()));
                }
            }
        }

    }

    @Command(name = "export", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "export a namespace to an OWL ontology."}, subcommands = {})
    public static class Export implements Runnable {

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
            var reasoner = KlabCLI.INSTANCE.modeler().currentUser()
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
