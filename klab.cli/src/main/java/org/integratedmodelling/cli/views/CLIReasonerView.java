package org.integratedmodelling.cli.views;

import org.integratedmodelling.cli.KlabCLI;
import org.integratedmodelling.common.services.client.reasoner.ReasonerClient;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.DescriptionType;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import static picocli.CommandLine.Help.Ansi.AUTO;

@Command(
    name = "reason",
    mixinStandardHelpOptions = true,
    version = Version.CURRENT,
    description = {"Commands to find, access and manipulate semantic knowledge.", ""},
    subcommands = {
      CLIReasonerView.Children.class, CLIReasonerView.Parents.class, CLIReasonerView.Traits.class,
      CLIReasonerView.Type.class, CLIReasonerView.BaseConcept.class,
          CLIReasonerView.Compatible.class,
      CLIReasonerView.Subsumes.class, CLIReasonerView.Strategy.class, CLIReasonerView.Export.class,
      CLIReasonerView.Matching.class, CLIReasonerView.Roles.class, CLIReasonerView.Info.class
    })
public class CLIReasonerView {

  @Command(
      name = "parents",
      mixinStandardHelpOptions = true,
      version = Version.CURRENT,
      description = {"List the asserted parent hierarchy of a concept."},
      subcommands = {})
  public static class Parents implements Runnable {

    @Spec CommandSpec commandSpec;

    @Parameters List<String> observables;

    @Override
    public void run() {

      PrintWriter out = commandSpec.commandLine().getOut();
      PrintWriter err = commandSpec.commandLine().getErr();

      var urn = Utils.Strings.join(observables, " ");
      var reasoner = KlabCLI.INSTANCE.modeler().currentUser().getService(Reasoner.class);
      Concept concept = reasoner.resolveConcept(urn);
      if (concept == null) {
        err.println("Concept " + urn + " not found");
      } else {
        out.println(concept.getUrn());
        printRelated(out, concept, reasoner::parents, 3);
      }
    }
  }

  public static void printRelated(
      PrintWriter out,
      Concept concept,
      Function<Concept, Collection<Concept>> producer,
      int offset) {
    String spaces = Utils.Strings.spaces(offset);
    for (var child : producer.apply(concept)) {
      out.println(spaces + child.getUrn());
      printRelated(out, child, producer, offset + 3);
    }
  }

  @Command(
      name = "explain",
      mixinStandardHelpOptions = true,
      version = Version.CURRENT,
      description = {
        "Compute and visualize the observation strategy for an observable in the current context."
      },
      subcommands = {})
  public static class Strategy implements Runnable {

    @Spec CommandSpec commandSpec;

    @Option(
        names = {"-c", "--context"},
        defaultValue = Parameters.NULL_VALUE,
        description = {"Choose a context for the observation (default is the current context)"},
        required = false)
    String context;

    @Option(
        names = {"-w", "--within"},
        defaultValue = Parameters.NULL_VALUE,
        description = {
          "Choose an observation to become the context of the observation.",
          "Use a dot to select the root subject if there is one."
        },
        required = false)
    String within;

    @Option(
        names = {"-g", "--geometry"},
        defaultValue = Parameters.NULL_VALUE,
        description = {"Specify a focal geometry for the context."},
        required = false)
    String geometry;

    @Parameters List<String> observables;

    //    @Option(
    //        names = {"-a", "--acknowledgement"},
    //        defaultValue = "false",
    //        description = {
    //          "Force a direct" + " observable to represent the acknowledgement of the observable."
    //        },
    //        required = false)
    //    boolean acknowledge;

    @Override
    public void run() {

      PrintWriter out = commandSpec.commandLine().getOut();
      PrintWriter err = commandSpec.commandLine().getErr();

      ContextScope ctx =
          context == null
              ? KlabCLI.INSTANCE.modeler().getCurrentContext()
              : KlabCLI.INSTANCE.modeler().openNewContext(context);

      if (within != null) {
        // TODO find the context observation and switch the context to it. If a dot,
        // must have a single root subject
      }

      var urn = Utils.Strings.join(observables, " ");
      var reasoner = ctx.getService(Reasoner.class);
      var observable = reasoner.resolveObservable(urn);

      var geom = geometry == null ? null : Geometry.create(geometry);

      if (observable == null) {
        err.println(
            AUTO.string(
                "URN @|red " + urn + "|@ does not resolve to " + "a valid " + "observable"));
        return;
      }

      //      if (acknowledge) {
      //        if (!observable.getDescriptionType().isInstantiation()) {
      //          err.println(
      //              CommandLine.Help.Ansi.AUTO.string(
      //                  "Cannot acknowledge something that is not" + " countable"));
      //          return;
      //        }
      //        observable = observable.builder(ctx).as(DescriptionType.ACKNOWLEDGEMENT).build();
      //      }

      var observation =
          DigitalTwin.createObservation(
              KlabCLI.INSTANCE.modeler().getCurrentScope(), observable, geometry);

      out.println(
          AUTO.string(
              "Observation strategies for @|bold "
                  + observable.getDescriptionType().name().toLowerCase()
                  + "|@ of @|green "
                  + observable.getUrn()
                  + "|@:"));
      for (var strategy : reasoner.computeObservationStrategies(observation, ctx)) {
        out.println(
            Utils.Strings.indent(
                strategy.toString(),
                Utils.Strings.fillUpLeftAligned(strategy.getRank() + ".", " ", 4)));
      }
    }
  }

  @Command(
      name = "children",
      mixinStandardHelpOptions = true,
      version = Version.CURRENT,
      description = {"List the asserted child hierarchy of a concept."},
      subcommands = {})
  public static class Children implements Runnable {

    @Spec CommandSpec commandSpec;

    @Parameters List<String> observables;

    @Override
    public void run() {

      PrintWriter out = commandSpec.commandLine().getOut();
      PrintWriter err = commandSpec.commandLine().getErr();

      var urn = Utils.Strings.join(observables, " ");
      var reasoner = KlabCLI.INSTANCE.modeler().currentUser().getService(Reasoner.class);
      Concept concept = reasoner.resolveConcept(urn);
      if (concept == null) {
        err.println("Concept " + urn + " not found");
      } else {
        out.println(concept.getUrn());
        printRelated(out, concept, reasoner::children, 3);
      }
    }
  }

  @Command(
      name = "match",
      mixinStandardHelpOptions = true,
      version = Version.CURRENT,
      description = {"Check if the first concept matches the concept pattern of the second."},
      subcommands = {})
  public static class Matching implements Runnable {

    @Spec CommandSpec commandSpec;

    @Parameters List<String> arguments;

    @Override
    public void run() {

      PrintWriter out = commandSpec.commandLine().getOut();
      PrintWriter err = commandSpec.commandLine().getErr();

      List<List<String>> tokens = new ArrayList<>();

      var current = new ArrayList<String>();
      for (var token : arguments) {
        if (token.endsWith(",")) {
          if (token.trim().length() > 1) {
            current.add(token.trim().substring(0, token.length() - 1));
          }
          tokens.add(current);
          current = new ArrayList<>();
        } else {
          current.add(token);
        }
      }
      tokens.add(current);

      var urns = tokens.stream().map(l -> Utils.Strings.join(l, " ")).toList();
      var reasoner = KlabCLI.INSTANCE.modeler().currentUser().getService(Reasoner.class);
      var concepts = urns.stream().map(reasoner::resolveConcept).toList();
      if (concepts.size() != 2) {
        err.println(
            "Not enough arguments for compatibility check. Use commas to separate 2 or 3 "
                + "definitions.");
      } else {
        var distance = reasoner.match(concepts.get(0), concepts.get(1));
        out.println(
            AUTO.string(
                "@|blue "
                    + concepts.get(0)
                    + (distance ? "|@ @|green DOES|@" : "|@ @|red DOES NOT|@")
                    + " match pattern "
                    + "@|blue "
                    + concepts.get(1)
                    + "|@"));
      }
    }
  }

  @Command(
      name = "is",
      mixinStandardHelpOptions = true,
      version = Version.CURRENT,
      description = {
        "Check if the second concept subsumes the first, i.e. the first 'is' the second."
      },
      subcommands = {})
  public static class Subsumes implements Runnable {

    @Spec CommandSpec commandSpec;

    @Parameters List<String> arguments;

    @Override
    public void run() {

      PrintWriter out = commandSpec.commandLine().getOut();
      PrintWriter err = commandSpec.commandLine().getErr();

      List<List<String>> tokens = new ArrayList<>();

      var current = new ArrayList<String>();
      for (var token : arguments) {
        if (token.endsWith(",")) {
          if (token.trim().length() > 1) {
            current.add(token.trim().substring(0, token.length() - 1));
          }
          tokens.add(current);
          current = new ArrayList<>();
        } else {
          current.add(token);
        }
      }
      tokens.add(current);

      var urns = tokens.stream().map(l -> Utils.Strings.join(l, " ")).toList();
      var reasoner = KlabCLI.INSTANCE.modeler().currentUser().getService(Reasoner.class);
      var concepts = urns.stream().map(reasoner::resolveConcept).toList();
      if (concepts.size() != 2) {
        err.println(
            "Not enough arguments for subsumption check. Use commas to separate 2 or 3 "
                + "definitions.");
      } else {
        var distance = reasoner.is(concepts.get(0), concepts.get(1));
        out.println(
            AUTO.string(
                "@|blue "
                    + concepts.get(0)
                    + (distance ? "|@ @|green IS|@" : "|@ @|red IS NOT|@")
                    + " @|blue "
                    + concepts.get(1)
                    + "|@"));
      }
    }
  }

  @Command(
      name = "compatible",
      mixinStandardHelpOptions = true,
      version = Version.CURRENT,
      description = {"Check if two concepts are compatible, optionally in context."},
      subcommands = {})
  public static class Compatible implements Runnable {

    @Spec CommandSpec commandSpec;

    @Parameters List<String> arguments;

    @Override
    public void run() {

      PrintWriter out = commandSpec.commandLine().getOut();
      PrintWriter err = commandSpec.commandLine().getErr();

      List<List<String>> tokens = new ArrayList<>();

      var current = new ArrayList<String>();
      for (var token : arguments) {
        if (token.endsWith(",")) {
          if (token.trim().length() > 1) {
            current.add(token.trim().substring(0, token.length() - 1));
          }
          tokens.add(current);
          current = new ArrayList<>();
        } else {
          current.add(token);
        }
      }
      tokens.add(current);

      var urns = tokens.stream().map(l -> Utils.Strings.join(l, " ")).toList();
      var reasoner = KlabCLI.INSTANCE.modeler().currentUser().getService(Reasoner.class);
      var concepts = urns.stream().map(reasoner::resolveConcept).toList();
      if (concepts.size() < 2) {
        err.println(
            "Not enough arguments for compatibility check. Use commas to separate 2 or 3 "
                + "definitions.");
      } else {

        var distance =
            concepts.size() == 2
                ? reasoner.compatible(concepts.get(0), concepts.get(1))
                : reasoner.contextuallyCompatible(
                    concepts.get(0), concepts.get(1), concepts.get(2));

        out.println(
            "Compatibility check  "
                + (distance ? "SUCCESSFUL" : "UNSUCCESSFUL")
                + " "
                + "between "
                + concepts.get(0)
                + " and "
                + concepts.get(1)
                + (concepts.size() == 2 ? "" : (" in context of " + concepts.get(3))));
      }
    }
  }

  @Command(
      name = "base",
      mixinStandardHelpOptions = true,
      version = Version.CURRENT,
      description = {"Display the declared base concept for a concept."},
      subcommands = {})
  public static class BaseConcept implements Runnable {

    @Spec CommandSpec commandSpec;
    @Parameters List<String> observables;

    @Override
    public void run() {

      PrintWriter out = commandSpec.commandLine().getOut();
      PrintWriter err = commandSpec.commandLine().getErr();

      var urn = Utils.Strings.join(observables, " ");
      var reasoner = KlabCLI.INSTANCE.modeler().currentUser().getService(Reasoner.class);

      Concept concept = reasoner.resolveConcept(urn);
      if (concept == null) {
        err.println("Concept " + urn + " not found");
      } else {
        if (concept.is(SemanticType.TRAIT)) {
          out.println(
              AUTO.string(
                  "Base parent trait: @|green "
                      + reasoner.baseParentTrait(concept).getUrn()
                      + "|@"));
        } else {
          out.println(
              AUTO.string(
                  "Base observable: @|green " + reasoner.baseParentTrait(concept).getUrn() + "|@"));
        }
      }
    }
  }

  @Command(
      name = "type",
      mixinStandardHelpOptions = true,
      version = Version.CURRENT,
      description = {"List the syntactic types of a concept."},
      subcommands = {})
  public static class Type implements Runnable {

    @Spec CommandSpec commandSpec;

    @Parameters List<String> observables;

    @Override
    public void run() {

      PrintWriter out = commandSpec.commandLine().getOut();
      PrintWriter err = commandSpec.commandLine().getErr();

      var urn = Utils.Strings.join(observables, " ");
      var reasoner = KlabCLI.INSTANCE.modeler().currentUser().getService(Reasoner.class);
      Concept concept = reasoner.resolveConcept(urn);
      if (concept == null) {
        err.println("Concept " + urn + " not found");
      } else {
        out.println("   " + concept.getType());
      }
    }
  }

  @Command(
      name = "traits",
      mixinStandardHelpOptions = true,
      version = Version.CURRENT,
      description = {"List all the traits in a concept."},
      subcommands = {})
  public static class Traits implements Runnable {

    @Spec CommandSpec commandSpec;

    @Option(
        names = {"-i", "--inherited"},
        defaultValue = "false",
        description = {"Include inherited " + "traits"},
        required = false)
    boolean inherited = false;

    @Parameters List<String> observables;

    @Override
    public void run() {

      PrintWriter out = commandSpec.commandLine().getOut();
      PrintWriter err = commandSpec.commandLine().getErr();

      var urn = Utils.Strings.join(observables, " ");
      var reasoner = KlabCLI.INSTANCE.modeler().currentUser().getService(Reasoner.class);
      Concept concept = reasoner.resolveConcept(urn);
      if (concept == null) {
        err.println("Concept " + urn + " not found");
      } else {
        for (Concept c : inherited ? reasoner.traits(concept) : reasoner.directTraits(concept)) {
          out.println(AUTO.string("   @|yellow " + c + "|@ " + c.getType()));
        }
      }
    }
  }

  @Command(
      name = "roles",
      mixinStandardHelpOptions = true,
      version = Version.CURRENT,
      description = {"List all the roles in a concept."},
      subcommands = {})
  public static class Roles implements Runnable {

    @Spec CommandSpec commandSpec;

    @Option(
        names = {"-i", "--inherited"},
        defaultValue = "false",
        description = {"Include inherited " + "traits"},
        required = false)
    boolean inherited = false;

    @Parameters List<String> observables;

    @Override
    public void run() {

      PrintWriter out = commandSpec.commandLine().getOut();
      PrintWriter err = commandSpec.commandLine().getErr();

      var urn = Utils.Strings.join(observables, " ");
      var reasoner = KlabCLI.INSTANCE.modeler().currentUser().getService(Reasoner.class);
      Concept concept = reasoner.resolveConcept(urn);
      if (concept == null) {
        err.println("Concept " + urn + " not found");
      } else {
        for (Concept c : inherited ? reasoner.roles(concept) : reasoner.directRoles(concept)) {
          out.println(AUTO.string("   @|yellow " + c + "|@ " + c.getType()));
        }
      }
    }
  }

  @Command(
      name = "export",
      mixinStandardHelpOptions = true,
      version = Version.CURRENT,
      description = {"export a namespace to an OWL ontology."},
      subcommands = {})
  public static class Export implements Runnable {

    @Spec CommandSpec commandSpec;

    @Option(
        names = {"-o", "--output"},
        description = {"Directory to output the results to"},
        required = false,
        defaultValue = Parameters.NULL_VALUE)
    private File output;

    @Parameters String namespace;

    @Override
    public void run() {

      PrintWriter out = commandSpec.commandLine().getOut();
      PrintWriter err = commandSpec.commandLine().getErr();
      var reasoner = KlabCLI.INSTANCE.modeler().currentUser().getService(Reasoner.class);

      if (reasoner instanceof Reasoner.Admin) {

        if (((Reasoner.Admin) reasoner)
            .exportNamespace(
                namespace,
                output == null ? Configuration.INSTANCE.getDefaultExportDirectory() : output)) {
          out.println(
              "Namespace "
                  + namespace
                  + " written to OWL ontologies in "
                  + (output == null ? Configuration.INSTANCE.getDefaultExportDirectory() : output));
        } else {
          err.println("Export of namespace " + namespace + " failed");
        }
      } else {
        err.println("Reasoner does not offer administration services in this scope");
      }
    }
  }

  @Command(
      name = "info",
      mixinStandardHelpOptions = true,
      version = Version.CURRENT,
      description = {"Report an observational summary for one or more concepts."},
      subcommands = {})
  public static class Info implements Runnable {

    @Spec CommandSpec commandSpec;

    @Option(
        names = {"-a", "--alternative"},
        defaultValue = "false",
        description = {"Include inherited " + "traits"},
        required = false)
    boolean alternative = false;

    @Parameters List<String> arguments;

    @Override
    public void run() {

      PrintWriter out = commandSpec.commandLine().getOut();
      PrintWriter err = commandSpec.commandLine().getErr();

      List<List<String>> tokens = new ArrayList<>();

      var current = new ArrayList<String>();
      for (var token : arguments) {
        if (token.endsWith(",")) {
          if (token.trim().length() > 1) {
            current.add(token.trim().substring(0, token.length() - 1));
          }
          tokens.add(current);
          current = new ArrayList<>();
        } else {
          current.add(token);
        }
      }
      tokens.add(current);

      var reasoner = KlabCLI.INSTANCE.user().getService(Reasoner.class);

      for (var urn : tokens.stream().map(l -> Utils.Strings.join(l, " ")).toList()) {

        Concept concept = null;

        if (alternative && reasoner instanceof ReasonerClient reasonerClient) {
          concept = reasonerClient.resolveConceptAlternative(urn);
        } else {
          concept = reasoner.resolveConcept(urn);
        }

        if (concept != null) {
          out.println(AUTO.string("Normalized URN: @|blue " + concept.getUrn() + "|@"));
          out.println(describe(concept, reasoner));
        } else {
          out.println(AUTO.string("UNKNOWN: @|red " + urn + "|@"));
        }

        //
        //                for (IConcept c : concept.getOperands()) {
        //                    ret += (ret.isEmpty() ? "\n" : (concept.is(Type.UNION) ? "\n  OR\n" :
        //                    "\n  AND\n")) + Observables.INSTANCE.describe(c);
        //                }
        //
        //                if (observable != null) {
        //                    ret += "\nObservation type: " + observable.getDescriptionType() +
        // "\n";
        //                    ret += "Generic: " + (observable.isGeneric() ? "true" : "false") +
        // "\n";
        //                }

      }
    }

    //        private String describe(Ontology ontology) {
    //
    //            String ret = "";
    //            ret += "Imports:\n" + printImports(ontology, 3, new HashSet<>());
    //            ret += "Concepts:\n";
    //            for (IConcept c : ontology.getConcepts()) {
    //                ret += "   " + c + " [" + c.getDefinition() + "]" + "\n";
    //            }
    //            return ret;
    //        }

    //        private String printImports(IOntology owlOntology, int i, Set<IOntology> done) {
    //            String ret = "";
    //            String spaces = StringUtil.spaces(i);
    //            for (IOntology o : owlOntology.getImports(false)) {
    //                boolean added = done.add(o);
    //                ret += spaces + o + "\n" + (added ? printImports(o, i + 3, done) : "");
    //            }
    //            return ret;
    //        }

  }

  private static String describe(Concept concept, Reasoner reasoner) {

    var described = reasoner.describedType(concept);
    var comparison = reasoner.directRelativeTo(concept);

    //
    //                for (IConcept c : concept.getOperands()) {
    //                    ret += (ret.isEmpty() ? "\n" : (concept.is(Type.UNION) ? "\n  OR\n" : "\n
    //                    AND\n")) + Observables.INSTANCE.describe(c);
    //                }
    //

    StringBuilder ret = new StringBuilder();
    //        ret.append("OWL identifier:  ").append(concept).append(" (may not be unique)\n");
    ret.append("k.IM definition: ").append(concept.getUrn()).append("\n");
    ret.append("Core observable: ").append(reasoner.coreObservable(concept).getUrn()).append("\n");
    ret.append("Syntactic types: ").append(concept.getType()).append("\n");
    ret.append(concept.getUrn())
        .append(" is ")
        .append(
            concept.isAbstract()
                ? AUTO.string("@|yellow ABSTRACT|@")
                : AUTO.string("@|yellow CONCRETE|@"))
        .append(" and ")
        .append(
            concept.isCollective()
                ? AUTO.string("@|yellow COLLECTIVE|@")
                : AUTO.string("@|yellow SINGULAR|@"))
        .append("\n\n");

    if (described != null) {
      ret.append("           Describes: ")
          .append(described.getUrn())
          .append(comparison == null ? "" : (" vs. " + comparison.getUrn()))
          .append("\n");
    }
    ret.append("       Inherent type: ")
        .append(decl(reasoner.inherent(concept)))
        .append(" [direct: ")
        .append(decl(reasoner.directInherent(concept)))
        .append("]\n");
    ret.append("        Causant type: ")
        .append(decl(reasoner.causant(concept)))
        .append(" [direct: ")
        .append(decl(reasoner.directCausant(concept)))
        .append("]\n");
    ret.append("         Caused type: ")
        .append(decl(reasoner.caused(concept)))
        .append(" [direct: ")
        .append(decl(reasoner.directCaused(concept)))
        .append("]\n");
    ret.append("           Goal type: ")
        .append(decl(reasoner.goal(concept)))
        .append(" [direct: ")
        .append(decl(reasoner.directGoal(concept)))
        .append("]\n");
    ret.append("       Adjacent type: ")
        .append(decl(reasoner.adjacent(concept)))
        .append(" [direct: ")
        .append(decl(reasoner.directAdjacent(concept)))
        .append("]\n");
    ret.append("     Compresent type: ")
        .append(decl(reasoner.compresent(concept)))
        .append(" [direct: ")
        .append(decl(reasoner.directCompresent(concept)))
        .append("]\n");
    ret.append("   Co-occurrent type: ")
        .append(decl(reasoner.cooccurrent(concept)))
        .append(" [direct: ")
        .append(decl(reasoner.directCooccurrent(concept)))
        .append("]\n");

    var allTraits = reasoner.traits(concept);
    var dirTraits = reasoner.directTraits(concept);
    if (!allTraits.isEmpty()) {
      ret.append("\nTraits:\n");
      for (var trait : allTraits) {
        ret.append("    ")
            .append(trait.getUrn())
            .append(dirTraits.contains(trait) ? " [direct]" : " [indirect]")
            .append(" ")
            .append(trait.getType())
            .append("\n");
      }
    }

    var allRoles = reasoner.roles(concept);
    var dirRoles = reasoner.directRoles(concept);
    if (!allRoles.isEmpty()) {
      ret.append("\nRoles:\n");
      for (var trait : allRoles) {
        ret.append("    ")
            .append(trait.getUrn())
            .append(dirRoles.contains(trait) ? " [direct]" : " [indirect]")
            .append("\n");
      }
    }

    var affected = reasoner.affected(concept);
    if (!affected.isEmpty()) {
      ret.append("\nAffects:\n");
      for (var quality : affected) {
        ret.append("    ").append(quality.getUrn()).append("\n");
      }
    }

    //        Collection<IConcept> required = reasoner.requiredIdentities(concept.getType());
    //        if (!required.isEmpty()) {
    //            ret += "\nRequired identities:\n";
    //            for (IConcept identity : required) {
    //                ret += "    " + identity.getDefinition() + "\n";
    //            }
    //        }

    ret.append("\nMetadata:\n");
    for (String key : concept.getMetadata().keySet()) {
      ret.append("   ")
          .append(key)
          .append(": ")
          .append(concept.getMetadata().get(key))
          .append("\n");
    }

    if (!concept.getNotifications().isEmpty()) {
      ret.append("Notifications:\n");
      for (var notification : concept.getNotifications()) {
        var color =
            switch (notification.getLevel()) {
              case Debug -> AUTO.string("@|blue Debug  |@");
              case Info -> AUTO.string("@|blue Info   |@");
              case Warning -> AUTO.string("@|yellow Warning|@");
              case Error -> AUTO.string("@|red Error  |@");
              case SystemError -> AUTO.string("@|red SYSTEM |@");
            };
        ret.append("  ").append(color).append(notification.getMessage()).append("\n");
      }
    }

    //        Unit unit = Units.INSTANCE.getDefaultUnitFor(concept);
    //        if (unit != null) {
    //            ret += "\nDefault unit: " + unit + "\n";
    //        }

    return ret.toString();
  }

  private static String decl(Concept concept) {
    return concept == null
        ? AUTO.string("@|red NONE|@")
        : (AUTO.string("@|blue " + concept.getUrn() + "|@"));
  }
}
