package org.integratedmodelling.cli.views;

import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.cli.KlabCLI;
import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.engine.distribution.Product;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.view.modeler.views.ServicesView;
import org.integratedmodelling.klab.api.view.modeler.views.controllers.ServicesViewController;
import picocli.CommandLine;

@CommandLine.Command(
    name = "services",
    mixinStandardHelpOptions = true,
    description = {
      "List, select and control services.",
      "Services can be started locally " + "or connected from the k" + ".LAB network.",
      "Service discovery is supported according to credentials.",
      ""
    },
    subcommands = {
      CLIServicesView.Start.class,
      CLIServicesView.Stop.class,
      CLIServicesView.Resources.class,
      CLIServicesView.Runtime.class
    })
public class CLIServicesView extends CLIView implements Runnable, ServicesView {

  private static ServicesViewController controller;
  private static AtomicReference<Engine.Status> status = new AtomicReference<>();

  public CLIServicesView() {
    controller = KlabCLI.INSTANCE.modeler().viewController(ServicesViewController.class);
    controller.registerView(this);
  }

  @CommandLine.Spec CommandLine.Model.CommandSpec commandSpec;

  @CommandLine.Option(
      names = {"-v", "--verbose"},
      defaultValue = "false",
      description = {"Display status and capabilities from services"},
      required = false)
  boolean verbose = false;

  @CommandLine.Option(
      names = {"-rs", "--reasoners"},
      defaultValue = "false",
      description = {"List all reasoner services."},
      required = false)
  boolean reasoners = false;

  @CommandLine.Option(
      names = {"-rv", "--resolvers"},
      defaultValue = "false",
      description = {"List all resolver services."},
      required = false)
  boolean resolvers = false;

  @CommandLine.Option(
      names = {"-rn", "--runtimes"},
      defaultValue = "false",
      description = {"List all runtime services."},
      required = false)
  boolean runtimes = false;

  @CommandLine.Option(
      names = {"-rr", "--resources"},
      defaultValue = "false",
      description = {"List all resource services."},
      required = false)
  boolean resources = false;

  @CommandLine.Option(
      names = {"-c", "--community"},
      defaultValue = "false",
      description = {"List all community services."},
      required = false)
  boolean community = false;

  @Override
  public void run() {

    PrintWriter out = commandSpec.commandLine().getOut();

    /*
     * TODO Print generic info about the service scope and the discovery strategy
     *  installed.
     */

    for (var serviceType :
        new KlabService.Type[] {
          KlabService.Type.REASONER,
          KlabService.Type.RESOURCES,
          KlabService.Type.RESOLVER,
          KlabService.Type.RUNTIME,
          KlabService.Type.COMMUNITY,
          KlabService.Type.ENGINE
        }) {

      if (serviceType == KlabService.Type.ENGINE) {
        // TODO describe the engine
      } else {

        //        boolean first = true;
        for (var service : KlabCLI.INSTANCE.user().getServices(serviceType.classify())) {

          if (reasoners && serviceType != KlabService.Type.REASONER
              || resolvers && serviceType != KlabService.Type.RESOLVER
              || resources && serviceType != KlabService.Type.RESOURCES
              || runtimes && serviceType != KlabService.Type.RUNTIME
              || community && serviceType != KlabService.Type.COMMUNITY) {
            continue;
          }

          var isDefault =
              service.equals(KlabCLI.INSTANCE.user().getService(serviceType.classify()));

          /*
           * TODO tag each service with a keyword or parameter so that it can be easily
           *  referenced using connect. Keep the services dictionary in the superclass.
           */

          //                            out.println(serviceType);
          // TODO number for selection; highlight the name of the "current" service in
          //  each category
          out.println(
              (isDefault ? "* " : "  ")
                  + Utils.Paths.getLast(service.getClass().getName(), '.')
                  + ": "
                  + service.getServiceName()
                  + " "
                  + " ["
                  + (service.status().isAvailable() ? "available" : "not available")
                  + (service instanceof ServiceClient client && client.isLocal()
                      ? "," + "local"
                      : "")
                  + "] "
                  + service.getUrl()
                  + ServicesAPI.CAPABILITIES);
          if (verbose) {
            out.println(
                Utils.Strings.indent(
                    Utils.Json.printAsJson(
                        service.capabilities(KlabCLI.INSTANCE.engine().getUsers().get(0))),
                    6));
          }
        }
      }
    }
  }

  public static class ServiceHandler {

    /**
     * TODO add optional arguments to fill in the entire request on the CLI by passing the schema id
     * and parameters. The help should list all parameters with a description (currently missing).
     *
     * @param serviceType
     */
    protected static void importFromSchema(
        KlabService.Type serviceType,
        boolean help,
        String suggestedUrn,
        java.util.List<String> arguments) {

      if (help) {

        return;
      }

      var service = KlabCLI.INSTANCE.user().getService(serviceType.classify());
      if (service != null) {
        KlabCLI.INSTANCE.importWithSchema(service, suggestedUrn, arguments);
      }
    }

    /**
     * TODO add optional arguments to fill in the entire request on the CLI by passing the schema id
     * and parameters. The help should list all parameters with a description (currently missing).
     *
     * @param serviceType
     */
    protected static void exportFromSchema(
        KlabService.Type serviceType, boolean help, java.util.List<String> arguments) {

      if (help) {
        return;
      }

      var service = KlabCLI.INSTANCE.user().getService(serviceType.classify());
      if (service != null) {
        KlabCLI.INSTANCE.exportWithSchema(service, arguments);
      }
    }
  }

  // TODO help should be custom and show the available schemata
  // TODO enable inline definitions
  @CommandLine.Command(
      name = "resources",
      subcommands = {Resources.Import.class, Resources.Export.class},
      mixinStandardHelpOptions = true,
      description = {
        "Connect to an " + "available " + "service",
        "Makes the service " + "available" + " for " + "requests."
      })
  public static class Resources extends ServiceHandler {

    @CommandLine.Option(
        names = {"-h", "--help"},
        defaultValue = "false",
        description = {"Display available import schemata"},
        required = false)
    boolean help = false;

    @CommandLine.Command(
        name = "import",
        mixinStandardHelpOptions = false,
        description = {
          "Connect to an " + "available " + "service",
          "Makes the service" + " available" + " for " + "requests."
        })
    public static class Import implements Runnable {

      @CommandLine.Option(
          names = {"-h", "--help"},
          defaultValue = "false",
          description = {"Display available import schemata"},
          required = false)
      boolean help = false;

      @CommandLine.Option(
          names = {"-u", "--urn"},
          defaultValue = "X:X:X:X",
          description = {"Pass suggested URN for import (result may differ)"},
          required = false)
      String urn;

      @CommandLine.Parameters java.util.List<String> arguments;

      @Override
      public void run() {
        importFromSchema(KlabService.Type.RESOURCES, help, urn, arguments);
      }
    }

    @CommandLine.Command(
        name = "export",
        mixinStandardHelpOptions = false,
        description = {
          "Connect to an " + "available " + "service",
          "Makes the service" + " available" + " for " + "requests."
        })
    public static class Export implements Runnable {

      @CommandLine.Option(
          names = {"-h", "--help"},
          defaultValue = "false",
          description = {"Display available import schemata"},
          required = false)
      boolean help = false;

      @CommandLine.Parameters java.util.List<String> arguments;

      @Override
      public void run() {
        exportFromSchema(KlabService.Type.RESOURCES, help, arguments);
      }
    }
  }

  // TODO help should be custom and show the available schemata
  // TODO enable inline definitions
  @CommandLine.Command(
      name = "runtime",
      subcommands = {Runtime.Import.class, Runtime.Export.class},
      mixinStandardHelpOptions = true,
      description = {
        "Connect to an " + "available " + "service",
        "Makes the service " + "available" + " for " + "requests."
      })
  static class Runtime extends ServiceHandler {

    @CommandLine.Command(
        name = "import",
        mixinStandardHelpOptions = false,
        description = {
          "Connect to an " + "available " + "service",
          "Makes the service" + " available" + " for " + "requests."
        })
    public static class Import implements Runnable {

      @CommandLine.Option(
          names = {"-h", "--help"},
          defaultValue = "false",
          description = {"Display available import schemata"},
          required = false)
      boolean help = false;

      @CommandLine.Option(
          names = {"-u", "--urn"},
          defaultValue = "X:X:X:X",
          description = {"Pass suggested URN for import (result may differ)"},
          required = false)
      String urn;

      @CommandLine.Parameters java.util.List<String> arguments;

      @Override
      public void run() {
        importFromSchema(KlabService.Type.RUNTIME, help, urn, arguments);
      }
    }

    @CommandLine.Command(
        name = "export",
        mixinStandardHelpOptions = false,
        description = {
          "Connect to an " + "available " + "service",
          "Makes the service" + " available" + " for " + "requests."
        })
    public static class Export implements Runnable {

      @CommandLine.Option(
          names = {"-h", "--help"},
          defaultValue = "false",
          description = {"Display available import schemata"},
          required = false)
      boolean help = false;

      @CommandLine.Parameters java.util.List<String> arguments;

      @Override
      public void run() {
        exportFromSchema(KlabService.Type.RUNTIME, help, arguments);
      }
    }
  }

  @CommandLine.Command(
      name = "start",
      mixinStandardHelpOptions = true,
      description = {"Start local services if a distribution is available."})
  static class Start implements Runnable {

    @CommandLine.Spec CommandLine.Model.CommandSpec commandSpec;

    @CommandLine.Option(
        names = {"-d", "--default"},
        defaultValue = "false",
        description = {"Make the connected services default"},
        required = false)
    boolean makeDefault = false;

    @Override
    public void run() {

      PrintWriter out = commandSpec.commandLine().getOut();
      var dStatus = KlabCLI.INSTANCE.engine().getDistributionStatus();
      if (dStatus.getDevelopmentStatus() == Product.Status.UP_TO_DATE) {
        out.println("Starting services from local source distribution");
      } else if (dStatus.getDownloadedStatus() != Product.Status.UNAVAILABLE) {
        out.println(
            CommandLine.Help.Ansi.AUTO.string(
                "Starting services from "
                    + (dStatus.getDownloadedStatus() == Product.Status.OBSOLETE
                        ? "@|yellow obsolete|@ "
                        : "up to date ")
                    + "local source distribution"));
      } else {
        out.println(
            CommandLine.Help.Ansi.AUTO.string(
                "@|yellow No k.LAB distribution is available locally|@"));
        return;
      }

      KlabCLI.INSTANCE.engine().startLocalServices();
    }
  }

  @CommandLine.Command(
      name = "stop",
      mixinStandardHelpOptions = true,
      description = {"Stop any local services started by this engine."})
  static class Stop implements Runnable {

    @Override
    public void run() {
      KlabCLI.INSTANCE.engine().stopLocalServices();
    }
  }

  @Override
  public void notifyServiceStatus(KlabService service, KlabService.ServiceStatus status) {}

  @Override
  public void engineStatusChanged(Engine.Status status) {
    CLIServicesView.status.set(status);
  }
}
