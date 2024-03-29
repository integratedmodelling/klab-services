package org.integratedmodelling.klab.api.lang.kactors.beans;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;

/**
 * Holds the state of a view component. Not polymorphic except for {@link Layout}, {@link ViewPanel} and some highly
 * specialized view types. It's part of the message used by a view-enabled actor to request creation of the view
 * component and is kept at both the view and model side so that state is synchronized.
 *
 * @author Ferd
 */
public class ViewComponent {

    /**
     * If type == Container, this will be filled later as the component it's supposed to host can only be computed at
     * runtime. It may be a group container (if the components are created in a loop) or another; the containedType
     * specifies what.
     *
     * @author Ferd
     */
    public enum Type {
        Panel, Alert, PushButton, CheckButton, RadioButton, TextInput, Combo, Group, Map, Tree, TreeItem,
        Confirm, View, Container, MultiContainer, Label, Text, Table, Notification, InputGroup, Separator,
        ModalWindow, Window, Browser, Image
        // etc
    }

    /**
     * A tree is a list of nodes (each a String->String map) and a list of child->parent links, expressed using the
     * index of the values in the list. For convenience, the index of the root node is also provided.
     *
     * @author Ferd
     */
    public static class Tree {

        private Map<String, Map<String, String>> values = new HashMap<>();
        private String rootId;
        private List<Pair<String, String>> links = new ArrayList<>();

        public String getRootId() {
            return rootId;
        }

        public void setRootId(String rootId) {
            this.rootId = rootId;
        }

        public List<Pair<String, String>> getLinks() {
            return links;
        }

        public void setLinks(List<Pair<String, String>> links) {
            this.links = links;
        }

        public Map<String, Map<String, String>> getValues() {
            return values;
        }

        public void setValues(Map<String, Map<String, String>> values) {
            this.values = values;
        }

    }

    private String id;
    private String identity;
    private String applicationId;
    private String parentId;
    private Type type;
    private String name;
    private String style;
    private String title;
    private Artifact.Type contentType;
    private String content;
    private Tree tree;
    private Layout layout;
    private List<ViewComponent> components = new ArrayList<>();
    private Map<String, String> attributes = new HashMap<>();
    private KActorsBehavior.Type destination;
    private KActorsBehavior.Platform platform;
    private List<Pair<String, String>> choices = new ArrayList<>();
    private String actorPath = null;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public String getStyle() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Artifact.Type getContentType() {
        return contentType;
    }

    public void setContentType(Artifact.Type contentType) {
        this.contentType = contentType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<ViewComponent> getComponents() {
        return components;
    }

    public void setComponents(List<ViewComponent> components) {
        this.components = components;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> data) {
        this.attributes = data;
    }

    @Override
    public String toString() {
        return "ViewComponent [parentId=" + parentId + ", type=" + type + ", name=" + name + ", title=" + title + ", " +
                "content="
                + content + ", attributes=" + attributes + "]";
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String identity) {
        this.applicationId = identity;
    }

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    public Tree getTree() {
        return tree;
    }

    public void setTree(Tree tree) {
        this.tree = tree;
    }

    public KActorsBehavior.Type getDestination() {
        return destination;
    }

    public void setDestination(KActorsBehavior.Type destination) {
        this.destination = destination;
    }

    public KActorsBehavior.Platform getPlatform() {
        return platform;
    }

    public void setPlatform(KActorsBehavior.Platform platform) {
        this.platform = platform;
    }

    public Layout getLayout() {
        return layout;
    }

    public void setLayout(Layout layout) {
        this.layout = layout;
    }

    /**
     * Actor path is null if the component is top-level, otherwise it will contain the path to the actor that all view
     * messages should be forwarded to, so that the top-level actor can send them.
     *
     * @return
     */
    public String getActorPath() {
        return actorPath;
    }

    public void setActorPath(String actorPath) {
        this.actorPath = actorPath;
    }

    public List<Pair<String, String>> getChoices() {
        return choices;
    }

    public void setChoices(List<Pair<String, String>> choices) {
        this.choices = choices;
    }

}
