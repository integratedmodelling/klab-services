package org.integratedmodelling.cli.views;

import org.integratedmodelling.cli.KlabCLI;
import org.integratedmodelling.cli.Resources;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableContainer;
import org.integratedmodelling.klab.api.view.modeler.views.ResourcesNavigator;
import org.integratedmodelling.klab.api.view.modeler.views.controllers.ResourcesNavigatorController;
import picocli.CommandLine;

import java.io.File;
import java.io.PrintStream;
import java.io.PrintWriter;

@CommandLine.Command(name = "resources", mixinStandardHelpOptions = true, version = Version.CURRENT,
                     description = {
                             "Commands to find, list, access and manipulate resources.", ""}, subcommands =
                             {CLIResourcesView.Import.class, CLIResourcesView.Rights.class,
                              CLIResourcesView.Components.class,
                              CLIResourcesView.List.class, CLIResourcesView.Project.class,
                              CLIResourcesView.Workspace.class})
public class CLIResourcesView extends CLIView implements ResourcesNavigator {

    private final ResourcesNavigatorController controller;

    public CLIResourcesView() {
        this.controller = KlabCLI.INSTANCE.modeler().viewController(ResourcesNavigatorController.class);
        this.controller.registerView(this);
    }

    @CommandLine.Command(name = "rights", mixinStandardHelpOptions = true, version = Version.CURRENT,
                         description = {
                                 "List and describe all the available resource services.", ""},
                         subcommands = {})
    public static class Rights implements Runnable {

        @CommandLine.Option(names = {"-s", "--service"}, defaultValue = "local" /* TODO initialize at null */,
                            description = {"Resource service to connect to"}, required = false)
        private String service;

        @CommandLine.Parameters(description = "The URN of the resource that we inquire or assign rights for")
        private String urn;

        @CommandLine.Option(names = {"-g", "--groups"}, defaultValue = CommandLine.Parameters.NULL_VALUE,
                            description = {"Comma" +
                                                   "-separated list of groups to set", "Use a +/- sign in " +
                                                   "front to define access type (+ is default)"})
        private java.util.List<String> groups;

        @CommandLine.Option(names = {"-u", "--users"}, defaultValue = CommandLine.Parameters.NULL_VALUE,
                            description = {"Comma" +
                                                   "-separated list of users to set", "Use a +/- sign in " +
                                                   "front to define access type (+ is default)"})
        private java.util.List<String> users;

        @Override
        public void run() {

            var service = KlabCLI.INSTANCE.service(this.service, ResourcesService.class);

            /*
            1. Find resource
            2. If no arguments, list rights
            3. If arguments, set rights and report
             */
            System.out.println("list rights");
        }
    }


    @CommandLine.Command(name = "list", mixinStandardHelpOptions = true, version = Version.CURRENT,
                         description = {
                                 "List and describe local or remote resources.", ""}, subcommands = {})
    public static class List implements Runnable {

        @CommandLine.Option(names = {"-s", "--service"}, defaultValue = "local", description = {"Resource service to " +
                                                                                                        "connect to"},
                            required = false)
        private String service;

        @CommandLine.Option(names = {"-n", "--namespaces"}, defaultValue = "false", description = {"List namespaces"},
                            required = false)
        boolean namespaces;

        @CommandLine.Option(names = {"-b", "--behaviors"}, defaultValue = "false", description = {"List behaviors"},
                            required = false)
        boolean behaviors;

        @CommandLine.Option(names = {"-t", "--tests"}, defaultValue = "false", description = {"List test cases"},
                            required = false)
        boolean tests;

        @CommandLine.Option(names = {"-sc", "--scripts"}, defaultValue = "false", description = {"List scripts"},
                            required = false)
        boolean scripts;

        @CommandLine.Option(names = {"-a", "--applications"}, defaultValue = "false", description = {"List applications"
        }, required = false)
        boolean applications;

        @CommandLine.Option(names = {"-r", "--resources"}, defaultValue = "false", description = {"List resources"},
                            required = false)
        boolean resources;

        @CommandLine.Option(names = {"-c", "--components"}, defaultValue = "false", description = {"List components"},
                            required = false)
        boolean components;

        @CommandLine.Option(names = {"-v", "--verbose"}, defaultValue = "false", description = {"List projects in each " +
                                                                                                        "workspace"},
                            required = false)
        private boolean verbose;

        @CommandLine.Parameters(description = "A query with wildcards. If not passed, all matches are " +
                "returned.",
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
                for (var urn :
                        ((ResourcesService.Admin) service).listResourceUrns(KlabCLI.INSTANCE.engine().serviceScope())) {
                    System.out.println("   " + urn);
                }
            }
        }
    }

    @CommandLine.Command(name = "import", mixinStandardHelpOptions = true, version = Version.CURRENT,
                         description = {
                                 "Import a resource into the current resources service from a project, an " +
                                         "archive file or a " +
                                         "local directory.", ""}, subcommands = {})
    public static class Import implements Runnable {

        @CommandLine.Option(names = {"-s", "--service"}, defaultValue = "local" /* TODO initialize at null */,
                            description = {"Resource service to connect to"}, required = false)
        private String service;

        @CommandLine.Parameters(description = "The URN of the resource that we inquire or assign rights for")
        private String source;

        @Override
        public void run() {

            var service = KlabCLI.INSTANCE.service(this.service, ResourcesService.class);

            File sourceFile = new File(source);

            if (sourceFile.exists()) {
                if (sourceFile.isDirectory()) {

                    File manifest = new File(sourceFile + File.separator + "resource.json");
                    if (manifest.exists()) {
                        // TODO zip it up and send it over
                    }

                } else if ("jar".equals(Utils.Files.getFileExtension(sourceFile)) || "zip".equals(Utils.Files.getFileExtension(sourceFile))) {
                    // TODO use other archive extensions
                    if (service instanceof ResourcesService.Admin admin) {
                        var result = admin.createResource(sourceFile, KlabCLI.INSTANCE.user());
                        for (var notification : result.getNotifications()) {
                            // TODO show nicely in console
                            System.out.println(notification.getLevel() + ": " + notification.getMessage());
                        }
                    } else {
                        throw new KlabIllegalStateException("Cannot perform admin operations on this " +
                                "service");
                    }
                }
            } else {
                // resolve as URN
            }

            /*
            1. Find resource
            2. If no arguments, list rights
            3. If arguments, set rights and report
             */
        }
    }


    @CommandLine.Command(name = "project", mixinStandardHelpOptions = true, version = Version.CURRENT,
                         description = {
                                 "Project operations", ""}, subcommands = {CLIResourcesView.Project.Add.class,
                                                                             CLIResourcesView.Project.Remove.class})
    public static class Project implements Runnable {

        @CommandLine.Spec
        CommandLine.Model.CommandSpec commandSpec;

        @CommandLine.Option(names = {"-s", "--service"}, defaultValue = "local", description = {
                "Resource service " +
                        "to connect" +
                        " to"},
                            required = false)
        private String service;

        @CommandLine.Option(names = {"-v", "--verbose"}, defaultValue = "false", description = {"List project " +
                                                                                                        "contents " +
                                                                                                        "and " +
                                                                                                        "metadata"}
                , required = false)
        private boolean verbose;

        @Override
        public void run() {

            PrintWriter out = commandSpec.commandLine().getOut();
            PrintWriter err = commandSpec.commandLine().getErr();

            var service = KlabCLI.INSTANCE.service(this.service, ResourcesService.class);
            if (service instanceof ResourcesService.Admin) {
                for (var project :
                        ((ResourcesService.Admin) service).listProjects(KlabCLI.INSTANCE.engine().serviceScope())) {
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

        @CommandLine.Command(name = "add", mixinStandardHelpOptions = true, version = Version.CURRENT,
                             description = {
                                     "Add a new project to the scope of this service.", ""}, subcommands = {})
        public static class Add implements Runnable {

            @CommandLine.Option(names = {"-s", "--service"}, defaultValue = "local", description = {
                    "Resource service " +
                            "to connect" +
                            " to"},
                                required = false)
            private String service;

            @CommandLine.Option(names = {"-w", "--workspace"}, defaultValue = "local", description = {
                    "Workspace for " +
                            "the " +
                            "imported" +
                            " project"
            }, required = false)
            private String workspace;

            @CommandLine.Option(names = {"-f", "--force"}, defaultValue = "false", description = {"Force reimport of an" +
                                                                                                          " existing " +
                                                                                                          "project"},
                                required = false)
            private boolean force;

            @CommandLine.Parameters
            private String projectUrl;

            @Override
            public void run() {

                /*
                TODO select the workspace if the passed value isn't null - remove the "local" default and
                 just create it if not existing.
                 */

                KlabCLI.INSTANCE.modeler().importProject(workspace, projectUrl, force);

                //                try {
                //                    var url = new File(projectUrl).isDirectory() ? new File(projectUrl)
                //                    .toURI().toURL() :
                //                              new URI(projectUrl).toURL();
                //                    var service = KlabCLI.INSTANCE.service(this.service, ResourcesService
                //                    .class);
                //                    if (service instanceof ResourcesService.Admin admin) {
                //                        if (admin.importProject(workspace, url.toString(), false).isEmpty
                //                        ()) {
                //                            System.out.println("project " + projectUrl + " was present or
                //                            in error, not " + "added");
                //                        } else {
                //                            System.out.println("project " + projectUrl + " added to
                //                            workspace " + workspace);
                //                        }
                //                    } else {
                //                        System.out.println("service " + this.service + " does not have
                //                        admin permissions " +
                //                                "in" + " this scope");
                //                    }
                //                } catch (Exception e) {
                //                    System.err.println("Invalid project URL entered");
                //                }
            }
        }

        @CommandLine.Command(name = "remove", mixinStandardHelpOptions = true, version = Version.CURRENT,
                             description =
                                     {"Remove a project from this service.", ""}, subcommands = {})
        public static class Remove implements Runnable {

            @CommandLine.Option(names = {"-s", "--service"}, defaultValue = "local", description = {
                    "Resource service " +
                            "to connect" +
                            " to"},
                                required = false)
            private String service;
            @CommandLine.Parameters
            private String project;

            @Override
            public void run() {
                var service = KlabCLI.INSTANCE.service(this.service, ResourcesService.class);
                if (service instanceof ResourcesService.Admin) {
                    ((ResourcesService.Admin) service).deleteProject(project, KlabCLI.INSTANCE.user());
                }
            }
        }
    }

    @CommandLine.Command(name = "components", mixinStandardHelpOptions = true, version = Version.CURRENT,
                         description =
                                 {"Components operations", ""}, subcommands =
                                 {CLIResourcesView.Components.Update.class,
                                  CLIResourcesView.Components.Remove.class})
    public static class Components implements Runnable {

        @Override
        public void run() {
            // TODO list components, also in verbose mode
        }


        @CommandLine.Command(name = "update", mixinStandardHelpOptions = true, version = Version.CURRENT,
                             description = {
                                     "Check for updates in a resource's original repository and optionally load it.", ""}, subcommands = {})
        public static class Update implements Runnable {

            @CommandLine.Option(names = {"-s", "--service"}, defaultValue = "local", description = {
                    "Resource service " +
                            "to connect" +
                            " to"},
                                required = false)
            private String service;

            @CommandLine.Parameters
            String componentUrl;

            @Override
            public void run() {
                // TODO Auto-generated method stub
                System.out.println("list project");
            }

        }

        @CommandLine.Command(name = "remove", mixinStandardHelpOptions = true, version = Version.CURRENT,
                             description =
                                     {"Remove a component from a service.", ""}, subcommands = {})
        public static class Remove implements Runnable {

            @CommandLine.Option(names = {"-s", "--service"}, defaultValue = "local", description = {
                    "Resource service " +
                            "to connect" +
                            " to"},
                                required = false)
            private String service;

            @CommandLine.Parameters
            String componentName;

            @Override
            public void run() {
            }

        }
    }

    @CommandLine.Command(name = "workspace", mixinStandardHelpOptions = true, version = Version.CURRENT,
                         description =
                                 {"Workspace operations", ""}, subcommands = {Resources.Workspace.List.class,
                                                                              Resources.Workspace.Remove.class})
    public static class Workspace {

        @CommandLine.Command(name = "list", mixinStandardHelpOptions = true, version = Version.CURRENT,
                             description = {
                                     "List and describe local workspaces.", ""}, subcommands = {})
        public static class List implements Runnable {

            @CommandLine.Option(names = {"-v", "--verbose"}, defaultValue = "false", description = {"List projects in " +
                                                                                                            "each " +
                                                                                                            "workspace"}, required = false)
            private boolean verbose;

            @CommandLine.Option(names = {"-s", "--service"}, defaultValue = "local", description = {
                    "Resource service " +
                            "to connect" +
                            " to"},
                                required = false)
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

        @CommandLine.Command(name = "remove", mixinStandardHelpOptions = true, version = Version.CURRENT,
                             description =
                                     {"Remove a workspace from this service.", ""}, subcommands = {})
        public static class Remove implements Runnable {

            @CommandLine.Option(names = {"-s", "--service"}, defaultValue = "local", description = {
                    "Resource service " +
                            "to connect" +
                            " to"},
                                required = false)
            private String service;
            @CommandLine.Parameters
            private String workspace;

            @Override
            public void run() {
                var service = KlabCLI.INSTANCE.service(this.service, ResourcesService.class);
                if (service instanceof ResourcesService.Admin) {
                    ((ResourcesService.Admin) service).deleteWorkspace(workspace, KlabCLI.INSTANCE.user());
                }
            }
        }

    }

    @Override
    public void showWorkspaces(java.util.List<NavigableContainer> workspaces) {

    }

    @Override
    public void showResources(NavigableContainer workspace) {

    }

    @Override
    public void workspaceModified(NavigableContainer changedContainer) {

    }

    @Override
    public void showAssetInfo(NavigableAsset asset) {

    }

    @Override
    public void highlightAssetPath(java.util.List<NavigableAsset> path) {

    }

    @Override
    public void setServiceCapabilities(ResourcesService.Capabilities capabilities) {

    }

    @Override
    public void workspaceCreated(NavigableContainer workspace) {

    }

    @Override
    public void resetValidationNotifications(NavigableContainer notifications) {

    }

    @Override
    public void engineStatusChanged(Engine.Status status) {

    }
}
