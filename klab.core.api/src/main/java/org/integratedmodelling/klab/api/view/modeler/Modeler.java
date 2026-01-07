package org.integratedmodelling.klab.api.view.modeler;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.klab.api.data.RepositoryState;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.view.UIController;
// import org.integratedmodelling.klab.api.view.annotations.UIActionHandler;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;

/**
 * A {@link UIController} that contains all the user actions relevant to a modeler IDE. Implement
 * this to use it in any view type. Can also provide a blueprint to implement a non-Java modeler
 * using websockets to drive a Java counterpart controller.
 *
 * <p>The modeler must wrap all k.LAB assets coming from the services into {@link
 * org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset} to enable navigation and
 * selection.
 *
 * <p>The modeler is itself a view that should receive UI events, particularly those related to
 * showing and hiding views.
 */
public interface Modeler extends UIController {

  /**
   * Authentication is necessary for the engine to operate. This can be called before {@link
   * #boot()} if needed by an application's logic. If not called, the engine will be authenticated
   * automatically on boot.
   *
   * @return
   */
  UserScope authenticate();

  /**
   * If the associated engine has a k.LAB distribution available, return it.
   *
   * @return the available distribution or null
   */
  Distribution getDistribution();

  /**
   * User action that obtains or find an asset from a URN and tells the modeler to observe it,
   * whatever that means. The asset should be classified and only valid contexts should be handled.
   * The UI may alert for illegitimate use of this action.
   *
   * @param asset a k.LAB asset of any type. May create a context, add to an existing one, or cause
   *     nothing or an error.
   * @param adding the user has activate a UI mode that forces an "add to existing" mode, such as
   *     keeping the Ctrl key pressed. May be handled or ignored according to context.
   */
  CompletableFuture<Observation> observe(ContextScope contextScope, Object asset, boolean adding);

  /**
   * Find an appropriate visualization method for the passed asset and media type. Return the result
   * of visualizing or null if a method cannot be found or has failed.
   *
   * @param asset
   * @param mediaType
   * @param visualizationOptions
   * @return
   */
  <T> T visualize(
      KlabAsset asset,
      Scheduler.Event event,
      String mediaType,
      ContextScope contextScope,
      Map<String, Object> visualizationOptions,
      Class<T> outputType);

  /**
   * Return all the open contexts for the current session.
   *
   * @deprecated this should be an IDE issue, not a Modeler issue
   * @return
   */
  List<ContextScope> getOpenContexts();

  /**
   * Return a new scope with any engine defaults set by the implementation and its configuration.
   *
   * @return
   */
  ContextScope createDefaultContext();

  /**
   * Transparently create (if needed) a local web server and publish the passed file so that it can
   * be accessed in a browser using the returned URL. The workspace argument may be null; if passed,
   * it guarantees that all assets published within the same workspace are accessible at the same
   * URL prefix.
   *
   * @param inputFile
   * @param workspace
   * @param additionalFiles any other file that needs to be locally available at the same web
   *     address
   * @return the URL serving inputFile
   */
  URL publishLocally(File inputFile, String workspace, File... additionalFiles);

  boolean shutdown(boolean shutdownLocalServices);

  /**
   * Call the correspondent API on the service and ingest all modifications, propagating UI events
   * as needed.
   *
   * @param service
   * @param workspaceName
   * @param projectUrl
   * @param overwriteExisting
   */
  void importProject(
      ResourcesService service, String workspaceName, String projectUrl, boolean overwriteExisting);

  /**
   * Call the correspondent API on the service and ingest all modifications, propagating UI events
   * as needed.
   *
   * @param service
   * @param projectUrl
   */
  void deleteProject(ResourcesService service, String projectUrl);

    boolean updateDocument(
            ResourcesService service,
            String projectName,
            String documentUrn,
            ProjectStorage.ResourceType documentType,
            String updatedContent);

    /**
   * Call the correspondent API on the service and ingest all modifications, propagating UI events
   * as needed.
   *
   * @param service
   * @param asset
   */
  void deleteAsset(ResourcesService service, NavigableAsset asset);

  /**
   * Call the correspondent API on the service and ingest all modifications, propagating UI events
   * as needed.
   *
   * @param service
   * @param projectId
   * @param operation
   * @param arguments
   */
  void manageProject(
      ResourcesService service,
      String projectId,
      RepositoryState.Operation operation,
      String... arguments);

  /**
   * Call the correspondent API on the service and ingest all modifications, propagating UI events
   * as needed.
   *
   * @param service
   * @param projectId
   */
  void editProperties(ResourcesService service, String projectId);

  /**
   * Call the correspondent API on the service and ingest all modifications, propagating UI events
   * as needed.
   *
   * @param service
   * @param projectName
   * @param workspaceName
   * @return
   */
  boolean createProject(ResourcesService service, String projectName, String workspaceName);

  /**
   * Call the correspondent API on the service and ingest all modifications, propagating UI events
   * as needed.
   *
   * @param service
   * @param projectName
   * @param documentUrn
   * @param documentType
   * @return
   */
  boolean createDocument(
      ResourcesService service,
      String projectName,
      String documentUrn,
      ProjectStorage.ResourceType documentType);
}
