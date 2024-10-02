/*
 * This file is part of k.LAB.
 *
 * k.LAB is free software: you can redistribute it and/or modify it under the terms of the Affero
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * A copy of the GNU Affero General Public License is distributed in the root directory of the k.LAB
 * distribution (LICENSE.txt). If this cannot be found see <http://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2007-2018 integratedmodelling.org and any authors mentioned in author tags. All
 * rights reserved.
 */
package org.integratedmodelling.klab.api.provenance;

import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.provenance.impl.AgentImpl;

/**
 * An agent in k.LAB is anything that can start observation activities. In a standard k.LAB
 * {@link org.integratedmodelling.klab.api.data.KnowledgeGraph} two agents, one representing the user and
 * another representing the AI decision-making in k.LAB, are predefined. Others may represent observed agent
 * observations in the same knowledge graph.
 *
 * @author Ferd
 * @version $Id: $Id
 */
public interface Agent extends Provenance.Node {

    default RuntimeAsset.Type classify() {
        return Type.AGENT;
    }

    static AgentImpl promote(Agent agent) {
        if (agent instanceof AgentImpl agentImpl) {
            return agentImpl;
        }
        // TODO copy fields
        throw new KlabUnimplementedException("Agent::promote");
    }
}
