package org.integratedmodelling.klab.services.runtime.neo4j;

import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.neo4j.driver.Driver;
import org.neo4j.driver.ExecutableQuery;

public abstract class KnowledgeGraphNeo4j  extends AbstractKnowledgeGraph{

    protected Driver driver;

    /**
     * Ensure things are OK re: agents and the like
     */
    protected void initializeContext() {

    }

    @Override
    public Agent user() {
        return null;
    }

    @Override
    public Agent klab() {
        return null;
    }

    @Override
    public void clear() {
        if (!contextualized) {
            driver.executableQuery("MATCH (n) DETACH DELETE n").execute();
        }
        // TODO REMOVE ONLY WHAT'S LINKED TO THE ROOT NODE
    }

    @Override
    protected long runOperation(OperationImpl operation) {
        return Observation.UNASSIGNED_ID;
    }

}
