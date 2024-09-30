package org.integratedmodelling.klab.services.runtime.neo4j;

import org.checkerframework.checker.units.qual.C;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.provenance.Plan;
import org.integratedmodelling.klab.api.provenance.impl.ActivityImpl;
import org.integratedmodelling.klab.api.provenance.impl.AgentImpl;
import org.integratedmodelling.klab.api.provenance.impl.PlanImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.objects.ContextInfo;
import org.neo4j.driver.Driver;
import org.neo4j.driver.EagerResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TODO check spatial queries: https://www.lyonwj.com/blog/neo4j-spatial-procedures-congressional-boundaries
 *  and https://neo4j-contrib.github.io/spatial/0.24-neo4j-3.1/index.html
 * <p>
 *  TODO must figure out where the heck the neo4j-spatial-5.20.0.jar is (no, it's not in
 *   https://github.com/neo4j-contrib/m2 nor in osgeo)
 */
public abstract class KnowledgeGraphNeo4j extends AbstractKnowledgeGraph {

    protected Driver driver;
    protected Agent user;
    protected Agent klab;

    // all predefined Cypher queries
    interface Queries {

        String REMOVE_CONTEXT = "match (n:Context {id: $contextId})-[*]-(c) detach delete n,c";
        String FIND_CONTEXT = "MATCH (ctx:Context {id: $contextId}) RETURN ctx";
        String FIND_BY_ID = "MATCH (n) WHERE id(n) = $id RETURN n";
        String FIND_BY_PROPERTY = "MATCH (n:{type}) WHERE n.{property} = $value RETURN n";
        // retrieve ID as records().getFirst().get(keys().getFirst()) ?
        String CREATE_WITH_PROPERTIES = "CREATE (n:{type}) SET n = $properties RETURN id(n) as id";
        String UPDATE_PROPERTIES = "MATCH (n) WHERE id(n) = $id SET n += $properties";
        String INITIALIZATION_QUERY = "CREATE\n"
                + "\t// main context node\n"
                + "\t(ctx:Context {id: $contextId, name: $name, user: $username, created: $timestamp, expiration: $expirationType}),\n"
                + "\t// main provenance and dataflow nodes\n"
                + "\t(prov:Provenance {name: 'Provenance'}), (df:Dataflow {name: 'Dataflow'}),\n"
                + "\t(ctx)-[:HAS_PROVENANCE]->(prov),\n"
                + "\t(ctx)-[:HAS_DATAFLOW]->(df),\n"
                + "\t// default agents within provenance\n"
                + "\t(user:Agent {name: $username, type: 'USER'}),\n"
                + "\t(klab:Agent {name: 'k.LAB', type: 'AI'}),\n"
                + "\t(prov)-[:HAS_AGENT]->(user),\n"
                + "\t(prov)-[:HAS_AGENT]->(klab),\n"
                + "\t// ACTIVITY that created the whole thing\n"
                + "\t(creation:Activity {start: $timestamp, end: $timestamp, name: 'Initialization'}),\n"
                + "\t// created by user\n"
                + "\t(creation)-[:BY_AGENT]->(user),\n"
                + "\t(ctx)<-[:CREATED]-(creation),\n"
                + "(prov)-[:HAS_ACTIVITY]->(creation)";
        String GET_AGENT_BY_NAME = "match (ctx:Context {id: $contextId})-->(prov:Provenance)-[:HAS_AGENT]->(a:Agent {name: $agentName}) RETURN a";
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
            query(
                    Queries.INITIALIZATION_QUERY,
                    Map.of(
                            "contextId", scope.getId(),
                            "name", scope.getName(),
                            "timestamp", timestamp,
                            "username", scope.getUser().getUsername(),
                            "expirationType", scope.getExpiration().name()));
        }

        this.user = adapt(
                query(
                        Queries.GET_AGENT_BY_NAME,
                        Map.of("contextId", scope.getId(), "agentName", scope.getUser().getUsername())),
                Agent.class).getFirst();
        this.klab = adapt(query(
                Queries.GET_AGENT_BY_NAME,
                Map.of("contextId", scope.getId(), "agentName", "k.LAB")), Agent.class).getFirst();
    }

    @Override
    public void deleteContext() {
        query(Queries.REMOVE_CONTEXT, Map.of("contextId", scope.getId()));
    }

    protected <T extends RuntimeAsset> List<T> adapt(EagerResult query, Class<T> cls) {
        List<T> ret = new ArrayList<>();

        for (var record : query.records()) {

            if (Agent.class.isAssignableFrom(cls)) {

                var instance = new AgentImpl();
                instance.setName(record.get("name", (String) null));
                instance.setEmpty(false);

                ret.add((T) instance);

            } else if (Observation.class.isAssignableFrom(cls)) {
                var instance = new ObservationImpl();

                ret.add((T) instance);
            } else if (Activity.class.isAssignableFrom(cls)) {
                var instance = new ActivityImpl();
                ret.add((T) instance);
            } else if (Actuator.class.isAssignableFrom(cls)) {
                var instance = new ActuatorImpl();
                ret.add((T) instance);
            } else if (Plan.class.isAssignableFrom(cls)) {
                var instance = new PlanImpl();
                ret.add((T) instance);
            }
        }
        return ret;
    }

    @Override
    public Agent user() {
        return user;
    }

    @Override
    public Agent klab() {
        return klab;
    }

    @Override
    public List<ContextInfo> getExistingContexts(UserScope scope) {

        var ret = new ArrayList<ContextInfo>();
        var result = scope == null
                     ? query(
                "match (c:Context)<-[:CREATED]-(a:Activity) return c.id as contextId, a.start as startTime",
                Map.of())
                     : query(
                             "match (c:Context {user: $username})<-[:CREATED]-(a:Activity) return c.name as contextName, c.id as contextId, a.start as startTime",
                             Map.of("username", scope.getUser().getUsername()));

        for (var record : result.records()) {
            ContextInfo info = new ContextInfo();
            info.setId(record.get("contextId").asString());
            info.setName(record.get("contextName").asString());
            info.setCreationTime(record.get("startTime").asLong());
            // TODO the rest
            ret.add(info);
        }
        return ret;
    }

    @Override
    public void clear() {
        if (scope == null) {
            driver.executableQuery("MATCH (n) DETACH DELETE n").execute();
        } else {
            query(Queries.REMOVE_CONTEXT, Map.of("contextId", scope.getId()));
        }
    }

    //    @Override
    protected long link(List<RuntimeAsset> targets, Map<String, Object> parameters) {

        System.out.println("LINK THESE FUCKERS");

        return 0;
    }

    //    @Override
//    protected long create(List<RuntimeAsset> targets, Map<String, Object> parameters) {
//
//        return ret;
//    }


    @Override
    protected long runOperation(OperationImpl operation, ContextScope scope) {

        // TODO use a transaction for the entire sequence of operations

        long ret = Observation.UNASSIGNED_ID;
        for (var step : operation.getSteps()) {
            switch (step.type()) {
                case CREATE -> {

                    for (var target : step.targets()) {
                        var type = switch (target) {
                            case Observation x -> "Observation";
                            case Activity x -> "Activity";
                            case Actuator x -> "Actuator";
                            case Agent x -> "Agent";
                            case Plan x -> "Plan";
                            default -> throw new KlabIllegalArgumentException(
                                    "Cannot store " + target.getClass() + " in knowledge graph");
                        };

                        var props = asParameters(target);
                        var result = query(
                                Queries.CREATE_WITH_PROPERTIES.replace("{type}", type),
                                Map.of("properties", asParameters(target)));
                        if (result != null && result.records().size() == 1) {
                            ret = result.records().getFirst().get(result.keys().getFirst()).asLong();
                            if (target instanceof ObservationImpl observation) {
                                observation.setId(ret);
                                observation.setUrn(scope.getId() + "." + ret);
                                props.put("urn", observation.getUrn());
                                query(
                                        Queries.UPDATE_PROPERTIES.replace("{type}", type),
                                        Map.of("id", ret, "properties", props));
                            }
                        }
                    }
                }
                case MODIFY -> {
                    ret = modify(step.targets(), step.parameters());
                }
                case LINK -> {

                    // match (n:Diocan), (c:Context) WHERE n.porco = 'Dio' AND c.id = '1io3bjbpr.1ioau1c8g' CREATE (c)-[r:DIOCAN]->(n) return r

                    ret = link(step.targets(), step.parameters());
                }
            }
            return ret;
        }

        return Observation.UNASSIGNED_ID;
    }

    //    protected abstract long create(List<RuntimeAsset> targets, Map<String, Object> parameters);
    //
    //    protected abstract long link(List<RuntimeAsset> targets, Map<String, Object> parameters);
    //
    //    protected abstract long modify(List<RuntimeAsset> targets, Map<String, Object> parameters);


    //    @Override
    protected long modify(List<RuntimeAsset> targets, Map<String, Object> parameters) {
        return 0;
    }

    @Override
    protected void finalizeOperation(OperationImpl operation, ContextScope scope, boolean b) {
        // TODO! Update activity with status and time
    }
}
