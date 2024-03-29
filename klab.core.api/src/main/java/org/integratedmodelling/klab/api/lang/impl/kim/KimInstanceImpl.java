package org.integratedmodelling.klab.api.lang.impl.kim;

import org.integratedmodelling.klab.api.lang.kim.KimInstance;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;

import java.util.ArrayList;
import java.util.List;

public class KimInstanceImpl extends KimActiveStatementImpl implements KimInstance {

    private static final long serialVersionUID = -2269601151635547580L;

    private String urn;
    private String name;
    private KimObservable observable;
    private List<KimObservable> states = new ArrayList<>();
    private String docstring;
    private List<KimInstance> children = new ArrayList<>();

    @Override
    public String getUrn() {
        return this.urn;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public KimObservable getObservable() {
        return this.observable;
    }

    @Override
    public List<KimObservable> getStates() {
        return this.states;
    }

    @Override
    public String getDocstring() {
        return this.docstring;
    }

    public void setUrn(String urn) {
        this.urn = urn;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setObservable(KimObservable observable) {
        this.observable = observable;
    }

    public void setStates(List<KimObservable> states) {
        this.states = states;
    }

    public void setDocstring(String docstring) {
        this.docstring = docstring;
    }

    @Override
    public List<KimInstance> getChildren() {
        return children;
    }

    public void setChildren(List<KimInstance> children) {
        this.children = children;
    }

    @Override
    public void visit(KlabStatementVisitor visitor) {

    }
}
