package org.integratedmodelling.klab.api.services.resources;

import java.util.ArrayList;
import java.util.List;

import org.integratedmodelling.klab.api.collections.impl.Pair;
import org.integratedmodelling.klab.api.data.Version;

/**
 * The output of any resources GET endpoint that provides models, projects, worldview, behaviors or
 * resources. The returned resources should be matched with anything already loaded and their
 * versions; the order of each array is the load dependency order.
 * <p>
 * A method isEmpty() is provided to streamline usage and possibly differentiate from a resource set
 * that contains no resources but actually represents as a non-empty result.
 * 
 * @author Ferd
 *
 */
public class ResourceSet {

    private List<Pair<String, Version>> namespaces = new ArrayList<>();
    private List<Pair<String, Version>> behaviors = new ArrayList<>();
    private List<Pair<String, Version>> resources = new ArrayList<>();
    private boolean empty;

    public List<Pair<String, Version>> getNamespaces() {
        return namespaces;
    }
    public void setNamespaces(List<Pair<String, Version>> namespaces) {
        this.namespaces = namespaces;
    }
    public List<Pair<String, Version>> getBehaviors() {
        return behaviors;
    }
    public void setBehaviors(List<Pair<String, Version>> behaviors) {
        this.behaviors = behaviors;
    }
    public List<Pair<String, Version>> getResources() {
        return resources;
    }
    public void setResources(List<Pair<String, Version>> resources) {
        this.resources = resources;
    }
    public boolean isEmpty() {
        return empty;
    }
    public void setEmpty(boolean empty) {
        this.empty = empty;
    }

}
