package org.integratedmodelling.klab.api.lang.kactors;

import java.util.ArrayList;
import java.util.List;

import org.integratedmodelling.klab.api.collections.impl.ParametersImpl;

/**
 * Arguments with possible unnamed parameters, actually named _p1, _p2.... Also keeps a set of any
 * arguments passed through keyed metadata (which enter the hash anyway) for later checking.
 * 
 * @author Ferd
 *
 */
public class KActorsArguments extends ParametersImpl<String> implements KActorsStatement.Arguments {

    private static final long serialVersionUID = -7349739696964126574L;

    List<String> metadataKeys = new ArrayList<>();

    public List<String> getMetadataKeys() {
        return metadataKeys;
    }

    // public void visit(IKActorsAction action, IKActorsStatement statement, Visitor visitor) {
    // // TODO Auto-generated method stub
    // for (Object value : values()) {
    // if (value instanceof KActorsValue) {
    // KActorsStatement.visitValue(visitor, (KActorsValue)value, statement, action);
    // }
    // }
    // }

}
