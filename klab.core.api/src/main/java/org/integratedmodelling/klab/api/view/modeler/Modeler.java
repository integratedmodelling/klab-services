package org.integratedmodelling.klab.api.view.modeler;

import org.integratedmodelling.klab.api.data.RepositoryState;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.view.UIController;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.annotations.UIActionHandler;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
  @UIActionHandler(
      value = UIAction.ObserveAsset,
      label = "Observe asset",
      tooltip = "Select a k.LAB " + "asset to create a new context or add to the current one.")
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

  // FIXME not sure about these below, or the entire UIAction business

  @UIActionHandler(
      value = UIReactor.UIAction.ImportProject,
      label = "New project",
      tooltip = "Create a new k.LAB project in the current workspace and scope")
  void importProject(String workspaceName, String projectUrl, boolean overwriteExisting);

  @UIActionHandler(UIReactor.UIAction.DeleteProject)
  void deleteProject(String projectUrl);

  @UIActionHandler(UIReactor.UIAction.DeleteAsset)
  void deleteAsset(NavigableAsset asset);

  @UIActionHandler(UIReactor.UIAction.ManageProject)
  void manageProject(String projectId, RepositoryState.Operation operation, String... arguments);

  @UIActionHandler(UIReactor.UIAction.EditProjectProperties)
  void editProperties(String projectId);

  @UIActionHandler(UIReactor.UIAction.CreateAsset)
  void createDocument(
      String newDocumentUrn, String projectName, ProjectStorage.ResourceType documentType);
}
