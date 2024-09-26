package org.integratedmodelling.klab.services.runtime.neo4j;

import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.provenance.Plan;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.neo4j.driver.Driver;
import org.neo4j.driver.EagerResult;

import java.util.List;
import java.util.Map;

/**
 * TODO check spatial queries: https://www.lyonwj.com/blog/neo4j-spatial-procedures-congressional-boundaries
 *  and https://neo4j-contrib.github.io/spatial/0.24-neo4j-3.1/index.html
 */
public abstract class KnowledgeGraphNeo4j extends AbstractKnowledgeGraph {

    protected Driver driver;

    // all predefined Cypher queries
    interface Queries {

        String FIND_CONTEXT = "MATCH (ctx:Context {id: $contextId}) RETURN ctx";
        String FIND_BY_ID = "MATCH (n) WHERE id(n) = $id RETURN n";
        String CREATE_WITH_PROPERTIES = "CREATE (n:{type}) SET n = $properties RETURN id(n)";
        String UPDATE_PROPERTIES = "MATCH (n) WHERE id(n) = $id SET n += $properties";
        String INITIALIZATION_QUERY = "CREATE\n"
                + "\t// main context node\n"
                + "\t(ctx:Context {id: $contextId, name: $name, user: $username, created: $timestamp, expiration: $expirationType}),\n"
                + "\t// main provenance and dataflow nodes\n"
                + "\t(prov:Provenance), (df:Dataflow),\n"
                + "\t(ctx)<-[:HAS_PROVENANCE]-(prov),\n"
                + "\t(ctx)<-[:HAS_DATAFLOW]-(df),\n"
                + "\t// default agents within provenance\n"
                + "\t(user:Agent {name: $username, type: 'USER'}),\n"
                + "\t(klab:Agent {name: 'k.LAB', type: 'AI'}),\n"
                + "\t(prov)<-[:HAS_AGENT]-(user),\n"
                + "\t(prov)<-[:HAS_AGENT]-(klab),\n"
                + "\t// ACTIVITY that created the whole thing\n"
                + "\t(creation:Activity {started: $timestamp, ended: $timestamp}),\n"
                + "\t// created by user\n"
                + "\t(creation)<-[:BY_AGENT]-(user),\n"
                + "\t(ctx)<-[:CREATED]-(creation),\n"
                + "(prov)<-[:HAS_ACTIVITY]-(creation)";
    }

    protected EagerResult query(String query, Map<String, Object> parameters) {
        if (isOnline()) {
            try {
                return driver.executableQuery(query).withParameters(parameters).execute();
            } catch (Throwable t) {
                scope.error(t.getMessage(), t);
            }
        }
        return null;
    }

    /**
     * Ensure things are OK re: main agents and the like. Must be called only once
     */
    protected void initializeContext() {
        var result = query(Queries.FIND_CONTEXT, Map.of("contextId", scope.getId()));
        if (result.records().isEmpty()) {
            long timestamp = System.currentTimeMillis();
            result = query(
                    Queries.INITIALIZATION_QUERY,
                    Map.of(
                            "contextId", scope.getId(),
                            "name", scope.getName(),
                            "timestamp", timestamp,
                            "username", scope.getUser().getUsername(),
                            "expirationType", /* TODO */ "DEFAULT"));
        }
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
        if (scope == null) {
            driver.executableQuery("MATCH (n) DETACH DELETE n").execute();
        }
        // TODO REMOVE ONLY WHAT'S LINKED TO THE ROOT NODE
    }

    @Override
    protected long link(List<RuntimeAsset> targets, Map<String, Object> parameters) {
        return 0;
    }

    @Override
    protected long create(List<RuntimeAsset> targets, Map<String, Object> parameters) {
        long ret = Observation.UNASSIGNED_ID;
        for (var target : targets) {
            var type = switch(target) {
                case Observation x -> "Observation";
                case Actuator x -> "Actuator";
                case Agent x -> "Agent";
                case Plan x -> "Plan";
                default -> throw new KlabIllegalArgumentException("Cannot store " + target.getClass() + " in knowledge graph");
            };

            var result = query(Queries.CREATE_WITH_PROPERTIES.replace("{type}", type), asParameters(target));
            if (result != null && result.records().size() == 1) {
                var dio = result.records().getFirst().asMap();
                System.out.println("NDO STA EL ID DEL CAZ");
            }

        }
        return ret;
    }

    @Override
    protected long modify(List<RuntimeAsset> targets, Map<String, Object> parameters) {
        return 0;
    }

    @Override
    protected Map<String, Object> nodeProperties(long nodeId) {
        var result = query(Queries.FIND_BY_ID, Map.of("id", nodeId));
        if (result != null && !result.records().isEmpty()) {
            return result.records().getFirst().asMap();
        }
        return Map.of();
    }
}
