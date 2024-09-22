package org.integratedmodelling.cli;

import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

@CommandLine.Command(name = "shutdown", mixinStandardHelpOptions = true, version = Version.CURRENT,
                     description = {
                             "Shutdown the engine and all services started locally.",
                             ""})
public class Shutdown implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec commandSpec;

    @CommandLine.Option(names = {"-e", "--exit"}, description = "Wait for all local services to shutdown " +
            "and exit")
    boolean exit;

    @Override
    public void run() {

        PrintWriter out = commandSpec.commandLine().getOut();
        PrintWriter err = commandSpec.commandLine().getErr();

        out.println(CommandLine.Help.Ansi.AUTO.string("@|cyan Shutting down local services" + (exit ? (" and" +
                " exiting to OS") : "") + "|@"));

        KlabCLI.INSTANCE.engine().shutdown();

        if (exit) {
            List<ServiceClient> services = new ArrayList<>();
            for (var serviceType : List.of(KlabService.Type.RESOURCES)) {
                for (var service :
                        KlabCLI.INSTANCE.engine().serviceScope().getServices(serviceType.classify())) {
                    if (service instanceof ServiceClient serviceClient && serviceClient.isLocal()) {
                        services.add(serviceClient);
                    }
                }
            }

            // 10 sec timeout
            final long timeout = 10000L;
            var ns = services.size();
            if (ns > 0) {

                out.println(CommandLine.Help.Ansi.AUTO.string("@|yellow Waiting for " + services.size() +
                        " local services to exit|@"));

                long time = System.currentTimeMillis();
                while (true) {

                    int n = 0;
                    for (var client : services) {
                        if (!client.getHttpClient().isAlive()) {
                            n++;
                        }
                    }

                    if (n == services.size()) {
                        out.println(CommandLine.Help.Ansi.AUTO.string("@|green All local services have shut" +
                                " down: exiting|@"));
                        System.exit(0);
                    }

                    if ((System.currentTimeMillis() - time) > timeout) {
                        err.println("Timeout reached: shutdown unsuccessful, continuing");
                        break;
                    }

                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        err.println("Thread exception: shutdown unsuccessful, continuing");
                        break;
                    }
                }
            }
        }
    }
}
