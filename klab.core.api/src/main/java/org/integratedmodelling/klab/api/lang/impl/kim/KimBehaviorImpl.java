package org.integratedmodelling.klab.api.lang.impl.kim;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

import org.integratedmodelling.klab.api.lang.Action;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.kim.KimBehavior;

/**
 * A IKimBehavior is the statement of the contextualization strategy for a model or an observation,
 * consisting of a list of action and a set of general methods for convenience.
 * 
 * @author fvilla
 *
 */
public class KimBehaviorImpl extends KimStatementImpl implements KimBehavior {

    @Serial
    private static final long serialVersionUID = 2701074196387350255L;
    private List<Action> actions = new ArrayList<>();
    private boolean empty;
    private boolean dynamic;
    private List<ServiceCall> extentFunctions = new ArrayList<>();

    @Override
    public List<Action> getActions() {
        return this.actions;
    }

    @Override
    public boolean isEmpty() {
        return this.empty;
    }

    @Override
    public boolean isDynamic() {
        return this.dynamic;
    }

    @Override
    public List<ServiceCall> getExtentFunctions() {
        return this.extentFunctions;
    }

    public void setActions(List<Action> actions) {
        this.actions = actions;
    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
    }

    public void setDynamic(boolean dynamic) {
        this.dynamic = dynamic;
    }

    public void setExtentFunctions(List<ServiceCall> extentFunctions) {
        this.extentFunctions = extentFunctions;
    }

}
