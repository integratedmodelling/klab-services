package org.integratedmodelling.kcli;

import java.io.PrintWriter;

import org.integratedmodelling.kcli.engine.Engine;
import org.integratedmodelling.kcli.functional.FunctionalCommand;
import org.integratedmodelling.kcli.visualization.Graphs;
import org.integratedmodelling.klab.Version;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.Resolution;
import org.integratedmodelling.klab.utilities.Utils;

import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "resolver", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
        "Commands to access resolution services.",
        ""}, subcommands = {Resolver.List.class, Resolver.Resolve.class, Resolver.Compile.class})
public class Resolver {

    @Command(name = "list", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "List and describe models known to the resolver.", ""}, subcommands = {})
    public static class List implements Runnable {

        @Override
        public void run() {
            // TODO Auto-generated method stub
            System.out.println("Hola");
        }

    }

    /**
     * TODO add options for minimum coverage, scenarios etc.
     * 
     * @author mario
     *
     */
    @Command(name = "resolve", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "Compute and optionally visualize a resolution graph.", "Resolve any URN in the current context."}, subcommands = {})
    public static class Resolve extends FunctionalCommand {

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
                "Specify a geometry for the new observation (must be a countable/substantial)."}, required = false)
        String geometry;

        @Parameters
        java.util.List<String> observables;

        @Option(names = {"-s", "--show"}, defaultValue = "false", description = {
                "Show the resolution graph after computing it."}, required = false)
        boolean show;

        @Override
        public void run() {

            PrintWriter out = commandSpec.commandLine().getOut();
            PrintWriter err = commandSpec.commandLine().getErr();

            ContextScope ctx = context == null ? Engine.INSTANCE.getCurrentContext(false) : Engine.INSTANCE.getContext(context);

            if (ctx == null) {
                out.println(Ansi.AUTO.string("No context for the observation! Create a context or choose among the existing."));
            } else {

                if (within != null) {
                    // TODO find the context observation and switch the context to it. If a dot,
                    // must have a single root subject
                }

                var urn = Utils.Strings.join(observables, " ");
                var resolver = ctx.getService(org.integratedmodelling.klab.api.services.Resolver.class);
                var knowledge = resolver.resolveKnowledge(urn, Knowledge.class, ctx);

                if (knowledge == null) {
                    err.println("URN " + urn + " does not resolve to any type of knowledge");
                    return;
                }

                Resolution resolution = resolver.resolve(knowledge, ctx);
                this.push(resolution);

                out.println("Resolution of " + resolution.getResolvable() + " terminated with "
                        + resolution.getCoverage().getCoverage() + " coverage");

                out.println(
                        Ansi.AUTO.string("@|" + (resolution.getCoverage().isRelevant() ? "green " : "red ") + resolution + "|@"));

                if (show) {
                    Graphs.show(resolution);
                }
            }

        }
    }

    @Command(name = "compile", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "Resolve some knowledge in the current context, compile a dataflow", "Prints or stores the JSON or the k.DL code",
            "Resolve any URN in the current context."}, subcommands = {})
    public static class Compile extends Resolve {

        @Override
        public void run() {

            super.run();

            if (lastPushed instanceof Resolution) {
                ContextScope ctx = context == null
                        ? Engine.INSTANCE.getCurrentContext(false)
                        : Engine.INSTANCE.getContext(context);
                var resolution = (Resolution) lastPushed;
                var resolver = ctx.getService(org.integratedmodelling.klab.api.services.Resolver.class);
                PrintWriter out = commandSpec.commandLine().getOut();
                PrintWriter err = commandSpec.commandLine().getErr();

                // Dataflow<Observation> dataflow = resolver.compile(null, null, ctx);
            }
        }
    }

}
