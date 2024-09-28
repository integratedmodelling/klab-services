package org.integratedmodelling.klab.api.provenance.impl;

import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Agent;

import java.util.Optional;

public class ActivityImpl extends ProvenanceNodeImpl implements Activity {

    @Override
    public long getStart() {
        return 0;
    }

    @Override
    public long getEnd() {
        return 0;
    }

    @Override
    public long getSchedulerTime() {
        return 0;
    }
}
