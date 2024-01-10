package org.integratedmodelling.klab.runtime.kactors;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.kactors.KActorsAction;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.KActorsValue;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.ConcurrentGroup;
import org.integratedmodelling.klab.api.lang.kactors.beans.Layout;
import org.integratedmodelling.klab.api.lang.kactors.beans.ViewComponent;
import org.integratedmodelling.klab.api.lang.kactors.beans.ViewPanel;
import org.integratedmodelling.klab.runtime.kactors.view.KActorsView;
import org.integratedmodelling.klab.utilities.Utils;

/**
 * Scope used during view extraction as the actor executes view-bound actions. One of these is part
 * of the KlabActor's scope used during k.Actors interpretation.
 * 
 * @author Ferd
 *
 */
public class ViewScope {

    /**
     * The possible destinations of a view panel in a layout. The lowercased name of these
     * corresponds to the annotations that attribute view roles to actions in applications.
     * 
     * @author Ferd
     */
    public enum PanelLocation {
        Left, Right, Panel, Header, Footer, Window, Modal
    }

    String identityId;
    Identity identity;
//    String applicationId;
    String actorPath = null;
    private Layout layout = null;
    Optional<Boolean> notEmpty;
    private ViewComponent currentComponent;

    private Integer groupCounter = 0;

    public ViewScope(KActorsScope actorScope) {
//        this.applicationId = actorScope.getAppId();
        this.identity = actorScope.getIdentity();
        this.identityId = actorScope.getIdentity().getId();
    }

    public ViewScope getChild(ConcurrentGroup group, KActorsScope scope) {

        if (this.currentComponent == null) {
            // not an app with a view
            return null;
        }
        ViewComponent parent = this.currentComponent;
        ViewComponent ret = new ViewComponent();
        ret.setIdentity(identityId);
//        ret.setApplicationId(applicationId);
        ret.setActorPath(actorPath);
        boolean isActive = group.getGroupMetadata().containsKey("inputgroup");
        ret.setType(isActive ? ViewComponent.Type.InputGroup : ViewComponent.Type.Group);
        if (group.getGroupMetadata().containsKey("name")) {
            String name = KActorsVM
                    .evaluateInScope((KActorsValue) group.getGroupMetadata().get("name"), scope).toString();
            ret.setName(name);
        }
        String id = null;
        if (group.getGroupMetadata().containsKey("id")) {
            id = group.getGroupMetadata().get("id").getStatedValue().toString();
        } else {
            id = "g" + (groupCounter++);
        }

        setViewMetadata(ret, Parameters.create(group.getGroupMetadata()), scope);
        ret.setId(parent.getId() + "/" + id);
        parent.getComponents().add(ret);

        ViewScope child = new ViewScope(this);
        child.currentComponent = ret;

        return child;
    }

    // @Override
    public void setViewMetadata(ViewComponent component, Parameters<String> parameters, KActorsScope scope) {
        if (parameters != null) {
            for (String key : parameters.keySet()) {
                if (!component.getAttributes().containsKey(key) && KActorsView.layoutMetadata.contains(key)) {
                    Object param = parameters.get(key);
                    String value = scope.localize(param instanceof KActorsValue
                            ? KActorsVM.evaluateInScope((KActorsValue) param, scope).toString()
                            : param.toString());
                    component.getAttributes().put(key, value);
                }
            }
        }
    }

    public ViewScope(Identity identity, Layout layout, String actorPath) {
        this.identity = identity;
        this.layout = layout;
        this.identityId = identity == null ? null : identity.getId();
//        this.applicationId = applicationId;
        this.actorPath = actorPath;
        this.notEmpty = Optional.of(Boolean.FALSE);
    }

    public ViewScope(ViewScope scope) {
        this.layout = scope.layout;
//        this.applicationId = scope.applicationId;
        this.identityId = scope.identityId;
        this.groupCounter = scope.groupCounter;
        this.actorPath = scope.actorPath;
        this.notEmpty = scope.notEmpty;
    }

    /**
     * Pass an action; if the action has a view associated, create the correspondent panel (and, if
     * needed, the layout) in the view scope passed with the actor scope. The returned panel becomes
     * the current view component for the calls in the action to populate.
     * 
     * @param action
     * @param identity2
     * @param appId
     * @param parentDataflow
     * @return
     */
    public ViewPanel createPanel(KActorsAction action, KActorsBehavior behavior, KActorsScope scope) {

        ViewPanel panel = null;
        boolean hasView = behavior.getType() == KActorsBehavior.Type.COMPONENT && "main".equals(action.getUrn());
        if (!hasView) {
            // scan annotations
            for (Annotation annotation : action.getAnnotations()) {

                PanelLocation panelLocation = Utils.Data.valueOf(Utils.Strings.capitalize(annotation.getName()), PanelLocation.class);

                if (panelLocation != null) {

                    panel = new ViewPanel(
                            annotation.containsKey("id") ? scope.localize(annotation.get("id", String.class)) : action.getUrn(),
                            annotation.get("style", String.class));
                    panel.getAttributes().putAll(getMetadata(annotation, scope));

                    if (this.layout == null) {
                        this.layout = createLayout(behavior, scope);
                    }

                    switch(panelLocation) {
                    case Footer:
                        this.layout.setFooter(panel);
                        break;
                    case Header:
                        this.layout.setFooter(panel);
                        break;
                    case Left:
                        this.layout.getLeftPanels().add(panel);
                        break;
                    case Panel:
                        this.layout.getPanels().add(panel);
                        break;
                    case Right:
                        this.layout.getRightPanels().add(panel);
                        break;
                    case Window:
                    case Modal:
                        // TODO
                        break;
                    }
                }
            }

        } else {

            /*
             * must have component in scope; panel enters as new component
             */
            panel = new ViewPanel(behavior.getUrn(), behavior.getStyle());
            for (Annotation annotation : action.getAnnotations()) {
                panel.getAttributes().putAll(getMetadata(annotation, scope));
            }
        }

        return panel;
    }

    public ViewScope createLayout(Annotation annotation, String actionId, KActorsScope scope) {

        ViewScope ret = new ViewScope(this);

        ret.layout = new Layout(actionId, scope.getIdentity().getId());
        ret.layout.setStyle(this.layout.getStyle());
        ret.layout.setDestination(this.layout.getDestination());
        ret.layout.setLabel(scope.localize(annotation.get("title", "")));
        ret.layout.setDescription(Utils.Strings.pack(scope.localize(annotation.get("subtitle", ""))));
        ret.layout.setPlatform(this.layout.getPlatform());
        ret.layout.setLogo(annotation.get("logo", (String) null));
        ret.layout.setProjectId(this.layout.getProjectId());
        ret.layout.setApplicationId(scope.getIdentity().getId());
        ret.layout.setId(actionId);
        ret.layout.setType("modal".equals(annotation.getName()) ? ViewComponent.Type.ModalWindow : ViewComponent.Type.Window);
        ViewPanel panel = new ViewPanel(annotation.containsKey("id") ? annotation.get("id", String.class) : actionId,
                annotation.get("style", String.class));
        panel.getAttributes().putAll(getMetadata(annotation, scope));
        ret.layout.getPanels().add(panel);
        ret.currentComponent = panel;
        return ret;
    }

    public Layout createLayout(KActorsBehavior behavior, KActorsScope scope) {

        Layout ret = new Layout(behavior.getUrn(), scope.getIdentity().getId());
        ret.setIdentity(this.identityId);
        ret.setVersionString(behavior.getVersion().toString());
        ret.setStyle(behavior.getStyle());
        ret.setDestination(behavior.getType());
        ret.setLabel(scope.localize(behavior.getLabel()));
        ret.setDescription(Utils.Strings.pack(scope.localize(behavior.getDescription())));
        ret.setPlatform(behavior.getPlatform());
        ret.setLogo(behavior.getLogo());
        ret.setProjectId(behavior.getProjectId());

        for (var action : behavior.getStatements()) {
            Annotation menu = Utils.Annotations.getAnnotation(action, "menu");
            if (menu != null) {
                Layout.MenuItem menuItem = new Layout.MenuItem();
                menuItem.setId("menu." + action.getUrn());
                menuItem.setText(menu.containsKey("title") ? scope.localize(menu.get("title").toString()) : "Unnamed menu");
                ret.getMenu().add(menuItem);
            }
        }

        if (behavior.getStyleSpecs() != null) {
            ret.setStyleSpecs(Utils.Json.printAsJson(behavior.getStyleSpecs()));
        }
        return ret;
    }

    /**
     * Get the view scope for a panel, linked to an action
     * 
     * @param action
     * @return
     */
    public ViewScope getChild(KActorsAction action, KActorsBehavior behavior, KActorsScope scope) {

        // this creates the layout if needed.
//        this.applicationId = appId;
        ViewPanel panel = createPanel(action, behavior, scope);
        if (panel == null) {
            return this;
        }

        ViewScope ret = new ViewScope(this);
        ret.currentComponent = panel;
        return ret;
    }

    public ViewComponent getCurrentComponent() {
        return currentComponent;
    }

    public void setCurrentComponent(ViewComponent currentComponent) {
        this.currentComponent = currentComponent;
    }

    public Layout getLayout() {
        return layout;
    }

    public void setLayout(Layout layout) {
        this.layout = layout;
    }
    
    public static Map<String, String> getMetadata(Parameters<String> arguments, KActorsScope scope) {
        Map<String, String> ret = new HashMap<>();
        if (arguments != null) {
            for (String key : arguments.getNamedKeys()) {
                Object o = arguments.get(key);
                if (o instanceof KActorsValue) {
                    o = KActorsVM.evaluateInScope((KActorsValue) o, scope);
                }
                if (o instanceof String) {
                    o = scope == null ? (String) o : scope.localize((String) o);
                }
                if (o == null) {
                    ret.put(key, "null");
                } else {
                    ret.put(key, o.toString());
                }
            }
        }
        return ret;
    }

}