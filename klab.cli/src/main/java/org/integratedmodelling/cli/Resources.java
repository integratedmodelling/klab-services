package org.integratedmodelling.cli;

import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.KimInstance;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KlabStatement;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceSet.Resource;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;

@Command(name = "resources", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
        "Commands to find, list, access and manipulate resources.", ""}, subcommands = {Resources.List.class,
                                                                                        Resources.Query.class, Resources.Services.class, Resources.Workspace.class, Resources.Project.class,
                                                                                        Resources.Components.class, Resources.Resolve.class})
public class Resources {

    @Command(name = "services", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "List and describe all the available resource services.", ""}, subcommands = {})
    public static class Services implements Runnable {

        @Override
        public void run() {
            // TODO Auto-generated method stub
            System.out.println("list services");
        }
    }

    @Command(name = "query", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "Query models that resolve a passed observable", ""}, subcommands = {})
    public static class Query implements Runnable {

        @Option(names = {"-s", "--service"}, defaultValue = "local" /* TODO initialize at null */,
                description = {
                        "Resource service to connect to"}, required = false)
        private String service;

        @Option(names = {"-c", "--source-code"}, defaultValue = "false", description = {
                "Print the original source code, if applicable, instead of the JSON specification"},
                required = false)
        private boolean source;

        @Option(names = {"-o", "--output"}, description = {
                "File to output the results to"}, required = false, defaultValue = Parameters.NULL_VALUE)
        private File output;

        @Parameters
        String[] urns;

        // TODO
        PrintStream out = System.out;

        @Override
        public void run() {

            Observable observable = KlabCLI.INSTANCE.modeler().currentUser()
                                                    .getService(org.integratedmodelling.klab.api.services.Reasoner.class)
                                                    .resolveObservable(Utils.Strings.join(urns, ' '));

            var service = KlabCLI.INSTANCE.service(this.service, ResourcesService.class);
            ResourceSet result = service.queryModels(observable, KlabCLI.INSTANCE.modeler().currentContext());
            out.println("Resource set: (TODO)");
            KlabCLI.printResourceSet(result, out, 3);

            if (result.getResults().size() > 0) {
                out.println(Ansi.AUTO.string("Displaying @|green " + result.getResults().size() + "|@ " +
                        "models:"));
                for (ResourceSet.Resource model : result.getResults()) {
                    out.println(Ansi.AUTO.string("   @|green " + model.getResourceUrn() + "|@"));
                    //					service.resolveNamespace(model.getResourceUrn(), Engine.INSTANCE
                    //					.getCurrentContext
                    //					(false));
                    //					out.println(Utils.Strings.indent(service.resolveModel(model
                    //					.getResourceUrn(), Engine
                    //					.INSTANCE.getCurrentContext(false)).get;
                }
            } else {
                out.println("No models found");
            }

        }
    }

    @Command(name = "resolve", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "Resolve a URN and describe the resulting resource set.", ""}, subcommands = {})
    public static class Resolve implements Runnable {

        @Option(names = {"-s", "--service"}, defaultValue = "local" /* TODO initialize at null */,
                description = {
                        "Resource service to connect to"}, required = false)
        private String service;

        @Option(names = {"-c", "--source-code"}, defaultValue = "false", description = {
                "Print the original source code, if applicable, instead of the JSON specification"},
                required = false)
        private boolean source;

        @Option(names = {"-o", "--output"}, description = {
                "File to output the results to"}, required = false, defaultValue = Parameters.NULL_VALUE)
        private File output;

        @Parameters
        String urn;

        // TODO
        PrintStream out = System.out;

        @Override
        public void run() {
            var service = KlabCLI.INSTANCE.service(this.service, ResourcesService.class);
            if (service != null) {
                ResourceSet asset = service.resolve(urn, KlabCLI.INSTANCE.modeler().currentUser());
                out.println("Resource set: (TODO)");
                KlabCLI.printResourceSet(asset, out, 3);
                out.println("Results:");
                for (Resource result : asset.getResults()) {

                    String text = result.getResourceUrn();

                    out.println(Ansi.AUTO
                            .string("   " + Utils.Strings.capitalize(result.getKnowledgeClass().name().toLowerCase())
                                    + " @|green " + result.getResourceUrn() + "|@ listing:"));

                    switch (result.getKnowledgeClass()) {
                        case APPLICATION:
                        case BEHAVIOR:
                        case SCRIPT:
                        case TESTCASE:
                            text = listApplication(result.getResourceUrn(), source, service);
                            break;
                        case INSTANCE:
                        case MODEL:
                        case RESOURCE:
                            text = listObject(result.getResourceUrn(), source, service,
                                    result.getKnowledgeClass());
                            break;
                        case NAMESPACE:
                            text = listNamespace(result.getResourceUrn(), source, service);
                            break;
                        case OBSERVABLE:
                            break;
                        case PROJECT:
                            break;
                        case COMPONENT:
                            break;
                        case CONCEPT:
                            break;
                        default:
                            break;

                    }

                    if (output == null) {
                        out.println(Utils.Strings.indent(text, 6));
                    } else {
                        Utils.Files.writeStringToFile(text, output);
                        out.println(Ansi.AUTO.string("      Result written to @|yellow " + output + "|@"));
                    }
                }
            }
        }

    }

    @Command(name = "list", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "List and describe local or remote resources.", ""}, subcommands = {})
    public static class List implements Runnable {

        @Option(names = {"-s", "--service"}, defaultValue = "local", description = {
                "Resource service to connect to"}, required = false)
        private String service;

        @Option(names = {"-n", "--namespaces"}, defaultValue = "false", description = {
                "List namespaces"}, required = false)
        boolean namespaces;

        @Option(names = {"-b", "--behaviors"}, defaultValue = "false", description = {
                "List behaviors"}, required = false)
        boolean behaviors;

        @Option(names = {"-t", "--tests"}, defaultValue = "false", description = {
                "List test cases"}, required = false)
        boolean tests;

        @Option(names = {"-sc", "--scripts"}, defaultValue = "false", description = {
                "List scripts"}, required = false)
        boolean scripts;

        @Option(names = {"-a", "--applications"}, defaultValue = "false", description = {
                "List applications"}, required = false)
        boolean applications;

        @Option(names = {"-r", "--resources"}, defaultValue = "false", description = {
                "List resources"}, required = false)
        boolean resources;

        @Option(names = {"-c", "--components"}, defaultValue = "false", description = {
                "List components"}, required = false)
        boolean components;

        @Option(names = {"-v", "--verbose"}, defaultValue = "false", description = {
                "List projects in each workspace"}, required = false)
        private boolean verbose;

        @Parameters(description = "A query with wildcards. If not passed, all matches are returned.",
                    defaultValue = "__ALL__")
        String query;

        // TODO
        PrintStream out = System.out;

        @Override
        public void run() {
            if (namespaces) {
                out.println("Namespaces:");
                listNamespaces();
            }
            if (resources) {
                out.println("Resources:");
                listResources();
            }
            if (scripts) {
                out.println("Scripts:");
                listScripts();
            }
            if (tests) {
                out.println("Test cases:");
                listTestCases();
            }
            if (behaviors) {
                out.println("Behaviors:");
                listBehaviors();
            }
            if (applications) {
                out.println("Applications:");
                listApplications();
            }
            if (components) {
                out.println("Components:");
                listComponents();
            }
        }

        private void listNamespaces() {
            // TODO Auto-generated method stub

        }

        private void listComponents() {
            // TODO Auto-generated method stub

        }

        private void listBehaviors() {
            // TODO Auto-generated method stub

        }

        private void listTestCases() {
            // TODO Auto-generated method stub

        }

        private void listScripts() {
            // TODO Auto-generated method stub

        }

        private void listApplications() {
            // TODO Auto-generated method stub

        }

        public void listResources() {
            var service = KlabCLI.INSTANCE.service(this.service, ResourcesService.class);
            if (service instanceof ResourcesService.Admin) {
                for (var urn : ((ResourcesService.Admin) service).listResourceUrns()) {
                    System.out.println("   " + urn);
                }
            }
        }

    }

    @Command(name = "workspace", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "Workspace operations",
            ""}, subcommands = {Workspace.List.class, Workspace.Remove.class})
    public static class Workspace {

        @Command(name = "list", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
                "List and describe local workspaces.", ""}, subcommands = {})
        public static class List implements Runnable {

            @Option(names = {"-v", "--verbose"}, defaultValue = "false", description = {
                    "List projects in each workspace"}, required = false)
            private boolean verbose;

            @Option(names = {"-s", "--service"}, defaultValue = "local", description = {
                    "Resource service to connect to"}, required = false)
            private String service;

            @Override
            public void run() {
                var service = KlabCLI.INSTANCE.service(this.service, ResourcesService.class);
                for (var workspace : service.listWorkspaces()) {
                    System.out.println("   " + workspace.getUrn());
                    if (verbose) {
                        for (var project : workspace.getProjects()) {
                            System.out.println("      " + project.getUrn());
                        }
                    }
                }
            }

        }

        @Command(name = "remove", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
                "Remove a workspace from this service.", ""}, subcommands = {})
        public static class Remove implements Runnable {

            @Option(names = {"-s", "--service"}, defaultValue = "local", description = {
                    "Resource service to connect to"}, required = false)
            private String service;
            @Parameters
            private String workspace;

            @Override
            public void run() {
                var service = KlabCLI.INSTANCE.service(this.service, ResourcesService.class);
                if (service instanceof ResourcesService.Admin) {
                    ((ResourcesService.Admin) service).removeWorkspace(workspace);
                }
            }
        }

    }

    @Command(name = "project", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "Workspace operations", ""}, subcommands = {Project.List.class,
                                                        Project.Add.class,
                                                        Project.Remove.class})
    public static class Project {

        @Command(name = "list", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
                "List and describe local projects.", ""}, subcommands = {})
        public static class List implements Runnable {

            @CommandLine.Spec
            CommandLine.Model.CommandSpec commandSpec;

            @Option(names = {"-s", "--service"}, defaultValue = "local", description = {
                    "Resource service to connect to"}, required = false)
            private String service;

            @Option(names = {"-v", "--verbose"}, defaultValue = "false", description = {
                    "List project contents and metadata"}, required = false)
            private boolean verbose;

            @Override
            public void run() {

                PrintWriter out = commandSpec.commandLine().getOut();
                PrintWriter err = commandSpec.commandLine().getErr();

                var service = KlabCLI.INSTANCE.service(this.service, ResourcesService.class);
                if (service instanceof ResourcesService.Admin) {
                    for (var project : ((ResourcesService.Admin) service).listProjects()) {
                        out.println("   " + project.getUrn());
                        if (verbose) {

                            boolean first = true;
                            for (var ontology : project.getOntologies()) {
                                if (first) {
                                    out.println("   Ontologies:");
                                }
                                out.println("      " + ontology.getUrn());
                                first = false;
                            }
                            first = true;
                            for (var namespace : project.getNamespaces()) {
                                if (first) {
                                    out.println("   Namespaces::");
                                }
                                out.println("      " + namespace.getUrn());
                                first = false;
                            }
                            for (var behavior : project.getBehaviors()) {
                                if (first) {
                                    out.println("   Behaviors::");
                                }
                                out.println("      " + behavior.getUrn());
                                first = false;
                            }
                            for (var app : project.getApps()) {
                                if (first) {
                                    out.println("   Applications:");
                                }
                                out.println("      " + app.getUrn());
                                first = false;
                            }
                            for (var testcase : project.getTestCases()) {
                                if (first) {
                                    out.println("   Test cases:");
                                }
                                out.println("      " + testcase.getUrn());
                                first = false;
                            }
                        }
                    }
                }
            }
        }

        @Command(name = "add", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
                "Add a new project to the scope of this service.", ""}, subcommands = {})
        public static class Add implements Runnable {

            @Option(names = {"-s", "--service"}, defaultValue = "local", description = {
                    "Resource service to connect to"}, required = false)
            private String service;

            @Option(names = {"-w", "--workspace"}, defaultValue = "local", description = {
                    "Workspace for the imported project"}, required = false)
            private String workspace;

            @Parameters
            private String projectUrl;

            @Override
            public void run() {

                KlabCLI.INSTANCE.modeler().importProject(projectUrl, workspace, service);

                try {
                    var url = new File(projectUrl).isDirectory() ? new File(projectUrl).toURI().toURL() :
                              new URL(projectUrl);
                    var service = KlabCLI.INSTANCE.service(this.service, ResourcesService.class);
                    if (service instanceof ResourcesService.Admin) {
                        if (!((ResourcesService.Admin) service).importProject(workspace,
                                url.toString(), false)) {
                            System.out.println("project " + projectUrl + " was present or in error, not " +
                                    "added");
                        } else {
                            System.out.println("project " + projectUrl + " added to workspace " + workspace);
                        }
                    } else {
                        System.out.println("service " + this.service + " does not have admin permissions in" +
                                " this scope");
                    }
                } catch (MalformedURLException e) {
                    System.err.println("Invalid project URL entered");
                }
            }
        }

        @Command(name = "remove", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
                "Remove a project from this service.", ""}, subcommands = {})
        public static class Remove implements Runnable {

            @Option(names = {"-s", "--service"}, defaultValue = "local", description = {
                    "Resource service to connect to"}, required = false)
            private String service;
            @Parameters
            private String project;

            @Override
            public void run() {
                var service = KlabCLI.INSTANCE.service(this.service, ResourcesService.class);
                if (service instanceof ResourcesService.Admin) {
                    ((ResourcesService.Admin) service).removeProject(project);
                }
            }
        }
    }

    @Command(name = "components", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
            "Components operations", ""}, subcommands = {Components.List.class,
                                                         Components.Add.class,
                                                         Components.Remove.class})
    public static class Components {

        @Command(name = "list", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
                "List and describe service components.", ""}, subcommands = {})
        public static class List implements Runnable {

            @Option(names = {"-s", "--service"}, defaultValue = "local", description = {
                    "Resource service to connect to"}, required = false)
            private String service;

            @Override
            public void run() {

            }

        }

        @Command(name = "add", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
                "Add a new component to the scope of a service.", ""}, subcommands = {})
        public static class Add implements Runnable {

            @Option(names = {"-s", "--service"}, defaultValue = "local", description = {
                    "Resource service to connect to"}, required = false)
            private String service;

            @Parameters
            String componentUrl;

            @Override
            public void run() {
                // TODO Auto-generated method stub
                System.out.println("list project");
            }

        }

        @Command(name = "remove", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
                "Remove a component from a service.", ""}, subcommands = {})
        public static class Remove implements Runnable {

            @Option(names = {"-s", "--service"}, defaultValue = "local", description = {
                    "Resource service to connect to"}, required = false)
            private String service;

            @Parameters
            String componentName;

            @Override
            public void run() {
            }

        }
    }

    public static String listApplication(String resourceUrn, boolean source, ResourcesService service) {
        KActorsBehavior behavior = service.resolveBehavior(resourceUrn,
                KlabCLI.INSTANCE.modeler().currentUser());
        return source ? behavior.getSourceCode() : Utils.Json.printAsJson(behavior);
    }

    public static String listNamespace(String resourceUrn, boolean source, ResourcesService service) {
        KimNamespace namespace = service.resolveNamespace(resourceUrn,
                KlabCLI.INSTANCE.modeler().currentUser());
        return source ? namespace.getSourceCode() : Utils.Json.printAsJson(namespace);
    }

    public static String listObject(String resourceUrn, boolean source, ResourcesService service,
                                    KnowledgeClass knowledgeClass) {

        String ns = Utils.Paths.getLeading(resourceUrn, '.');
        String on = Utils.Paths.getLast(resourceUrn, '.');

        KimNamespace namespace = service.resolveNamespace(ns, KlabCLI.INSTANCE.modeler().currentUser());
        for (KlabStatement statement : namespace.getStatements()) {
            if (knowledgeClass == KnowledgeClass.INSTANCE && statement instanceof KimInstance
                    && on.equals(((KimInstance) statement).getName())) {
                return source ? statement.sourceCode() : Utils.Json.printAsJson(statement);
            } /*else if (knowledgeClass == KnowledgeClass.MODEL && statement instanceof KimModel
					&& resourceUrn.equals(((KimModel) statement).getName())) {
				return source ? statement.getSourceCode() : Utils.Json.printAsJson(statement);
			}*/
        }

        return null;
    }

}
