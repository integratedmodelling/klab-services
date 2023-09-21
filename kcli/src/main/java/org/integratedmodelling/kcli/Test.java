package org.integratedmodelling.kcli;

import org.integratedmodelling.klab.Version;
import picocli.CommandLine;

import java.util.List;

@CommandLine.Command(name = "test", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
        "Commands to execute and report on k.Actors test cases.",
        ""}, subcommands = {Test.Run.class})
public class Test {


    @CommandLine.Command(name = "run", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "Run selected test cases or all in the workspace.",
            ""}, subcommands = {})
    public static class Run implements Runnable {

        @CommandLine.Option(names = {"-o", "--output"}, defaultValue = CommandLine.Parameters.NULL_VALUE,
                            description = {
                                    "Output AsciiDoc file (in ~/.klab/output or specified directory).", "Default is " +
                                    "~/" +
                                    ".klab/testoutput_<date>.adoc"}, required = false)
        String output;

        @CommandLine.Parameters
        java.util.List<String> testcases;

        @CommandLine.Option(names = {"-s", "--stop"}, defaultValue = "false", description = {
                "Stop at the first test failed."}, required = false)
        boolean stopOnFail;

        @Override
        public void run() {

            List<String> namespaces;

            if (testcases == null || testcases.isEmpty()) {

            /*
            No parameters: run all tests in workspace. Otherwise select tests (overlaps with `run` at top level) but
            with all options for logging etc. Can also "run" a project, i.e. all tests in it.
             */
            } else {
                namespaces.addAll(testcases);
            }

            // TODO Auto-generated method stub
            System.out.println("Hola");
        }

    }

}
