package org.integratedmodelling.kcli;

import java.io.PrintWriter;

import org.integratedmodelling.kcli.engine.Engine;
import org.integratedmodelling.kcli.functional.FunctionalCommand;
import org.integratedmodelling.kcli.visualization.Graphs;
import org.integratedmodelling.klab.Version;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.ResolutionGraph;
import org.integratedmodelling.klab.utilities.Utils;

import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "resolver", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
        "Commands to access resolution services.", ""}, subcommands = {Resolver.List.class, Resolver.Resolve.class})
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

    @Command(name = "resolve", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "Compute and optionally visualize a resolution graph.", "Resolve any URN in the current context."}, subcommands = {})
    public static class Resolve extends FunctionalCommand {

        @Spec
        CommandSpec commandSpec;

        @Option(names = {"-c", "--context"}, defaultValue = Parameters.NULL_VALUE, description = {
                "Choose a context for the observation (default is the current context)"}, required = false)
        private String context;

        @Option(names = {"-g", "--geometry"}, defaultValue = Parameters.NULL_VALUE, description = {
                "Specify a geometry for the new observation (must be a countable/substantial)."}, required = false)
        private String geometry;

        @Parameters
        java.util.List<String> observables;

        @Option(names = {"-s", "--show"}, defaultValue = "false", description = {
                "Show the resolution graph after computing it."}, required = false)
        boolean show;

        @Override
        public void run() {

            PrintWriter out = commandSpec.commandLine().getOut();
            ContextScope ctx = context == null ? Engine.INSTANCE.getCurrentContext(false) : Engine.INSTANCE.getContext(context);

            if (ctx == null) {
                out.println(Ansi.AUTO.string("No context for the observation! Create a context or choose among the existing."));
            } else {

                var resolver = ctx.getService(org.integratedmodelling.klab.api.services.Resolver.class);
                ResolutionGraph resolution = resolver.computeResolutionGraph(
                        resolver.resolveKnowledge(Utils.Strings.join(observables, " "), Knowledge.class, ctx), ctx);

                this.push(resolution);

                if (show) {
                    Graphs.show(resolution);
                } else {
                    out.println(Utils.Json.printAsJson(resolution));
                }

            }

        }

    }

}
