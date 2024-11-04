package org.integratedmodelling.klab.services.runtime.neo4j;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import org.hsqldb.rights.User;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.provenance.Plan;
import org.integratedmodelling.klab.api.provenance.impl.ActivityImpl;
import org.integratedmodelling.klab.api.provenance.impl.AgentImpl;
import org.integratedmodelling.klab.api.provenance.impl.PlanImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.objects.ContextInfo;
import org.integratedmodelling.klab.api.services.runtime.objects.SessionInfo;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.runtime.scale.space.ShapeImpl;
import org.neo4j.driver.Driver;
import org.neo4j.driver.EagerResult;
import org.neo4j.driver.Value;

import java.util.*;

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
    protected String rootContextId;

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
                + "\t(ctx:Context {id: $contextId, name: $name, user: $username, created: $timestamp, " +
                "expiration: $expirationType}),\n"
                + "\t// main provenance and dataflow nodes\n"
                + "\t(prov:Provenance {name: 'Provenance', id: $contextId + '.PROVENANCE'}), (df:Dataflow " +
                "{name: 'Dataflow', id: $contextId + '.DATAFLOW'}),\n"
                + "\t(ctx)-[:HAS_PROVENANCE]->(prov),\n"
                + "\t(ctx)-[:HAS_DATAFLOW]->(df),\n"
                + "\t// default agents within provenance\n"
                + "\t(user:Agent {name: $username, type: 'USER'}),\n"
                + "\t(klab:Agent {name: 'k.LAB', type: 'AI'}),\n"
                + "\t(prov)-[:HAS_AGENT]->(user),\n"
                + "\t(prov)-[:HAS_AGENT]->(klab),\n"
                + "\t// ACTIVITY that created the whole thing\n"
                + "\t(creation:Activity {start: $timestamp, end: $timestamp, name: 'INITIALIZATION'}),\n"
                + "\t// created by user\n"
                + "\t(creation)-[:BY_AGENT]->(user),\n"
                + "\t(ctx)<-[:CREATED]-(creation),\n"
                + "(prov)-[:HAS_CHILD]->(creation)";
        String GET_AGENT_BY_NAME = "match (ctx:Context {id: $contextId})-->(prov:Provenance)-[:HAS_AGENT]->" +
                "(a:Agent {name: $agentName}) RETURN a";
        String LINK_ASSETS = "match (n:{fromLabel}), (c:{toLabel}) WHERE n.{fromKeyProperty} = $fromKey AND" +
                " c.{toKeyProperty} = $toKey CREATE (n)-[r:{relationshipLabel}]->(c) return r";
    }

    protected EagerResult query(String query, Map<String, Object> parameters, Scope scope) {
        if (isOnline()) {
            try {
                return driver.executableQuery(query).withParameters(parameters).execute();
            } catch (Throwable t) {
                scope.error(t.getMessage(), t);
            }
        }
        return null;
    }


    protected RuntimeAsset getAssetForKey(long key) {
        throw new KlabUnimplementedException("RETRIEVAL OF ARBITRARY ASSET FROM DB INTO CACHE");
    }

    /**
     * Ensure things are OK re: main agents and the like. Must be called only once
     */
    protected void initializeContext() {

        this.rootContextId = scope.getId();

        var result = query(Queries.FIND_CONTEXT, Map.of("contextId", scope.getId()), scope);

        if (result.records().isEmpty()) {
            long timestamp = System.currentTimeMillis();
            query(
                    Queries.INITIALIZATION_QUERY,
                    Map.of(
                            "contextId", scope.getId(),
                            "name", scope.getName(),
                            "timestamp", timestamp,
                            "username", scope.getUser().getUsername(),
                            "expirationType", scope.getExpiration().name()),
                    scope);
        }

        this.user = adapt(
                query(
                        Queries.GET_AGENT_BY_NAME,
                        Map.of("contextId", scope.getId(), "agentName", scope.getUser().getUsername()),
                        scope),
                Agent.class, scope).getFirst();
        this.klab = adapt(query(
                Queries.GET_AGENT_BY_NAME,
                Map.of("contextId", scope.getId(), "agentName", "k.LAB"), scope), Agent.class, scope).getFirst();
    }

    @Override
    public void deleteContext() {
        query(Queries.REMOVE_CONTEXT, Map.of("contextId", scope.getId()), scope);
    }

    /**
     * @param query
     * @param cls
     * @param <T>
     * @return
     */
    protected <T> List<T> adapt(EagerResult query, Class<T> cls, Scope scope) {

        List<T> ret = new ArrayList<>();

        for (var record : query.records()) {

            Value node = null;
            Map<String, Object> properties = new HashMap<>();
            if (!record.values().isEmpty()) {
                // must be one field for the node
                node = record.values().getFirst();
            }

            if (node == null) {
                continue;
            }

            if (Map.class.isAssignableFrom(cls)) {

                ret.add((T) node.asMap(Map.of()));

            } else if (Agent.class.isAssignableFrom(cls)) {

                var instance = new AgentImpl();
                instance.setName(node.get("name").asString());
                instance.setEmpty(false);

                ret.add((T) instance);

            } else if (Observation.class.isAssignableFrom(cls)) {

                var instance = new ObservationImpl();
                var reasoner = scope.getService(Reasoner.class);

                instance.setUrn(node.get("urn").asString());
                instance.setName(node.get("name").asString());
                instance.setObservable(reasoner.resolveObservable(node.get("semantics").asString()));
                instance.setResolved(node.get("resolved").asBoolean());
                instance.setId(node.get("id").asLong());

                // SHIT, THE GEOMETRY - geometry, metadata etc
                var gResult = query("MATCH (o:Observation)-[:HAS_GEOMETRY]->(g:Geometry) WHERE id" +
                        "(o) = $id RETURN g", Map.of("id", node.get("id").asLong()), scope);

                if (gResult == null || !gResult.records().isEmpty()) {
                    instance.setGeometry(adapt(gResult, Geometry.class, scope).getFirst());
                }

                ret.add((T) instance);

            } else if (Activity.class.isAssignableFrom(cls)) {
                var instance = new ActivityImpl();
                // TODO
                ret.add((T) instance);
            } else if (Actuator.class.isAssignableFrom(cls)) {
                var instance = new ActuatorImpl();
                // TODO
                ret.add((T) instance);
            } else if (Plan.class.isAssignableFrom(cls)) {
                var instance = new PlanImpl();
                // TODO
                ret.add((T) instance);
            } else if (Geometry.class.isAssignableFrom(cls)) {
                ret.add((T) Geometry.create(node.get("definition").asString()));
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
                Map.of(), scope)
                     : query(
                             "match (c:Context {user: $username})<-[:CREATED]-(a:Activity) return c.name as" +
                                     " contextName, c.id as contextId, a.start as startTime",
                             Map.of("username", scope.getUser().getUsername()), scope);

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
            query(Queries.REMOVE_CONTEXT, Map.of("contextId", scope.getId()), scope);
        }
    }

    @Override
    protected long runOperation(OperationImpl operation, ContextScope scope) {

        operation.registerAsset(scope, "id", scope.getId());

        /*
        TODO use a transaction for the whole sequence!
        First pass defines the activity. If we have one in the targets, that is the parent activity to
        link it to.
         */

        List<Long> created = new ArrayList<>();
        List<Long> plans = new ArrayList<>();

        long ret = Observation.UNASSIGNED_ID;
        for (var step : operation.getSteps()) {
            switch (step.type()) {
                case CREATE -> {

                    for (var target : step.targets()) {

                        var type = getLabel(target);
                        var props = asParameters(target);
                        var result = query(
                                Queries.CREATE_WITH_PROPERTIES.replace("{type}", type),
                                Map.of("properties", asParameters(target)), scope);
                        if (result != null && result.records().size() == 1) {
                            ret = result.records().getFirst().get(result.keys().getFirst()).asLong();
                            if (target instanceof ObservationImpl observation) {

                                observation.setId(ret);
                                created.add(ret);
                                observation.setUrn(scope.getId() + "." + ret);
                                props.put("id", ret);
                                props.put("urn", observation.getUrn());
                                // TODO generate the IDs internally and skip this
                                query(
                                        Queries.UPDATE_PROPERTIES.replace("{type}", type),
                                        Map.of("id", ret, "properties", props), scope);

                                // TODO store spatial and temporal boundaries or ideally the geometry as is
                                //  using neo4j-spatial, hoping it appears on maven central
                                var geometry = encodeGeometry(scope.getObservationGeometry(observation));
                                var geoRecord = query("MATCH (g:Geometry {definition: $definition}) RETURN g",
                                        Map.of("definition", geometry), scope);

                                if (geoRecord.records().isEmpty()) {
                                    query("MATCH (o:Observation {id: $observationId}) CREATE (g:Geometry " +
                                                    "{definition: $definition}), (o)-[:HAS_GEOMETRY]->(g)",
                                            Map.of("observationId", ret, "definition", geometry), scope);
                                } else {
                                    query("MATCH (o:Observation {id: $observationId}), (g:Geometry " +
                                                    "{definition: $definition}) CREATE (o)" +
                                                    "-[:HAS_GEOMETRY]->(g)",
                                            Map.of("observationId", ret, "definition", geometry), scope);
                                }

                            } else if (target instanceof ActuatorImpl actuator) {

                                // TODO generate the ID and skip the update query
                                actuator.setId(ret);
                                created.add(ret);
                                props.put("id", ret);

                                query(
                                        Queries.UPDATE_PROPERTIES.replace("{type}", type),
                                        Map.of("id", ret, "properties", props), scope);
                            } else if (target instanceof ActivityImpl activity) {

                                // TODO generate the ID and skip the update query
                                activity.setId(ret);
                                props.put("id", ret);
                                query(
                                        Queries.UPDATE_PROPERTIES.replace("{type}", type),
                                        Map.of("id", ret, "properties", props), scope);
                            } else if (target instanceof PlanImpl plan) {

                                // TODO generate the ID and skip the update query
                                plan.setId(ret);
                                plans.add(ret);
                                props.put("id", ret);
                                query(
                                        Queries.UPDATE_PROPERTIES.replace("{type}", type),
                                        Map.of("id", ret, "properties", props), scope);
                            }

                            operation.registerAsset(target, "id", ret);
                        }
                    }
                }
                case MODIFY -> {
                    // TODO - do we need this here? maybe with scheduling - for now it's only at finalization
                    throw new KlabUnimplementedException("target setting or graph modification");
                }
                case LINK -> {

                    DigitalTwin.Relationship relationship = null;
                    var props = new HashMap<String, Object>();
                    for (int i = 0; i < step.parameters().length; i++) {
                        var arg = step.parameters()[i];
                        if (arg instanceof DigitalTwin.Relationship dr) {
                            relationship = dr;
                        } else {
                            props.put(arg.toString(), step.parameters()[++i]);
                        }
                    }

                    if (relationship != null && step.targets().size() == 2) {

                        var query = Queries.LINK_ASSETS
                                .replace("{relationshipLabel}", relationship.name())
                                .replace("{fromLabel}", getLabel(step.targets().getFirst()))
                                .replace("{toLabel}", getLabel(step.targets().getLast()))
                                .replace(
                                        "{fromKeyProperty}",
                                        operation.getAssetKeyProperty(step.targets().getFirst()))
                                .replace(
                                        "{toKeyProperty}",
                                        operation.getAssetKeyProperty(step.targets().getLast()));

                        query(query, Map.of("fromKey", operation.getAssetKey(step.targets().getFirst()),
                                "toKey", operation.getAssetKey(step.targets().getLast())), scope);
                    }
                }
                case ROOT_LINK -> {

                    if (step.targets().getFirst() instanceof Observation observation) {

                        var query = Queries.LINK_ASSETS
                                .replace("{relationshipLabel}", DigitalTwin.Relationship.HAS_CHILD.name())
                                .replace("{fromLabel}", "Context")
                                .replace("{toLabel}", "Observation")
                                .replace("{fromKeyProperty}", "id")
                                .replace("{toKeyProperty}", "id");

                        query(query, Map.of("fromKey", rootContextId, "toKey", observation.getId()), scope);

                    } else if (step.targets().getFirst() instanceof Actuator actuator) {

                        var query = Queries.LINK_ASSETS
                                .replace("{relationshipLabel}", DigitalTwin.Relationship.HAS_CHILD.name())
                                .replace("{fromLabel}", "Dataflow")
                                .replace("{toLabel}", "Actuator")
                                .replace("{fromKeyProperty}", "id")
                                .replace("{toKeyProperty}", "id");

                        query(
                                query,
                                Map.of("fromKey", rootContextId + ".DATAFLOW", "toKey", actuator.getId()),
                                scope);

                    } else if (step.targets().getFirst() instanceof Activity activity) {

                        var query = Queries.LINK_ASSETS
                                .replace("{relationshipLabel}", DigitalTwin.Relationship.HAS_CHILD.name())
                                .replace("{fromLabel}", "Provenance")
                                .replace("{toLabel}", "Activity")
                                .replace("{fromKeyProperty}", "id")
                                .replace("{toKeyProperty}", "id");

                        query(
                                query,
                                Map.of("fromKey", rootContextId + ".PROVENANCE", "toKey", activity.getId())
                                , scope);

                    } else {
                        throw new KlabInternalErrorException("unexpected root link request");
                    }
                }
            }
        }

        /*
        Link created assets to the activity
         */
        for (long asset : created) {
            query(
                    "match (n:Activity), (c) WHERE id(n) = $fromId AND id(c) = $toId CREATE (n)" +
                            "-[r:CREATED]->(c) return r",
                    Map.of("fromId", operation.getActivity().getId(), "toId", asset), scope);
        }

        /*
        Link any plans to the activity (should be one at most)
         */
        for (long plan : plans) {
            query(
                    "match (n:Activity), (c:Plan) WHERE id(n) = $fromId AND id(c) = $toId CREATE (n)" +
                            "-[r:HAS_PLAN]->(c) return r",
                    Map.of("fromId", operation.getActivity().getId(), "toId", plan), scope);
        }

        // link the activity to the agent
        query(
                "match (n:Activity), (c:Agent) WHERE id(n) = $fromId AND c.name = $agentName CREATE (n)" +
                        "-[r:BY_AGENT]->(c) return r",
                Map.of(
                        "fromId", operation.getActivity().getId(), "agentName",
                        operation.getAgent().getName()), scope);

        return ret;
    }

    private String encodeGeometry(Geometry observationGeometry) {

        /*
         * Ensure that the shape parameter is in WKB and any prescriptive grid parameters are resolved.
         * TODO we should cache the geometries and scales, then reuse them.
         */
        var ret = Scale.create(observationGeometry).encode(ShapeImpl.wkbEncoder);

        return ret;

    }

    private String getLabel(Object target) {

        if (target instanceof Class<?> cls) {
            if (Observation.class.isAssignableFrom(cls)) {
                return "Observation";
            } else if (Activity.class.isAssignableFrom(cls)) {
                return "Activity";
            } else if (Actuator.class.isAssignableFrom(cls)) {
                return "Actuator";
            } else if (Agent.class.isAssignableFrom(cls)) {
                return "Agent";
            } else if (Plan.class.isAssignableFrom(cls)) {
                return "Plan";
            } else {
                throw new KlabIllegalArgumentException(
                        "Cannot store " + cls + " in knowledge graph");
            }
        }

        return switch (target) {
            case Observation x -> "Observation";
            case Activity x -> "Activity";
            case Actuator x -> "Actuator";
            case Agent x -> "Agent";
            case Plan x -> "Plan";
            default -> throw new KlabIllegalArgumentException(
                    "Cannot store " + target.getClass() + " in knowledge graph");
        };

    }

    @Override
    protected void finalizeOperation(OperationImpl operation, ContextScope scope, boolean success,
                                     RuntimeAsset... results) {

        var props = asParameters(operation.getActivity());
        props.put("end", System.currentTimeMillis());
        query(Queries.UPDATE_PROPERTIES.replace("{type}", "Activity"),
                Map.of("id", operation.getActivity().getId(), "properties", props), scope);

        for (var asset : results) {

        }

        System.out.println((success ? "YEAH " : "FUCK ") + ": FINALIZE THIS SHIT");
    }

    @Override
    public <T extends RuntimeAsset> List<T> get(ContextScope scope, Class<T> resultClass,
                                                Object... queriables) {

        Map<String, Object> queryParameters = new LinkedHashMap<>();
        if (queriables != null) {
            for (var parameter : queriables) {
                if (parameter instanceof Observable observable) {
                    queryParameters.put("semantics", observable.getSemantics().getUrn());
                } else if (parameter instanceof Long id) {
                    queryParameters.put("id", id);
                }// TODO hostia
            }
        }

        if (queryParameters.containsKey("id") && RuntimeAsset.class.isAssignableFrom(resultClass)) {
            return adapt(query(Queries.FIND_BY_ID, queryParameters, scope), resultClass, scope);
        }

        StringBuilder locator = new StringBuilder("MATCH (c:Context {id: $contextId})");
        var scopeData = ContextScope.parseScopeId(ContextScope.getScopeId(scope));
        if (scopeData.observationPath() != null) {
            for (var observationId : scopeData.observationPath()) {
                locator.append("-[:HAS_CHILD]->(Observation {id: ").append(observationId).append("})");
            }
        }
        if (scopeData.observerId() != Observation.UNASSIGNED_ID) {
            // TODO needs a locator for the obs to POSTPONE to the query with reversed direction
            // .....(n..)<-[:HAS_OBSERVER]-(observer:Observation {id: ...})
        }

        /*
         * build the final query. For now the relationship is always HAS_CHILD and this only navigates child
         * hierarchies.
         */
        String label = getLabel(resultClass);
        StringBuilder query = new StringBuilder(locator).append("-[:HAS_CHILD]->(n:").append(label);

        if (!queryParameters.isEmpty()) {
            query.append(" {");
            int n = 0;
            for (var key : queryParameters.keySet()) {
                if (n > 0) {
                    query.append(", ");
                }
                query.append(key).append(": $").append(key);
                n++;
            }
            query.append("}");
        }

        queryParameters.put("contextId", scope.getId());
        var result = query(query.append(") return n").toString(), queryParameters, scope);

        return adapt(result, resultClass, scope);
    }

    @Override
    public Agent requireAgent(String agentName) {
        if ("k.LAB".equals(agentName)) {
            return klab;
        } else if (scope.getUser().getUsername().equals(agentName)) {
            return user;
        } else if (agentName != null) {
            // TODO create agent
        }
        return user;
    }

    @Override
    public List<SessionInfo> getSessionInfo(Scope scope) {

        var sessionIds = new LinkedHashMap<String, SessionInfo>();
        EagerResult contexts = switch (scope) {
            case ContextScope contextScope ->
                    query("match(c:Context {id: $contextId}) return c", Map.of("contextId",
                                    contextScope.getId())
                            , scope);
            case SessionScope sessionScope ->
                    query("match (c:Context) WHERE c.id STARTS WITH $sessionId return c", Map.of(
                            "sessionId",
                            sessionScope.getId() + "."), scope);
            case UserScope userScope -> query("match(c:Context {user: $user}) return (c)", Map.of("user",
                    userScope.getUser().getUsername()), scope);
            default -> throw new KlabIllegalStateException("Unexpected value: " + scope);
        };

        List<ContextInfo> contextInfos = new ArrayList<>();
        for (var context : adapt(contexts, Map.class, scope)) {
            ContextInfo contextInfo = new ContextInfo();
            contextInfo.setId(context.get("id").toString());
            contextInfo.setCreationTime((Long)context.get("created"));
            contextInfo.setName(context.get("name").toString());
            contextInfo.setUser(context.get("user").toString());
            contextInfos.add(contextInfo);
        }

        contextInfos.sort(new Comparator<ContextInfo>() {
            @Override
            public int compare(ContextInfo o1, ContextInfo o2) {
                return Long.compare(o1.getCreationTime(), o2.getCreationTime());
            }
        });

        // collect sessions
        for (var context : contextInfos) {
            var sessionId = Utils.Paths.getFirst(context.getId(), ".");
            var sessionInfo = sessionIds.computeIfAbsent(sessionId, (s) -> {
                var ss = new SessionInfo();
                ss.setId(s);
                ss.setUsername(context.getUser());
                return ss;
            });
            sessionInfo.getContexts().add(context);
        }

        return new ArrayList<>(sessionIds.values());
    }
}
