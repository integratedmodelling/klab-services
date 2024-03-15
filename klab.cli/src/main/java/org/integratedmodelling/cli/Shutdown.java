package org.integratedmodelling.cli;

import org.integratedmodelling.klab.api.data.Version;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;

@CommandLine.Command(name = "shutdown", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
        "Shutdown the engine and all services started locally.",
        ""})
public class Shutdown implements Runnable {
    @Override
    public void run() {
        KlabCLI.INSTANCE.engine().shutdown();
    }
}
