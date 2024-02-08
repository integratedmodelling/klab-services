package org.integratedmodelling.kcli;

import org.integratedmodelling.kcli.engine.Engine;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.services.Reasoner;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;

@CommandLine.Command(name = "test", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
        "Commands to execute and report on k.Actors test cases.",
        ""}, subcommands = {Test.Run.class})
public class Test implements Runnable {


    @Override
    public void run() {
        /*
        Generic method to put temporary stuff to test quickly or debug with.
         */
        var session = Engine.INSTANCE.getCurrentSession(true, Engine.INSTANCE.getAnonymousScope());
        var urban = session.getService(Reasoner.class).resolveConcept("distance to infrastructure:City");
        var city = session.getService(Reasoner.class).resolveConcept("infrastructure:City");
        var obwithin = Observable.promote(urban).builder(session).of(city).build();
        System.out.println(obwithin.toString());

    }

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

            List<String> namespaces = new ArrayList<>();

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
