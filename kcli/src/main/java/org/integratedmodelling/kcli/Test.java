package org.integratedmodelling.kcli;

import org.integratedmodelling.klab.Version;
import picocli.CommandLine;

@CommandLine.Command(name = "test", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
        "Commands to execute and report on k.Actors test cases.",
        "" }, subcommands = {Test.Run.class})
public class Test {


    @CommandLine.Command(name = "run", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "Run selected test cases or all in the workspace.",
            "" }, subcommands = {})
    public static class Run implements Runnable {

        @Override
        public void run() {

            /*

             */

            // TODO Auto-generated method stub
            System.out.println("Hola");
        }

    }

}
