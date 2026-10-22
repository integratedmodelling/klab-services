package org.integratedmodelling.klab.services.runtime.neo4j;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.Test;
import org.neo4j.harness.Neo4jBuilders;

class ObservationPerspectivesScopeTest {
  @Test void serverEnumeratesOnlyExistingDirectChildrenAtRootOrFocusAndNeverTransactionProposals() throws Exception {
    try (var database=Neo4jBuilders.newInProcessBuilder().withDisabledServer()
        .withConfig(org.neo4j.configuration.connectors.BoltConnector.enabled,false).build()) {
      database.defaultDatabaseService().executeTransactionally("""
          CREATE (root:Context {id:'perspectives.context'}), (other:Context {id:'foreign.context'}),
                 (focus:Observation {id:20,urn:'obs:20'}), (sibling:Observation {id:30,urn:'obs:30'}),
                 (a:Observation {id:1,urn:'obs:1'}), (b:Observation {id:2,urn:'obs:2'}),
                 (c:Observation {id:3,urn:'obs:3'}), (d:Observation {id:4,urn:'obs:4'}),
                 (root)-[:HAS_CHILD]->(focus), (root)-[:HAS_CHILD]->(sibling),
                 (root)-[:HAS_CHILD]->(a), (focus)-[:HAS_CHILD]->(b),
                 (sibling)-[:HAS_CHILD]->(c), (other)-[:HAS_CHILD]->(d)
          """);
      var concept=new ConceptImpl();concept.setUrn("test:Height");
      concept.setType(EnumSet.of(SemanticType.QUALITY,SemanticType.OBSERVABLE));
      var observable=new ObservableImpl();observable.setSemantics(concept);observable.setUrn(concept.getUrn());
      var records=new HashMap<Long,Observation>();
      for(long id:List.of(1L,2L,3L,4L,20L,30L)) {
        var record=new ObservationImpl();record.setId(id);record.setUrn("obs:"+id);
        if(id<20)record.setObservable(observable);
        else {var otherObservable=new ObservableImpl();otherObservable.setUrn("test:Subject");otherObservable.setSemantics(concept);record.setObservable(otherObservable);}
        records.put(id,record);
      }
      var graph=mock(KnowledgeGraph.class);
      var twin=mock(DigitalTwin.class);when(twin.getKnowledgeGraph()).thenReturn(graph);
      doAnswer(call->new KnowledgeGraphQuery<Observation>(KnowledgeGraphQuery.AssetType.OBSERVATION) {
        @Override public List<Observation> run(org.integratedmodelling.klab.api.scope.Scope scope) {
          var compiled=Neo4jQueryCompiler.compile(this,"perspectives.context");
          return database.defaultDatabaseService().executeTransactionally(compiled.cypher(),compiled.parameters(),rows->{
            var result=new ArrayList<Observation>();
            while(rows.hasNext())result.add(records.get((Long)((org.neo4j.graphdb.Node)rows.next().get("n")).getProperty("id")));
            return result;
          });
        }
      }).when(graph).query(eq(Observation.class),any());
      for(Long focus:Arrays.asList(null,20L,30L)) {
        var scope=mock(ServiceContextScope.class,CALLS_REAL_METHODS);
        doReturn("perspectives.context").when(scope).getId();
        doReturn(focus==null?null:records.get(focus)).when(scope).getContextObservation();
        scope.setDigitalTwin(twin);
        var reasoner=mock(Reasoner.class);doReturn(reasoner).when(scope).getService(Reasoner.class);
        when(reasoner.buildObservable(any(),same(scope))).thenAnswer(call->{
          org.integratedmodelling.klab.api.knowledge.ObservableBuildStrategy builder=call.getArgument(0);
          return builder.getBaseObservable(); // Absolute fixtures: typed removal is an identity operation.
        });
        assertEquals(List.of(focus==null?1L:focus==20L?2L:3L),
            scope.<Observation>getPerspectives(observable).stream().map(Observation::getId).toList());
        verify(scope,never()).getCurrentTransaction();
      }
    }
  }
}
