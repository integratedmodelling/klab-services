package org.integratedmodelling.klab.api.provenance;

import org.integratedmodelling.klab.api.data.RuntimeAsset;

/**
 * Plan is the dataflow in k.LAB.
 * 
 * @author Ferd
 *
 */
public interface Plan extends Provenance.Node {

    default RuntimeAsset.Type classify() {
        return RuntimeAsset.Type.PLAN;
    }
}
