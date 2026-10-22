package org.integratedmodelling.klab.services.runtime.neo4j;

import static org.mockito.Mockito.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.knowledge.observation.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.runtime.DependencyFamily;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.neo4j.driver.*;
import org.neo4j.harness.*;

/** Embedded transport bridge only: all stored properties, links and restoration use production KG. */
public final class NativeFamilyRecovery implements AutoCloseable {
  private final Neo4j database=Neo4jBuilders.newInProcessBuilder().withDisabledServer()
      .withConfig(org.neo4j.configuration.GraphDatabaseSettings.transaction_timeout,java.time.Duration.ofSeconds(60))
      .withConfig(org.neo4j.configuration.GraphDatabaseSettings.lock_acquisition_timeout,java.time.Duration.ofSeconds(60))
      .withConfig(org.neo4j.configuration.connectors.BoltConnector.enabled,false).build();
  private String contextId="family.native";
  private final AtomicLong sequence=new AtomicLong(10000);
  private final Driver driver=mock(Driver.class);
  public NativeFamilyRecovery() {
    database.defaultDatabaseService().executeTransactionally("CREATE (:Context {id:'family.native'})");
    when(driver.session()).thenAnswer(ignored->{var session=mock(org.neo4j.driver.Session.class);
      when(session.beginTransaction(any(TransactionConfig.class))).thenAnswer(config->transaction());when(session.beginTransaction()).thenAnswer(config->transaction());return session;});
  }
  private Value value(Object raw) {
    if(raw instanceof org.neo4j.graphdb.Node n) {
      var properties=new LinkedHashMap<String,Value>();n.getAllProperties().forEach((key,item)->properties.put(key,value(item)));
      var labels=new ArrayList<String>();n.getLabels().forEach(label->labels.add(label.name()));
      return new org.neo4j.driver.internal.InternalNode(0,n.getElementId(),labels,properties).asValue();
    }
    if(raw instanceof org.neo4j.graphdb.Relationship r) {
      var properties=new LinkedHashMap<String,Value>();r.getAllProperties().forEach((key,item)->properties.put(key,value(item)));
      return new org.neo4j.driver.internal.InternalRelationship(0,0,0,r.getType().name(),properties).asValue();
    }
    return Values.value(raw);
  }
  private List<org.neo4j.driver.Record> records(org.neo4j.graphdb.Result result) {
    var records=new ArrayList<org.neo4j.driver.Record>();
    while(result.hasNext()) {
      var row=result.next();var keys=new ArrayList<>(row.keySet());var values=keys.stream().map(key->value(row.get(key))).toList();
      records.add(new org.neo4j.driver.internal.InternalRecord(keys,values.toArray(Value[]::new)));
    }
    return records;
  }
  private org.neo4j.driver.Transaction transaction() {
    var embedded=database.defaultDatabaseService().beginTx();var tx=mock(org.neo4j.driver.Transaction.class);var open=new java.util.concurrent.atomic.AtomicBoolean(true);
    when(tx.isOpen()).thenAnswer(ignored->open.get());
    when(tx.run(anyString(),anyMap())).thenAnswer(call->{
      List<org.neo4j.driver.Record> rows;try(var result=embedded.execute(call.getArgument(0),call.getArgument(1))){rows=records(result);}
      var result=mock(org.neo4j.driver.Result.class);when(result.list()).thenReturn(rows);when(result.hasNext()).thenReturn(!rows.isEmpty());if(!rows.isEmpty()){when(result.single()).thenReturn(rows.getFirst());when(result.next()).thenReturn(rows.getFirst());}return result;
    });
    doAnswer(ignored->{if(open.getAndSet(false)){embedded.commit();embedded.close();}return null;}).when(tx).commit();
    doAnswer(ignored->{if(open.getAndSet(false)){embedded.rollback();embedded.close();}return null;}).when(tx).rollback();
    // Driver close rolls back an uncommitted transaction, including constructor failures.
    doAnswer(ignored->{if(open.getAndSet(false)){embedded.rollback();embedded.close();}return null;}).when(tx).close();
    return tx;
  }
  private final class Graph extends KnowledgeGraphNeo4j {
    Graph(){this.driver=NativeFamilyRecovery.this.driver;this.rootContextId=contextId;}
    void initialize(ServiceContextScope scope) {
      this.userScope=scope;this.serviceId="runtime";
      this.klab=getOrCreateAgent("k.LAB","AI");this.user=getOrCreateAgent("tester","USER");
      var params=new LinkedHashMap<String,Object>();
      params.put(GraphModel.Fields.CONTEXT_ID,contextId);params.put(GraphModel.Fields.NAME,"embedded root fixture");
      params.put(GraphModel.Fields.USERNAME,"tester");params.put(GraphModel.Fields.TIMESTAMP,System.currentTimeMillis());
      params.put(GraphModel.Fields.RIGHTS,"");params.put(GraphModel.Fields.FEDERATION,"");params.put(GraphModel.Fields.DESCRIPTION,"");
      params.put(GraphModel.Fields.LAST_UPDATE,System.currentTimeMillis());params.put(GraphModel.Fields.EXPIRATION_TYPE,"EXPLICIT_ACTION");params.put(GraphModel.Fields.ACTIVITY_ID,nextKey());
      for(var query:Queries.INITIALIZATION_QUERIES)execute(query,params);
    }
    public long nextKey(){return sequence.incrementAndGet();}
    public KnowledgeGraph contextualize(DigitalTwin.Configuration c,UserScope s){throw new UnsupportedOperationException();}
    public KnowledgeGraph merge(URL url){throw new UnsupportedOperationException();}
    public boolean isOnline(){return true;}public void shutdown(){}
    protected synchronized EagerResult query(String cypher,Map<String,Object> parameters,Scope scope) {
      try(var tx=database.defaultDatabaseService().beginTx();var result=tx.execute(cypher,parameters==null?Map.of():parameters)) {
        var rows=records(result);tx.commit();var eager=mock(EagerResult.class);when(eager.records()).thenReturn(rows);return eager;
      }
    }
  }
  private ServiceContextScope scope(Graph graph,ContextScope original) throws Exception {
    var scope=mock(ServiceContextScope.class,CALLS_REAL_METHODS);var twin=mock(DigitalTwin.class);var storage=mock(StorageManager.class);
    when(storage.finalizeStorage(anyLong(),anyLong())).thenReturn(true);when(twin.getStorageManager()).thenReturn(storage);when(twin.getKnowledgeGraph()).thenReturn(graph);
    doReturn(twin).when(scope).getDigitalTwin();doReturn(null).when(scope).getCurrentTransaction();doReturn(false).when(scope).isInterrupted();
    doReturn(original.getService(Reasoner.class)).when(scope).getService(Reasoner.class);
    doReturn(original.getService(RuntimeService.class)).when(scope).getService(RuntimeService.class);
    doAnswer(i->graph.getAsset(i.getArgument(0,Long.class).longValue(),scope,Observation.class)).when(scope).getObservation(anyLong());return scope;
  }
  public record Recovered(ServiceContextScope scope,Observation target,List<DependencyFamily.Input> inputs,Map<Long,Long> persistedIds) {}
  public Recovered persist(Observation parent,Observation nativeContext,List<DependencyFamily.Input> inputs,ContextScope original) throws Exception {
    return persist(parent,nativeContext,inputs,original,false);
  }
  public Recovered persist(Observation parent,Observation nativeContext,List<DependencyFamily.Input> inputs,ContextScope original,boolean abort) throws Exception {
    var mapper=JacksonConfiguration.newObjectMapper();var graph=new Graph();var scope=scope(graph,original);
    var context=mapper.readValue(mapper.writeValueAsString(nativeContext),Observation.class);
    var target=mapper.readValue(mapper.writeValueAsString(parent),Observation.class);
    var sources=new LinkedHashMap<Long,Observation>();
    for(var input:inputs)sources.putIfAbsent(input.observation().getId(),mapper.readValue(mapper.writeValueAsString(input.observation()),Observation.class));
    try(var tx=graph.createTransaction(scope)) {
      tx.store(context);((ObservationImpl)target).setParentId(context.getId());tx.store(target);tx.link(context,target,GraphModel.Relationship.HAS_CHILD);
      for(var source:sources.values()){((ObservationImpl)source).setParentId(context.getId());tx.store(source);tx.link(context,source,GraphModel.Relationship.HAS_CHILD);}
      for(var input:inputs)tx.link(sources.get(input.observation().getId()),target,GraphModel.Relationship.AFFECTS,
          DependencyFamily.Binding.PROPERTY,org.integratedmodelling.common.utils.Utils.Json.asString(new DependencyFamily.Binding(input.family().id(),input.branch().id(),input.family().rootAttempt(),input.branch().observable())),
          StorageScan.Binding.PROPERTY,org.integratedmodelling.common.utils.Utils.Json.asString(input.storageBinding()),ProcessPlan.EDGE_ROLE,"PREREQUISITE","readState","CURRENT");
      if(abort)tx.fail(new IllegalStateException("fixture native rollback"));
    }
    if(abort)return null;
    var cold=new Graph();var coldScope=scope(cold,original);var restored=cold.getAsset(target.getId(),coldScope,Observation.class);
    var recovered=new ArrayList<DependencyFamily.Input>();for(var family:coldScope.getDependencyFamilies(restored))recovered.addAll(coldScope.getDependencyInputs(restored,family.id()));
    var ids=new LinkedHashMap<Long,Long>();sources.forEach((before,after)->ids.put(before,after.getId()));return new Recovered(coldScope,restored,List.copyOf(recovered),Map.copyOf(ids));
  }
  /** Install BEFORE submission: all graph operations remain production native operations. */
  public KnowledgeGraphNeo4j initializeRoot(ServiceContextScope scope) {
    contextId=scope.getId();var graph=new Graph();graph.initialize(scope);return graph;
  }
  public ServiceContextScope coldScope(ContextScope original) throws Exception {return scope(new Graph(),original);}
  public KnowledgeGraphNeo4j coldGraph() { return new Graph(); }
  public void execute(String cypher){database.defaultDatabaseService().executeTransactionally(cypher);}
  public void execute(String cypher,Map<String,Object> parameters){database.defaultDatabaseService().executeTransactionally(cypher,parameters);}
  public Storage.Shard standaloneShard(Storage.Shard shard,ContextScope original) {
    var graph=new Graph();graph.store(shard,original);return new Graph().getAsset(shard.getId(),original,Storage.Shard.class);
  }
  public long children(long id){return database.defaultDatabaseService().executeTransactionally("MATCH (:Observation {id:$id})-[:HAS_CHILD]->() RETURN count(*) AS n",Map.of("id",id),result->(Long)result.next().get("n"));}
  public long observations(){return database.defaultDatabaseService().executeTransactionally("MATCH (n:Observation) RETURN count(n) AS n",Map.of(),result->(Long)result.next().get("n"));}
  public void close(){database.close();}
}
