package org.integratedmodelling.common.view;

import org.apache.commons.compress.utils.Lists;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.authentication.ExternalAuthenticationCredentials;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.view.*;
import org.integratedmodelling.klab.api.view.annotations.UIActionHandler;
import org.integratedmodelling.klab.api.view.annotations.UIEventHandler;
import org.integratedmodelling.klab.api.view.annotations.UIPanelController;
import org.integratedmodelling.klab.api.view.annotations.UIViewController;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableContainer;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;
import org.integratedmodelling.klab.api.view.modeler.visualization.Visualization;
import org.integratedmodelling.klab.common.data.ExportFileCache;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.springframework.core.annotation.AnnotationUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Base abstract {@link UIController} class that implements annotation-driven (de)registration of
 * {@link UIReactor}s and event dispatching.
 */
public abstract class AbstractUIController implements UIController {

  private final UIView uiView;

  /**
   * All events that the UI reacts to. Used to filter the engine events so they are not dispatched
   * unless something is listening.
   */
  private Set<UIReactor.UIEvent> relevantEvents = EnumSet.noneOf(UIReactor.UIEvent.class);

  /** Reactors to each event are registered here */
  private final Map<UIReactor.UIEvent, List<EventReactor>> reactors = new ConcurrentHashMap<>();

  private final List<Pair<UIPanelController, Class<? extends PanelController<?, ?>>>>
      panelControllerClasses = Collections.synchronizedList(new ArrayList<>());
  private final Map<UIReactor.Type, Object> views = new HashMap<>();
  private final Map<Object, PanelController<?, ?>> panelControllers = new HashMap<>();
  private Engine engine;
  private final List<VisualizationDescriptor> visualizationDescriptors =
      Collections.synchronizedList(new ArrayList<>());

  private record VisualizationDescriptor(
      Visualization visualization, Object reactor, Method method) {
    public boolean appliesTo(Object asset) {
      // TODO check geometry and semantics from the descriptor as applicable
      return true;
    }
  }

  private class EventReactor {

    Method method;
    Object reactor;
    Queue<Pair<UIReactor, Object[]>> messageQueue = new LinkedBlockingDeque<>();

    static class EventReactionEdge extends DefaultEdge {
      UIReactor.UIEvent event;
    }

    /**
     * The interaction graph detailing who sends and receives what and from/to whom. Created after
     * the views before boot, gets updated when a panel is created or removed.
     */
    Graph<UIReactor, EventReactionEdge> interactionGraph =
        new DefaultDirectedGraph<>(EventReactionEdge.class);

    public EventReactor(Object reactor, Method method) {
      this.reactor = reactor;
      this.method = method;
      //
      // this.parameterClasses.addAll(Arrays.stream(method.getParameterTypes()).toList());
    }

    public void dispatchPendingTasks() {
      while (!messageQueue.isEmpty()) {
        var message = messageQueue.remove();
        callMessage(message.getFirst(), message.getSecond());
      }
    }

    public Object call(UIReactor sender, Object... payload) {

      /*
         this would be fun
      */
      if (sender == reactor || (sender == AbstractUIController.this && reactor == uiView)) {
        return null;
      }

      if (reactor instanceof AbstractUIViewController<?> viewController) {
        if (viewController.view() == null) {
          // put away the messages in the synchronous queue
          // FIXME it takes another message (with a non-null view) to empty a queue; messages
          //  pre-view creation get lost if there are no messages after
          messageQueue.add(Pair.of(sender, payload));
          return null;
        } else {
          while (!messageQueue.isEmpty()) {
            var message = messageQueue.remove();
            callMessage(message.getFirst(), message.getSecond());
          }
        }
      }

      return callMessage(sender, payload);
    }

    private Object callMessage(UIReactor sender, Object... payload) {
      var args = Utils.Collections.matchArguments(method.getParameterTypes(), payload);
      if (args == null && payload != null) {
        return null;
      }
      try {
        return method.invoke(reactor, args);
      } catch (Exception e) {
        scope().error(e);
      }
      return null;
    }
  }

  protected AbstractUIController() {
    this(null);
  }

  protected AbstractUIController(UIView mainApplication) {
    this.uiView = mainApplication;
    if (mainApplication != null) {
      registerViewController(mainApplication);
    }
    createView();
    createViewGraph();
  }

  /**
   * Create the engine. Do not boot it! It will be booted when {@link #boot()} is called.
   *
   * @return
   */
  public abstract Engine createEngine();

  @Override
  public Engine engine() {
    return engine;
  }

  public UserScope authenticate() {

    if (engine == null) {
      engine = createEngine();
    }

    var ret = engine.authenticate();

    ret.onMessage(this::processEvent, Message.Queue.Events);
    ret.onMessage(this::processInteraction, Message.Queue.UI);
    ret.onMessage(
        this::processNotification,
        Message.Queue.Info,
        Message.Queue.Errors,
        Message.Queue.Warnings);

    return ret;
  }

  /**
   * Boot the engine asynchronously after installing the needed listeners. Must be called by
   * implementors after creation.
   *
   * <p>TODO this should return a future for the booted engine status
   */
  public void boot() {
    authenticate();
    engine.boot();
  }

  /**
   * After all views were registered, process views and their links so that events can be properly
   * routed.
   */
  private void createViewGraph() {
    // TODO build the event routing strategy based on the annotations
    for (var view : views.values()) {}
  }

  /**
   * All the registration of views should happen here. Panels are created on demand but views must
   * pre-exist before boot, potentially in a hidden state.
   */
  protected abstract void createView();

  protected void processNotification(Channel scope, Message message) {}

  protected void processInteraction(Channel scope, Message message) {}

  /**
   * Translate k.LAB events into relevant UI events and dispatch them, routing through the view
   * graph. If overridden, most implementation should make sure that super is called.
   *
   * @param scope
   * @param message
   */
  protected void processEvent(Channel scope, Message message) {

    switch (message.getMessageClass()) {
      case Void -> {}
      case UserContextChange -> {}
      case UserContextDefinition -> {}
      case ServiceLifecycle -> {}
      case KimLifecycle -> {}
      case ResourceLifecycle -> {
        if (message.is(Message.MessageType.WorkspaceChanged)) {
          dispatch(this, UIEvent.WorkspaceModified, message.getPayload(ResourceSet.class));
        }
      }
      case ProjectLifecycle -> {}
      case TaskLifecycle -> {}
      case DigitalTwin -> {}
      case SessionLifecycle -> {}
      case UnitTests -> {}
      case Notification -> {}
      case Search -> {}
      case Query -> {}
      case Run -> {}
      case ViewActor -> {}
      case ActorCommunication -> {}
      case KnowledgeLifecycle -> {}
    }
  }

  @Override
  public void dispatch(UIReactor sender, UIReactor.UIEvent event, Object... payload) {
    // TODO handle reactors to UiEvent.Any
    if (relevantEvents.contains(event)) {
      var rs = reactors.get(event);
      if (rs != null) {
        for (var desc : rs) {
          if (event != UIEvent.ServiceStatus) {
            //                        System.out.println("Dispatching " + event + "(" +
            // Arrays.toString(payload) + ") to "
            //                                + desc.method);
          }
          desc.call(sender, payload);
        }
      }
    }
  }

  /**
   * Define the scope to use to report engine issues.
   *
   * @return
   */
  protected abstract Scope scope();

  @Override
  public void registerPanelControllerClass(Class<? extends PanelController<?, ?>> cls) {
    var panelAnnotation = AnnotationUtils.findAnnotation(cls, UIPanelController.class);
    if (panelAnnotation == null) {
      throw new KlabInternalErrorException(
          "Panel class " + cls.getCanonicalName() + " " + "is not " + "annotated with UIPanel");
    }
    panelControllerClasses.add(Pair.of(panelAnnotation, cls));
  }

  public void closePanel(PanelController<?, ?> reactor) {
    var payload = reactor.getPayload();
    var controller = panelControllers.get(payload);
    if (controller != null) {
      if (controller.panel().close()) {
        panelControllers.remove(payload);
        for (var reactorList : reactors.values()) {
          reactorList.removeIf(er -> er.reactor == reactor);
        }
      }
    }
  }

  // These must be in the reactor list but must be found easily for when the panel closes. All
  // checks are
  // made in advance.
  private void registerPanelController(PanelController<?, ?> reactor) {

    var viewAnnotation =
        AnnotationUtils.findAnnotation(reactor.getClass(), UIPanelController.class);
    if (viewAnnotation == null) {
      throw new KlabInternalErrorException("null panel annotation at registration");
    }

    //        panels.put(viewAnnotation.value(), reactor);

    for (var method : reactor.getClass().getDeclaredMethods()) {
      var eventHandlerDefinition = AnnotationUtils.findAnnotation(method, UIEventHandler.class);
      if (eventHandlerDefinition != null) {
        var key = eventHandlerDefinition.value();
        var descriptor = new EventReactor(reactor, method);
        descriptor.method = method;
        descriptor.reactor = reactor;
        relevantEvents.add(eventHandlerDefinition.value());
        // TODO validate the argument list w.r.t. the event payload class!
        this.reactors.computeIfAbsent(key, k -> new ArrayList<>()).add(descriptor);
      }
      var actionHandlerDefinition = AnnotationUtils.findAnnotation(method, UIActionHandler.class);
      if (actionHandlerDefinition != null) {

        // TODO update action graph

      }
    }
  }

  @Override
  public void registerViewController(Object reactor) {

    var viewAnnotation = AnnotationUtils.findAnnotation(reactor.getClass(), UIViewController.class);
    if (viewAnnotation != null) {
      /*
      We can only have one of each declared views.
       */
      if (views.containsKey(viewAnnotation.value())) {
        throw new KlabInternalErrorException(
            "View class "
                + reactor.getClass().getCanonicalName()
                + " "
                + " adds duplicated view type "
                + viewAnnotation.value());
      }
      views.put(viewAnnotation.value(), reactor);
    }

    for (var method : reactor.getClass().getDeclaredMethods()) {
      var eventHandlerDefinition = AnnotationUtils.findAnnotation(method, UIEventHandler.class);
      if (eventHandlerDefinition != null) {
        var key = eventHandlerDefinition.value();
        var descriptor = new EventReactor(reactor, method);
        descriptor.method = method;
        descriptor.reactor = reactor;
        relevantEvents.add(eventHandlerDefinition.value());
        // TODO validate the argument list w.r.t. the event payload class!
        this.reactors.computeIfAbsent(key, k -> new ArrayList<>()).add(descriptor);

        // TODO update action graph

      }
      var actionHandlerDefinition = AnnotationUtils.findAnnotation(method, UIActionHandler.class);
      if (actionHandlerDefinition != null) {

        // TODO update action graph

      }
      var visualizationHandlerDefinition =
          AnnotationUtils.findAnnotation(method, Visualization.class);
      if (visualizationHandlerDefinition != null) {
        visualizationDescriptors.add(
            new VisualizationDescriptor(visualizationHandlerDefinition, reactor, method));
      }
    }
  }

  @Override
  public <T extends ViewController<?>> T viewController(Class<T> controllerClass) {
    for (var view : views.values()) {
      if (controllerClass.isAssignableFrom(view.getClass())) {
        return (T) view;
      }
    }
    return null;
  }

  @Override
  public void unregister(UIReactor reactor) {
    for (var key : reactors.keySet()) {
      reactors.get(key).remove(reactor);
    }
  }

  @Override
  public <P, T extends PanelView<P>> T openPanel(Class<T> panelType, P payload) {

    var existing = panelControllers.get(payload);
    if (existing != null) {
      existing.bringForward();
      return (T) existing.panel();
    }

    // create and register the panel controller, which must unregister itself when the panel is
    // closed.
    // This must be hooked into a view-side controller somehow, as we cannot create
    // the panel view itself.
    Class<PanelController<P, PanelView<P>>> controllerClass = null;
    PanelView<P> ret = null;
    PanelController<P, PanelView<P>> controller = null;

    for (var desc : panelControllerClasses) {

      if (desc.getFirst().panelType().isAssignableFrom(panelType)) {

        controllerClass = (Class<PanelController<P, PanelView<P>>>) desc.getSecond();

        /*
        try creating the controller first. Likely choice of arguments is just (UIController)
        because we will load() the payload later, and the panel view will register itself.

        TODO make the arg matcher smarter for injection of optional parameters, e.g. scopes and
         services, to pass as varargs
         */

        var args = new Object[] {this};
        for (var constructor : controllerClass.getDeclaredConstructors()) {
          var argList = Utils.Collections.matchArguments(constructor.getParameterTypes(), args);
          if (argList != null) {
            // use this constructor
            try {
              controller = (PanelController<P, PanelView<P>>) constructor.newInstance(argList);
              break;
            } catch (Throwable t) {
              // just continue
              t.printStackTrace();
            }
          }
        }

        if (controller != null) {

          // try creating the view with just the controller as arguments (see below). If no luck,
          // keep going.
          var viewArgs = new Object[] {controller};
          for (var constructor : panelType.getDeclaredConstructors()) {
            // TODO same as above
            var argList =
                Utils.Collections.matchArguments(constructor.getParameterTypes(), viewArgs);
            if (argList != null) {
              // use this constructor
              try {
                ret = (PanelView<P>) constructor.newInstance(argList);
                controller.setPanel((PanelView<P>) ret);
                break;
              } catch (Throwable t) {
                // just continue
              }
            }
            if (ret != null) {
              break;
            }
          }

          if (ret != null) {
            registerPanelController(controller);
            controller.load(payload);
            break;
          }
        }
      }
    }

    if (ret != null) {
      ret.load(payload);
      this.panelControllers.put(payload, controller);
    }

    return (T) ret;
  }

  @Override
  public <T extends PanelController<?, ?>> Collection<T> getOpenPanels(
      Class<T> panelControllerClass) {
    List<T> ret = new ArrayList<>();
    for (var pc : panelControllers.values()) {
      if (panelControllerClass.isAssignableFrom(pc.getClass())) {
        ret.add((T) pc);
      }
    }
    return ret;
  }

  /**
   * Convenience method used by inheritors
   *
   * @param serviceId
   * @param serviceClass
   * @param <S>
   * @return
   */
  public <S extends KlabService> S serviceById(String serviceId, Class<S> serviceClass) {
    for (var service : engine.getOwner().getServices(serviceClass)) {
      if (serviceId.equals(service.serviceId())) {
        return service;
      }
    }
    return null;
  }

  @Override
  public <P, T extends PanelController<P, ?>> T getPanelController(
      P payload, Class<T> panelControllerClass) {
    return (T) panelControllers.get(payload);
  }

  public <T extends View> void dispatchPendingTasks(AbstractUIViewController<T> viewController) {

    for (var reactorList : reactors.values()) {
      for (var reactor : reactorList) {
        if (reactor.reactor == viewController && !reactor.messageQueue.isEmpty()) {
          reactor.dispatchPendingTasks();
        }
      }
    }
  }

  @Override
  public void switchWorkbenchService(
      UIReactor requestingReactor, KlabService.ServiceCapabilities service) {}

  @Override
  public void switchWorkbench(UIReactor requestingReactor, NavigableContainer container) {}

  @Override
  public void configureWorkbench(
      UIReactor requestingReactor, NavigableDocument document, boolean shown) {}

  @Override
  public UIView getUI() {
    return uiView;
  }

  /**
   * Empty implementation of storeView. Override to implement/
   *
   * @param changedElements
   */
  @Override
  public void storeView(Object... changedElements) {
    scope().debug("Storing view");
  }

  @Override
  public void shutdown() {
    for (var view : views.values()) {
      if (view instanceof UIReactor reactor) {
        reactor.shutdown();
      }
    }
    for (var panel : getOpenPanels(PanelController.class)) {
      panel.shutdown();
    }
  }

  @Override
  public List<ExternalAuthenticationCredentials.CredentialInfo> getCredentials(
      KlabService.Type serviceType, String serviceId) {

    //    if (serviceType == KlabService.Type.ENGINE
    //        && (serviceId == null
    //            || (engine.serviceId() != null && engine.serviceId().equals(serviceId)))) {
    //      // TODO local credentials, no scope check
    //      return List.of();
    //    }
    var service = serviceById(serviceId, serviceType.classify());
    if (service != null) {
      return service.getCredentialInfo(user());
    }
    return List.of();
  }

  @Override
  public ExternalAuthenticationCredentials.CredentialInfo setCredentials(
      String host,
      ExternalAuthenticationCredentials credentials,
      KlabService.Type serviceType,
      String serviceId) {
    if (serviceType == KlabService.Type.ENGINE
    /*&& (serviceId == null
    || (engine.serviceId() != null && engine.serviceId().equals(serviceId)))*/ ) {
      // TODO add to local credentials, no scope check
      return null;
    }
    var service = serviceById(serviceId, serviceType.classify());
    if (service != null) {
      return service.addCredentials(host, credentials, engine().getOwner());
    }
    return null;
  }

  public InputStream visualize(
      KlabAsset asset,
      Scheduler.Event event,
      String mediaType,
      ContextScope contextScope,
      Map<String, Object> visualizationOptions) {

    var service = getServiceHosting(asset);
    if (service == null) {
      return null;
    }

    for (var visualization :
        visualizationDescriptors.stream()
            .filter(v -> v.visualization().provides().equals(mediaType))
            .toList()) {
      if (visualization.appliesTo(asset)) {
        // retrieve a cached file if possible, otherwise export the asset and cache it.
        File cachedFile = null;
        if (visualization.visualization.requires() != null) {

          var extension = "dat";
          try {
            // remove any application stuff so that it's more likely that we understand the
            // extension. This is very painful and not very robust.
            var mimeType = visualization.visualization.requires();
            if (mimeType.contains(";")) {
              mimeType = mimeType.substring(0, mimeType.indexOf(";"));
            }
            var type = MimeTypes.getDefaultMimeTypes().forName(mimeType);
            extension = type.getExtension();
            if (extension == null || extension.isEmpty()) {
              extension = "dat";
            }
          } catch (MimeTypeException e) {
            // just leave "dat" in
          }
          cachedFile =
              ExportFileCache.temporary()
                  .withExtension(extension)
                  .get(
                      asset.getUrn(),
                      event,
                      visualization.visualization.requires(),
                      () ->
                          service.exportAsset(
                              asset.getUrn(),
                              KlabAsset.classify(asset),
                              visualization.visualization.requires(),
                              contextScope));
        }

        if (cachedFile != null && visualization.method() != null) {
          try {
            var ret =
                Utils.Java.runWithMatchedParameters(
                    visualization.method(),
                    visualization.reactor(),
                    contextScope,
                    Arrays.asList(
                        contextScope,
                        asset,
                        event,
                        visualizationOptions,
                        cachedFile,
                        cachedFile.toURI().toURL()));
          } catch (Throwable t) {
            contextScope.error(t);
          }
        }
      }
    }

    return null;
  }

  public KlabService getServiceHosting(Object asset) {
    return switch (asset) {
      case Observation observation ->
          observation.getContextualizationData() == null
              ? null
              : scope()
                  .getService(
                      RuntimeService.class,
                      s ->
                          s.serviceId()
                              .equals(observation.getContextualizationData().getServiceId()));
      // TODO everything else
      default -> null;
    };
  }
}
