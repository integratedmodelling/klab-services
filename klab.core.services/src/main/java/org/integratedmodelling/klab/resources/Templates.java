package org.integratedmodelling.klab.resources;

import java.io.File;
import java.io.IOException;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.utils.Utils;

public interface Templates {

  String emptyJSONTemplate = "{\n}";
  String propertiesTemplate = "klab.version = " + Version.CURRENT + "\n";

  String ontologyTemplate =
      "ontology __NAMESPACE__\n" + "      version 1.0\n" + "      in domain im:Domain;\n\n";
  String namespaceTemplate = "namespace __NAMESPACE__\n" + "   version 1.0;\n\n";
  String behaviorTemplate =
      "behavior __NAMESPACE__\n" + "   \"Description\" \n" + "\tversion 1.0\n\n";
  String applicationTemplate =
      "app __NAMESPACE__\n" + "   \"Description\" \n" + "\tversion 1.0\n\n";
  String testcaseTemplate =
      "testcase __NAMESPACE__\n" + "   \"Description\" \n" + "\tversion 1.0\n\n";
  String scriptTemplate = "script __NAMESPACE__\n" + "   \"Description\" \n" + "\tversion 1.0\n\n";
  String observationStrategiesTemplate = "strategies __NAMESPACE__\n" + "   version 1.0;\n\n";

  static void createDocument(ProjectStorage.ResourceType resourceType, String resourceId, File file)
      throws IOException {

    String contents =
        switch (resourceType) {
          case ONTOLOGY -> ontologyTemplate.replace("__NAMESPACE__", resourceId);
          case MODEL_NAMESPACE -> namespaceTemplate.replace("__NAMESPACE__", resourceId);
          case STRATEGY -> observationStrategiesTemplate.replace("__NAMESPACE__", resourceId);
          case BEHAVIOR -> behaviorTemplate.replace("__NAMESPACE__", resourceId);
          case APPLICATION -> applicationTemplate.replace("__NAMESPACE__", resourceId);
          case SCRIPT -> scriptTemplate.replace("__NAMESPACE__", resourceId);
          case TESTCASE -> testcaseTemplate.replace("__NAMESPACE__", resourceId);
          default ->
              throw new KlabIllegalArgumentException(
                  "Unsupported resource type in template engine: " + resourceType);
        };

    Utils.Files.writeStringToFile(contents, file);
  }
}
