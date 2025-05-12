package org.integratedmodelling.cli.views;

import java.io.PrintWriter;
import java.util.List;
import org.integratedmodelling.cli.KlabCLI;
import org.integratedmodelling.common.knowledge.KnowledgeRepository;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.api.view.modeler.views.RuntimeView;
import org.integratedmodelling.klab.api.view.modeler.views.controllers.RuntimeViewController;
import picocli.CommandLine;

@CommandLine.Command(
    name = "observe",
    mixinStandardHelpOptions = true,
    version = Version.CURRENT,
    description = {"Commands to create, access and manipulate contexts.", ""},
    subcommands = {
      CLIObservationView.Session.class,
      CLIObservationView.Context.class,
      CLIObservationView.Clear.class
    })
public class CLIObservationView extends CLIView implements RuntimeView, Runnable {

  private static RuntimeViewController controller;

  public CLIObservationView() {
    controller = KlabCLI.INSTANCE.modeler().viewController(RuntimeViewController.class);
    controller.registerView(this);
  }

  @CommandLine.Spec CommandLine.Model.CommandSpec commandSpec;

  @CommandLine.Option(
      names = {"-a", "--add"},
      defaultValue = "false",
      description = {"Add to existing context as a parallel observation"},
      required = false)
  boolean addToContext = false;

  @CommandLine.Option(
      names = {"-w", "--within"},
      defaultValue = CommandLine.Parameters.NULL_VALUE,
      description = {
        "Choose an observation to become the context of the observation.",
        "Use a dot to select the root subject if there is one."
      },
      required = false)
  private String within;

  @CommandLine.Option(
      names = {"-g", "--geometry"},
      defaultValue = CommandLine.Parameters.NULL_VALUE,
      description = {
        "Override the geometry for the new observation (must be a " + "countable/substantial)."
      },
      required = false)
  private String geometry;

  @CommandLine.Parameters List<String> observables;

  @Override
  public void run() {

    PrintWriter out = commandSpec.commandLine().getOut();
    PrintWriter err = commandSpec.commandLine().getErr();

    if (observables == null || observables.isEmpty()) {
      //            int n = 1;
      //            if (observationsMade.isEmpty()) {
      //                out.println(CommandLine.Help.Ansi.AUTO.string("@|yellow No previous
      //                observations|@ "));
      //            }
      //            for (var urn : observationsMade) {
      //                out.println(CommandLine.Help.Ansi.AUTO.string("@|yellow " + n + ".|@ " +
      // urn));
      //            }
      return;
    }

    String urn = Utils.Strings.join(observables, " ");

    if (Utils.Numbers.encodesInteger(urn)) {
      int n = Integer.parseInt(urn) - 1;
      //            if (n < 0 || observationsMade.size() >= n) {
      //                err.println("No previous observation at index " + n);
      //                return;
      //            }
      //            // FIXME use SessionInfo for everything, remove any state from the controller
      // and
      //             engine except
      //            //  the current session/ctx
      //            urn = observationsMade.get(n);
    } else {
      //            observationsMade.add(urn);
    }

    var resources = KlabCLI.INSTANCE.user().getService(ResourcesService.class);

    var type = Urn.classify(urn);
    if (type == Urn.Type.OBSERVABLE) {

      var resolved = resources.retrieveObservable(urn);
      if (resolved != null) {
        out.println(
            CommandLine.Help.Ansi.AUTO.string(
                "Observation of @|yellow " + urn + "|@ " + " started"));
        KlabCLI.INSTANCE.modeler().observe(resolved, addToContext);
      } else {
        err.println(
            CommandLine.Help.Ansi.AUTO.string(
                "Can't resolve URN @|yellow " + urn + "|@ to an observable"));
      }

    } else if (type == Urn.Type.KIM_OBJECT || type == Urn.Type.RESOURCE) {

      var resolvable = resources.resolve(urn, KlabCLI.INSTANCE.user());
      var results =
          KnowledgeRepository.INSTANCE.ingest(resolvable, KlabCLI.INSTANCE.user(), KlabAsset.class);

      // TODO this is only for root observations
      if (!results.isEmpty()) {
        out.println(
            CommandLine.Help.Ansi.AUTO.string(
                "Observation of @|yellow "
                    + urn
                    + "|@ "
                    + "started in "
                    + results.getFirst().getUrn()));
        KlabCLI.INSTANCE.modeler().observe(results.getFirst(), addToContext);
      }

    } else {
      err.println(
          CommandLine.Help.Ansi.AUTO.string(
              "Can't resolve URN @|yellow " + urn + "|@ to observable knowledge"));
    }
  }

  @Override
  public void notifyNewDigitalTwin(ContextScope scope, RuntimeService service) {}

  @Override
  public void notifyDigitalTwinModified(DigitalTwin digitalTwin, Message change) {}

  @Override
  public void notifyObservationSubmission(Observation observation, ContextScope contextScope,
                                          RuntimeService service) {

  }

  @Override
  public void notifyObservationSubmissionAborted(Observation observation, ContextScope contextScope,
                                                 RuntimeService service) {

  }

  @Override
  public void notifyObservationSubmissionFinished(Observation observation, ContextScope contextScope,
                                                  RuntimeService service) {

  }

  @Override
  public void notifyContextObservationResolved(Observation observation, ContextScope contextScope,
                                               RuntimeService service) {

  }

  @Override
  public void notifyObserverResolved(Observation observation, ContextScope contextScope,
                                     RuntimeService service) {

  }

  @CommandLine.Command(
      name = "close",
      mixinStandardHelpOptions = true,
      version = Version.CURRENT,
      description = {"Close the active digital twin or session and delete all " + "observations"})
  public static class Clear implements Runnable {

    @CommandLine.ParentCommand CLIObservationView parent;

    @CommandLine.Spec CommandLine.Model.CommandSpec commandSpec;

    @CommandLine.Option(
        names = {"-f", "--force"},
        defaultValue = "false",
        description = {"Close the current scope without asking for confirmation"},
        required = false)
    boolean force = false;

    @Override
    public void run() {

      PrintWriter out = commandSpec.commandLine().getOut();
      PrintWriter err = commandSpec.commandLine().getErr();

      boolean isSession = false;
      Channel context = KlabCLI.INSTANCE.modeler().getCurrentContext();
      if (context == null) {
        context = KlabCLI.INSTANCE.modeler().getCurrentSession();
        isSession = true;
      }

      if (context == null) {
        err.println("No current context or session.");
        return;
      }

      if (force
          || KlabCLI.INSTANCE.confirm(
              "Delete the current "
                  + (isSession ? "session" : "context")
                  + " and ALL "
                  + (isSession ? "contexts and observations in them" : "observations in it"))) {

        context.close();

        out.println(
            (isSession ? "Session" : "Context")
                + " has been permanently closed and all "
                + "data have been deleted");

        KlabCLI.INSTANCE.modeler().setCurrentContext(null);
        if (isSession) {
          KlabCLI.INSTANCE.modeler().setCurrentSession(null);
        }
      }
    }
  }

  /* ---- subcommands ---- */

  @CommandLine.Command(
      name = "session",
      mixinStandardHelpOptions = true,
      version = Version.CURRENT,
      description = {
        "List the active sessions and optionally choose one by number or " + "name",
        ""
      },
      subcommands = {Session.New.class})
  public static class Session implements Runnable {

    @CommandLine.ParentCommand CLIObservationView parent;

    @CommandLine.Parameters(defaultValue = "__NULL__")
    String sessionNumberOrId;

    @CommandLine.Spec CommandLine.Model.CommandSpec commandSpec;

    @CommandLine.Option(names = "-v", description = "print session information when listing")
    boolean verbose;

    private static String displaySession(SessionScope session) {
      // TODO improve and react to verbose flag
      return "<" + session.getName() + ", id=" + session.getId() + ">";
    }

    @Override
    public void run() {

      PrintWriter out = commandSpec.commandLine().getOut();
      PrintWriter err = commandSpec.commandLine().getErr();

      if ("__NULL__".equals(sessionNumberOrId)) {

        var runtime = KlabCLI.INSTANCE.user().getService(RuntimeService.class);

        for (var session : runtime.getSessionInfo(KlabCLI.INSTANCE.user())) {
          // TODO this is the proper way
        }

        // FIXME below is the wrong way. Engine should only have a current session, the selected
        //  runtime knows the rest.
        int n = 1;
        if (KlabCLI.INSTANCE.modeler().getOpenSessions().isEmpty()) {
          out.println(CommandLine.Help.Ansi.AUTO.string("@|yellow No sessions|@ "));
        }
        for (var session : KlabCLI.INSTANCE.modeler().getOpenSessions()) {
          out.println(
              CommandLine.Help.Ansi.AUTO.string(
                  "@|green " + n + ". " + displaySession(session) + "|@"));
        }
        return;

      } else {

        SessionScope selected = null;

        if (Utils.Numbers.encodesInteger(sessionNumberOrId)) {
          int n = Integer.parseInt(sessionNumberOrId) - 1;
          if (n > 0 && KlabCLI.INSTANCE.modeler().getOpenSessions().size() < n) {
            selected = KlabCLI.INSTANCE.modeler().getOpenSessions().get(n);
          }
        } else
          for (var session : KlabCLI.INSTANCE.modeler().getOpenSessions()) {
            if (sessionNumberOrId.equals(session.getName())
                || sessionNumberOrId.equals(session.getId())) {
              selected = session;
              break;
            }
          }

        // select the session with the passed number or name/ID
        if (selected != null) {
          KlabCLI.INSTANCE.modeler().setCurrentSession(selected);
          out.println(
              CommandLine.Help.Ansi.AUTO.string(
                  "@|green Session " + displaySession(selected) + "selected|@ "));
        }
      }
    }

    @CommandLine.Command(
        name = "new",
        mixinStandardHelpOptions = true,
        version = Version.CURRENT,
        description = {"Create a new session and make it current.", ""},
        subcommands = {})
    public static class New implements Runnable {

      @CommandLine.ParentCommand Session parent;

      @CommandLine.Parameters(defaultValue = "__NULL__")
      String sessionName;

      @CommandLine.Spec CommandLine.Model.CommandSpec commandSpec;

      @Override
      public void run() {

        PrintWriter out = commandSpec.commandLine().getOut();
        PrintWriter err = commandSpec.commandLine().getErr();

        String sessionName =
            "__NULL__".equals(this.sessionName)
                ? ("Session " + (KlabCLI.INSTANCE.modeler().getOpenSessions().size() + 1))
                : this.sessionName;
        var ret = KlabCLI.INSTANCE.modeler().openNewSession(sessionName);
        out.println(
            CommandLine.Help.Ansi.AUTO.string("@|green New session " + displaySession(ret))
                + " created|@");
      }
    }
  }

  @CommandLine.Command(
      name = "context",
      mixinStandardHelpOptions = true,
      version = Version.CURRENT,
      description = {"Connect to an existing context.", ""},
      subcommands = {})
  public static class Context implements Runnable {

    @CommandLine.ParentCommand CLIObservationView parent;

    @Override
    public void run() {
      var runtime = KlabCLI.INSTANCE.user().getService(RuntimeService.class);
      for (var session : runtime.getSessionInfo(KlabCLI.INSTANCE.user())) {}
    }
  }
}
