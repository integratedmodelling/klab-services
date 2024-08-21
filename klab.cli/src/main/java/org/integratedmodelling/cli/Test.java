package org.integratedmodelling.cli;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.lang.Statement;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableContainer;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;
import org.integratedmodelling.klab.modeler.panels.DocumentEditorAdvisor;
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
       KlabCLI.INSTANCE.modeler().getController().openPanel(DocumentEditorAdvisor.class, new NavigableDocument() {
           @Override
           public String getFileExtension() {
               return null;
           }

           @Override
           public List<NavigableAsset> getAssetsAt(int offset) {
               return List.of();
           }

           @Override
           public List<NavigableAsset> getClosestAsset(int offset) {
               return List.of();
           }

           @Override
           public List<Statement> getStatementPath(int offset) {
               return List.of();
           }

           @Override
           public boolean mergeMetadata(Metadata metadata, List<Notification> notifications) {
                return false;
           }

           @Override
           public List<? extends NavigableAsset> children() {
               return List.of();
           }

           @Override
           public NavigableAsset parent() {
               return null;
           }

           @Override
           public <T extends NavigableAsset> T parent(Class<T> parentClass) {
               return null;
           }

           @Override
           public NavigableContainer root() {
               return null;
           }

           @Override
           public Metadata localMetadata() {
               return null;
           }

           @Override
           public <T extends KlabAsset> T findAsset(String resourceUrn, Class<T> resultClass, KnowledgeClass... assetType) {
               return null;
           }

           @Override
           public String getUrn() {
               return null;
           }

           @Override
           public Metadata getMetadata() {
               return null;
           }
       });

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
        List<String> testcases;

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
