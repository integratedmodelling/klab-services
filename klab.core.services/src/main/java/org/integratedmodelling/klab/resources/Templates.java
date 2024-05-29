package org.integratedmodelling.klab.resources;

import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;

import java.io.File;
import java.io.IOException;

public interface Templates {

    String projectTemplate = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" + "<projectDescription>\r\n"
            + " <name>__PROJECT__</name>\r\n" + "   <comment></comment>\r\n" + "    <projects>\r\n" + " " +
            "</projects>\r\n"
            + " <buildSpec>\r\n" + "        <buildCommand>\r\n"
            + "         <name>org.eclipse.xtext.ui.shared.xtextBuilder</name>\r\n" + "          " +
            "<arguments>\r\n"
            + "         </arguments>\r\n" + "       </buildCommand>\r\n" + "        <buildCommand>\r\n"
            + "         <name>org.integratedmodelling.klab.ide.klabBuilder</name>\r\n" + "          " +
            "<arguments>\r\n"
            + "         </arguments>\r\n" + "       </buildCommand>\r\n" + "    </buildSpec>\r\n" + "   " +
            "<natures>\r\n"
            + "     <nature>org.eclipse.xtext.ui.shared.xtextNature</nature>\r\n" + "   </natures>\r\n"
            + "</projectDescription>";

    String emptyJSONTemplate = "{\n}";
    String propertiesTemplate = "klab.version = " + Version.CURRENT + "\n";
    String knowledgeTemplate = "namespace __PROJECT__;\n\n";

    String testCaseTemplate = "worldview __WORLDVIEW__;\r\n" +
            "\r\n" +
            "@test(\r\n" +
            "    name = \"__TEST_NAME__\",\r\n" +
            "    description  = \"\",\r\n" +
            "    // add to the list any concepts to be observed in the context below\r\n" +
            "    observations = (\"\"),\r\n" +
            "    // add assertions to check the observations after execution\r\n" +
            "    assertions = ()\r\n" +
            ")\r\n" +
            "// change the concept to the one you want; use a URN optionally (in a separate test project " +
            "including the resource)\r\n" +
            "observe /* a:urn:to:use#objectid as */ earth:Region named __NAME__\r\n" +
            "    // add any space and/or time extents: for example\r\n" +
            "    // over space(shape=\"EPSG:4326 POLYGON((33.796 -7.086, 35.946 -7.086, 35.946 -9.41, 33" +
            ".796 -9.41, 33.796 -7.086))\", grid=\"1 km\")\r\n" +
            ";\r\n\n" +
            "// add any models here\n";

    String kimScriptTemplate = "worldview __WORLDVIEW__;\r\n" +
            "\r\n" +
            "@run(\r\n" +
            "    name = \"__SCRIPT_NAME__\",\r\n" +
            "    description  = \"\",\r\n" +
            "    // add to the list any concepts to be observed in the context below\r\n" +
            "    observations = (\"\")\r\n" +
            ")\r\n" +
            "// change the concept to the one you want; add a URN optionally\r\n" +
            "observe /* a:urn:to:use#objectid as */ __WORLDVIEW__:Thing named __NAME__\r\n" +
            "    // add any space and/or time extents: for example\r\n" +
            "    // over space(shape=\"EPSG:4326 POLYGON((33.796 -7.086, 35.946 -7.086, 35.946 -9.41, 33" +
            ".796 -9.41, 33.796 -7.086))\", grid=\"1 km\")\r\n" +
            ";\r\n" +
            "\r\n" +
            "// add any models here\r\n" +
            "";

    String ontologyTemplate = "namespace __NAMESPACE__\n" +
            "      version 1.0\n" +
            "      in domain im:Domain;\n\n";

    String namespaceTemplate = "";

    String behaviorTemplate = "";

    String applicationTemplate = "";

    String observationStrategiesTemplate = "";

    static void createDocument(ProjectStorage.ResourceType resourceType, String resourceId, File file) throws IOException {

        String contents = null;

        switch (resourceType) {
            case ONTOLOGY -> {
            }
            case MODEL_NAMESPACE -> {
            }
            case MANIFEST -> {
            }
            case DOCUMENTATION_NAMESPACE -> {
            }
            case STRATEGY -> {
            }
            case BEHAVIOR -> {
            }
            case APPLICATION -> {
            }
            case SCRIPT -> {
            }
            case TESTCASE -> {
            }
            case BEHAVIOR_COMPONENT -> {
            }
            case RESOURCE -> {
            }
            case RESOURCE_ASSET -> {
            }
        }

    }
}
