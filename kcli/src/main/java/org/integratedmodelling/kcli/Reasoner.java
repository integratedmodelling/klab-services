package org.integratedmodelling.kcli;

import java.io.File;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.function.Function;

import org.integratedmodelling.kcli.engine.Engine;
import org.integratedmodelling.kcli.functional.FunctionalCommand;
import org.integratedmodelling.klab.Version;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.configuration.Configuration;
import org.integratedmodelling.klab.utilities.Utils;

import groovyjarjarpicocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "reason", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
        "Commands to find, access and manipulate semantic knowledge.",
        ""}, subcommands = {Reasoner.Children.class, Reasoner.Parents.class, Reasoner.Traits.class,
        Reasoner.Export.class})
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

    public static void printRelated(PrintWriter out, Concept concept, Function<Concept, Collection<Concept>> producer
            , int offset) {
        String spaces = Utils.Strings.spaces(offset);
        for (var child : producer.apply(concept)) {
            out.println(spaces + child.getUrn());
            printRelated(out, child, producer, offset + 3);
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
                for (Concept c : inherited ? reasoner.directTraits(concept) : reasoner.traits(concept)) {
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