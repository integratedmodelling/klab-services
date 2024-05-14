package org.integratedmodelling.cli;

import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.Resolvable;
import org.integratedmodelling.klab.api.knowledge.observation.DirectObservation;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resolver.Resolution;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.File;
import java.io.PrintWriter;
import java.text.NumberFormat;

@Command(name = "resolver", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
        "Commands to access resolution services.",
        ""}, subcommands = {Resolver.List.class, Resolver.Resolve.class, Resolver.Compile.class, Resolver.Run.class})
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
     */
    @Command(name = "resolve", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "Compute and optionally visualize a resolution graph.", "Resolve any URN in the current context."},
             subcommands = {})
    public static class Resolve  {

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

        Resolution resolution;
        Resolvable knowledge;

//        @Override
        public void run() {

            PrintWriter out = commandSpec.commandLine().getOut();
            PrintWriter err = commandSpec.commandLine().getErr();

            ContextScope ctx = context == null ? KlabCLI.INSTANCE.modeler().currentContext() :
                               KlabCLI.INSTANCE.modeler().context(context);

            if (ctx == null) {
                out.println(Ansi.AUTO.string("No context for the observation! Create a context or choose among the " +
                        "existing."));
            } else {

                if (within != null) {
                    // TODO find the context observation and switch the context to it. If a dot,
                    // must have a single root subject
                }

                var urn = Utils.Strings.join(observables, " ");
                var resolver = ctx.getService(org.integratedmodelling.klab.api.services.Resolver.class);
                this.knowledge = resolver.resolveKnowledge(urn, Resolvable.class, ctx);

                if (knowledge == null) {
                    err.println("URN " + urn + " does not resolve to any type of knowledge");
                    return;
                }

                this.resolution = resolver.resolve(knowledge, ctx);
//                this.push(resolution);

                out.println(Ansi.AUTO.string("Resolution of @|yellow " + resolution.getResolvable() + "|@ terminated " +
                        "with @|"
                        + (resolution.getCoverage().isRelevant() ? "green " : "red ")
                        + NumberFormat.getPercentInstance().format(resolution.getCoverage().getCoverage()) + "|@ " +
                        "coverage"));

                out.println(
                        Ansi.AUTO.string("@|" + (resolution.getCoverage().isRelevant() ? "green " : "red ") + resolution + "|@"));

                if (show) {
//                    Graphs.show(resolution);
                }
            }

        }
    }

    @Command(name = "compile", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "Resolve some knowledge in the current context, compile a dataflow", "Prints or stores the JSON or the k" +
            ".DL code",
            "Resolve any URN in the current context."}, subcommands = {})
    public static class Compile extends Resolve {

        @Option(names = {"-j", "--json"}, defaultValue = "false", description = {
                "Print or output the graph as JSON instead of k.DL"}, required = false)
        boolean json;

        @Option(names = {"-o", "--output"}, description = {
                "File to output the results to"}, required = false, defaultValue = Parameters.NULL_VALUE)
        private File output;

        protected Dataflow<Observation> dataflow;

        @Override
        public void run() {

            super.run();

            if (this.resolution instanceof Resolution) {

                ContextScope ctx = context == null
                        ? KlabCLI.INSTANCE.modeler().currentContext()
                        : KlabCLI.INSTANCE.modeler().context(context);

                PrintWriter out = commandSpec.commandLine().getOut();
                PrintWriter err = commandSpec.commandLine().getErr();
                var resolver = ctx.getService(org.integratedmodelling.klab.api.services.Resolver.class);

                this.dataflow = resolver.compile(this.knowledge, this.resolution, ctx);

                if (dataflow.isEmpty()) {
                    err.println("Dataflow is empty");
                } else if (output == null) {
                    out.println(json ? Utils.Json.printAsJson(dataflow) : resolver.encodeDataflow(dataflow));
                } else {
                    Utils.Files.writeStringToFile(json ? Utils.Json.printAsJson(dataflow) :
                                    resolver.encodeDataflow(dataflow),
                            output);
                    out.println(Ansi.AUTO.string("Result written to @|yellow " + output + "|@"));
                }
            }
        }
    }

    @Command(name = "run", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "Resolve some knowledge in the current context, compile a dataflow and run it"}, subcommands = {})
    public static class Run extends Compile {

        @Override
        public void run() {

            super.run();

            PrintWriter err = commandSpec.commandLine().getErr();
            PrintWriter out = commandSpec.commandLine().getOut();

            if (this.dataflow != null && !this.dataflow.isEmpty()) {

                ContextScope ctx = context == null
                        ? KlabCLI.INSTANCE.modeler().currentContext()
                        : KlabCLI.INSTANCE.modeler().context(context);

//                ctx = this.knowledge instanceof Instance i ? ctx.withGeometry(i.getScale()) : ctx;

                var runtime = ctx.getService(org.integratedmodelling.klab.api.services.RuntimeService.class);
                var result = runtime.run(this.dataflow, ctx);
                out.println("Dataflow submitted to the runtime for execution");
                try {
                    dumpObservationStructure(result.get(), ctx, runtime, out, 0);
                } catch (Exception e) {
                    throw new KlabIOException(e);
                }

            } else {
                err.println("Dataflow is empty: not submitted to runtime");
            }
        }

        private void dumpObservationStructure(Observation observation, ContextScope scope, RuntimeService service,
                                              PrintWriter out, int level) {
            var spacer = Utils.Strings.spaces(level);
            out.println(Ansi.AUTO.string("@|green " + spacer + (observation instanceof DirectObservation dobs ?
                    (dobs.getName() + " ") : "") + observation.getObservable() + "|@"));
            for (var child : service.children(scope, observation)) {
                dumpObservationStructure(child, scope, service, out, level + 3);
            }
        }
    }
}
