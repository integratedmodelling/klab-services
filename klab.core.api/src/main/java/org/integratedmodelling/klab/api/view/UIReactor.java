package org.integratedmodelling.klab.api.view;

import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.view.annotations.UIActionHandler;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Anything that reacts to UI events implements this interface. The enums and the inherent
 * constraints validate and establish the communication within the modeler. All this is meant to
 * build the "controller" part of the modeler as much as possible within the core k.LAB
 * implementation.
 *
 * <p>Interfaces derived from this define the wiring of events and actions by means of methods
 * annotated with the @{@link org.integratedmodelling.klab.api.modeler.annotations.UIEventHandler}
 * and @{@link org.integratedmodelling.klab.api.modeler.annotations.UIActionHandler} annotations.
 * The modeler collects them and automatically wires events from the engine to UI actions that are
 * dispatched to all views and panels registered with them.
 */
public interface UIReactor {

  enum ViewCategory {
    View,
    Panel
  }

  /**
   * We categorize the different UI views and panels so that we can build a wiring pattern of events
   * through annotations on the correspondent interfaces.
   */
  enum Type {
    CLIConsole(ViewCategory.View),
    ContextView(ViewCategory.View),
    DebugView(ViewCategory.View),
    DocumentationEditor(ViewCategory.Panel),
    DocumentEditor(ViewCategory.Panel, NavigableDocument.class),
    KnowledgeNavigator(ViewCategory.View),
    KnowledgeInspector(ViewCategory.View),
    DistributionView(ViewCategory.Panel),
    KnowledgeEditor(ViewCategory.Panel),
    ResourceNavigator(ViewCategory.View),
    ProjectPropertyEditor(ViewCategory.Panel),
    ResourceEditor(ViewCategory.Panel, Resource.class),
    LocalizationEditor(ViewCategory.Panel),
    ServiceChooser(ViewCategory.View),
    AuthenticationView(ViewCategory.View),
    EventViewer(ViewCategory.View),
    ContextInspector(ViewCategory.View);

    // only defined for "panel" views
    public Class<?> target;
    public ViewCategory category;

    private Type(ViewCategory category, Class<?>... target) {
      this.category = category;
      if (target != null) {
        this.target = target.length == 1 ? target[0] : null;
      }
    }
  }

  /**
   * Defines the direction in which a UI event is sent. Each event contains the possible direction
   * of messaging for validation. Inactive events cannot be sent.
   */
  enum EventDirection {
    ViewToView,
    ViewToEngine,
    EngineToView,
    Bidirectional,
    Inactive
  }

  /**
   * Annotation for controller methods that can be called from the view. Controllers will work
   * without using it, but using it will enable building the full UI call graph that enables
   * exporting and documenting a UI.
   */
  enum UIAction {
    RefreshContents(Void.class),

    DocumentAdd(KlabAsset.KnowledgeClass.class, String.class, Map.class),
    DocumentDelete(KlabDocument.class),
    DocumentSelect(KlabDocument.class),

    /**
     * Sent by document editors upon save, triggering engine update request. No target as the view
     * must own the document for this to be sent.
     */
    DocumentUpdate(String.class),
    CreateAsset(String.class),
    DeleteAsset(NavigableAsset.class),
    ImportProject(String.class),

    ObserveAsset(KlabAsset.class),

    /**
     * Resources can be imported with a project as a target or as a service-wide resource, so a
     * project may be passed in the target. If there is one remaining parameter and it's a file,
     * ingestion of the "default" resource for the file type will be attempted. Otherwise the
     * parameters must specify enough information for the resource adapter to produce the resource.
     */
    ImportResource(List.class),

    DeleteProject(Project.class),

    ManageProject(Project.class),

    EditProjectProperties(Project.Manifest.class),

    /**
     * Sent by a view that owns a document after each change in cursor position. No document in
     * target as the view must own one in order to send this.
     */
    ReportChangeOfPositionInDocument(Integer.class),
    RequestChangeOfPositionInDocument(Integer.class),
    SelectDistribution(Distribution.class),
    FocusService(KlabService.ServiceCapabilities.class);
    //        SelectService(KlabService.ServiceCapabilities.class);

    List<Class<?>> targetClasses = new ArrayList<>();

    UIAction(Class<?>... targetClass) {
      if (targetClass != null) {
        for (var cls : targetClass) {
          this.targetClasses.add(cls);
        }
      }
    }
  }

  /**
   * The UI only reacts to these events, which contain their direction and the allowed payload
   * class(es) for validation. If the payload classes are multiple, the payload in the corresponding
   * message/method must be an array of objects of those classes, in order.
   *
   * <p>Some of these events go from the UI to the engine, others the other way around, some even
   * both ways. This should be indicated somehow, e.g. with a first parameter enum.
   */
  enum UIEvent {
    EngineStatusChanged(EventDirection.EngineToView, Engine.Status.class),

    /**
     * Sent from the UI to the engine to define which service to use as the default for the class,
     * and to the UI to the UI to tune the views on the new service in focus.
     */
    ServiceSelected(EventDirection.Bidirectional, KlabService.class),

    /** Communicate service status according to configured options and communication permissions */
    ServiceStatus(EventDirection.EngineToView, KlabService.ServiceStatus.class),

    ContextCreated(EventDirection.EngineToView, ContextScope.class, RuntimeService.class),

    /** Document add requested contains type of document, URN and a map of parameters */
    DigitalTwinModified(EventDirection.EngineToView, DigitalTwin.class),

    /**
     * First parameter is the Project (or null for "current service", the second the URN, the rest
     * is the initialization payload
     */
    AssetCreateRequest(EventDirection.ViewToEngine, String.class, String.class, Map.class),
    /**
     * Show a document after selection. The second parameter is true if the document should be read
     * only, otherwise an editor will be used.
     */
    AssetShowRequest(EventDirection.ViewToView, NavigableDocument.class, Boolean.class),
    AssetHideRequest(EventDirection.ViewToView, NavigableDocument.class),

    /** Request to update document in passed URN with passed text content */
    AssetUpdateRequest(EventDirection.ViewToView, String.class, String.class),

    AssetDeleteRequest(EventDirection.ViewToEngine, String.class),

    AssetChanged(EventDirection.EngineToView, ResourceSet.class),

    ViewShown(EventDirection.ViewToView, Type.class),

    ViewHidden(EventDirection.ViewToView, Type.class),

    AssetSelected(EventDirection.ViewToEngine, KlabDocument.class),

    AssetFocused(EventDirection.ViewToEngine, KlabDocument.class),

    DocumentPositionChanged(EventDirection.ViewToEngine, KlabDocument.class, Integer.class),
    ResourceFocused(EventDirection.ViewToEngine, Resource.class),
    ServiceFocused(EventDirection.ViewToEngine, KlabService.ServiceCapabilities.class),
    NewProjectRequest(EventDirection.ViewToEngine, String.class),
    ImportProjectRequest(EventDirection.ViewToEngine, String.class),
    Notification(EventDirection.Bidirectional, Notification.class),
    DistributionAvailable(EventDirection.EngineToView, Distribution.class),
    DistributionSelected(EventDirection.ViewToView, Distribution.class),
    //        ReasoningAvailable(EventDirection.EngineToView, Reasoner.Capabilities.class),
    UserAuthenticated(EventDirection.Bidirectional, UserIdentity.class),

    /** Resource services communicated that a workspace was modified */
    WorkspaceModified(EventDirection.EngineToView, ResourceSet.class),

    /**
     * Reasoner or other service has validated a set of syntactically valid documents and sends back
     * any notifications.
     */
    LogicalValidation(EventDirection.EngineToView, ResourceSet.class),

    /**
     * Declaring a dependency on this event means that we want all events sent to the annotated
     * method.
     */
    AnyEvent(EventDirection.Bidirectional, Void.class),

    ContextObservationResolved(
        EventDirection.ViewToView, ContextScope.class, RuntimeService.class, Observation.class),

    ObserverResolved(
        EventDirection.ViewToView, ContextScope.class, RuntimeService.class, Observation.class),

    ObservationSubmissionStarted(
        EventDirection.EngineToView, ContextScope.class, RuntimeService.class, Observation.class),
    ObservationSubmissionAborted(
        EventDirection.EngineToView, ContextScope.class, RuntimeService.class, Observation.class),
    ObservationSubmissionFinished(
        EventDirection.EngineToView, ContextScope.class, RuntimeService.class, Observation.class);

    List<Class<?>> payloadClasses = new ArrayList<>();
    EventDirection direction;

    UIEvent(EventDirection direction, Class<?>... payloadClass) {
      this.direction = direction;
      if (payloadClass != null) {
        for (var cls : payloadClass) {
          this.payloadClasses.add(cls);
        }
      }
    }
  }

  /**
   * Views may call refresh() to ensure that the controller sends them whatever updated content they
   * may be handling. This may be linked to the view coming into focus after potential changes.
   */
  @UIActionHandler(UIAction.RefreshContents)
  default void refresh() {}

  /**
   * Each reactor should deallocate resources or release locks at shutdown. This will be called by
   * {@link UIController#shutdown()} on every open view and panel. Note that this is not called on
   * {@link PanelController#close()}
   */
  default void shutdown() {}

  UIController getController();
}
