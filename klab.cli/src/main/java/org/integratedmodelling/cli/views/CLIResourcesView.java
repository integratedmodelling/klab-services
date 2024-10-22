package org.integratedmodelling.cli.views;

import org.integratedmodelling.cli.KlabCLI;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableContainer;
import org.integratedmodelling.klab.api.view.modeler.views.ResourcesNavigator;
import org.integratedmodelling.klab.api.view.modeler.views.controllers.ResourcesNavigatorController;
import org.integratedmodelling.klab.api.view.modeler.views.controllers.ServicesViewController;
import picocli.CommandLine;

import java.io.File;
import java.util.List;

@CommandLine.Command(name = "resources", mixinStandardHelpOptions = true, version = Version.CURRENT,
                     description = {
        "Commands to find, list, access and manipulate resources.", ""}, subcommands =
                             {CLIResourcesView.Import.class, CLIResourcesView.Rights.class})
public class CLIResourcesView extends CLIView implements ResourcesNavigator {

    private final ResourcesNavigatorController controller;

    public CLIResourcesView() {
        this.controller = KlabCLI.INSTANCE.modeler().viewController(ResourcesNavigatorController.class);
        this.controller.registerView(this);
    }

    @CommandLine.Command(name = "rights", mixinStandardHelpOptions = true, version = Version.CURRENT,
                         description = {
            "List and describe all the available resource services.", ""}, subcommands = {})
    public static class Rights implements Runnable {

        @CommandLine.Option(names = {"-s", "--service"}, defaultValue = "local" /* TODO initialize at null */,
                            description = {"Resource service to connect to"}, required = false)
        private String service;

        @CommandLine.Parameters(description = "The URN of the resource that we inquire or assign rights for")
        private String urn;

        @CommandLine.Option(names = {"-g", "--groups"}, defaultValue = CommandLine.Parameters.NULL_VALUE,
                            description = {"Comma" +
                                                                                                                                 "-separated list of groups to set", "Use a +/- sign in front to define access type (+ is default)"})
        private java.util.List<String> groups;

        @CommandLine.Option(names = {"-u", "--users"}, defaultValue = CommandLine.Parameters.NULL_VALUE,
                            description = {"Comma" +
                                                                                                                                "-separated list of users to set", "Use a +/- sign in front to define access type (+ is default)"})
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


    @CommandLine.Command(name = "import", mixinStandardHelpOptions = true, version = Version.CURRENT,
                         description = {
            "Import a resource into the current resources service from a project, an archive file or a " +
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
                        admin.createResource(sourceFile, KlabCLI.INSTANCE.user());
                    } else {
                        throw new KlabIllegalStateException("Cannot perform admin operations on this service");
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
            System.out.println("list rights");
        }
    }


    @Override
    public void showWorkspaces(List<NavigableContainer> workspaces) {

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
    public void highlightAssetPath(List<NavigableAsset> path) {

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
