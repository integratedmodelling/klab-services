package org.integratedmodelling.klab.api.lang.impl.kim;

import java.util.List;

import org.integratedmodelling.klab.api.lang.kim.KimInstance;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;

public class KimAcknowledgementImpl extends KimActiveStatementImpl implements KimInstance {

    private static final long serialVersionUID = -2269601151635547580L;

    private String urn;
    private String name;
    private KimObservable observable;
    private List<KimObservable> states;
    private String docstring;

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

}
