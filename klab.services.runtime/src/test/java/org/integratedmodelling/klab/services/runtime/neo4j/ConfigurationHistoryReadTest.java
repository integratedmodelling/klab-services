package org.integratedmodelling.klab.services.runtime.neo4j;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.URL;
import java.util.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.*;
import org.neo4j.driver.*;
import org.neo4j.harness.*;

/** Production authorized query against an isolated real native store; never issues receipts. */
class ConfigurationHistoryReadTest {
  static Neo4j database;
  @BeforeAll static void start(){database=Neo4jBuilders.newInProcessBuilder().withDisabledServer()
      .withConfig(org.neo4j.configuration.connectors.BoltConnector.enabled,false).build();}
  @AfterAll static void stop(){database.close();}
  static void write(String query,Map<String,Object> parameters) {
    try(var tx=database.defaultDatabaseService().beginTx()){tx.execute(query,parameters).close();tx.commit();}
  }
  static String metadata(String context,long focus) {
    return metadata(context,41,0,focus,1);
  }
  static String metadata(String context,Object id,Object observer,Object focus,Object revision) {
    return Utils.Json.asString(Map.of("klab.configuration.current",Utils.Json.asString(Map.of("observationId",id,"number",revision)),
        "klab.configuration.perspective",Utils.Json.asString(Map.of("contextId",context,"observerId",observer,"focusId",focus))));
  }
  @BeforeEach void seed() {
    write("MATCH(n) DETACH DELETE n",Map.of());
    write("CREATE (a:Context{id:'a'}),(b:Context{id:'b'}),"
        +"(x:Observation{id:41,observable:'test:Pair',ownerContextId:'a',_metadata:$metadata,configurationHistory:$history}),"
        +"(y:Observation{id:42,observable:'test:Pair',ownerContextId:'b',_metadata:$metadata}),"
        +"(a)-[:HAS_CHILD]->(x),(b)-[:HAS_CHILD]->(y)",Map.of("metadata",metadata("a",0),"history",List.of("original-beforeimage")));
  }
  static class Fixture extends KnowledgeGraphNeo4j {
    final Driver readDriver=mock(Driver.class);final Session readSession=mock(Session.class);
    Fixture() {
      driver=readDriver;rootContextId="a";
      when(readDriver.session(any(SessionConfig.class))).thenReturn(readSession);
      when(readSession.executeRead(any())).thenAnswer(call->{
        try(var nativeTx=database.defaultDatabaseService().beginTx()) {
          var context=mock(TransactionContext.class);
          when(context.run(anyString(),anyMap())).thenAnswer(run->{
            var records=new ArrayList<org.neo4j.driver.Record>();
            try(var nativeResult=nativeTx.execute(run.getArgument(0),run.getArgument(1))) {
              while(nativeResult.hasNext()) {
                var nativeRow=nativeResult.next();var record=mock(org.neo4j.driver.Record.class);
                when(record.get(anyString())).thenAnswer(key->{
                  var object=nativeRow.get(key.getArgument(0));
                  if(object instanceof org.neo4j.graphdb.Node n) {
                    var node=mock(org.neo4j.driver.types.Node.class);
                    when(node.get(anyString())).thenAnswer(field->Values.value(n.getProperty(field.getArgument(0),null)));
                    var value=mock(Value.class);when(value.asNode()).thenReturn(node);return value;
                  }
                  return Values.value(object);
                });records.add(record);
              }
            }
            var result=mock(Result.class);when(result.list()).thenReturn(records);return result;
          });
          return ((TransactionCallback<?>)call.getArgument(0)).execute(context);
        }
      });
    }
    public KnowledgeGraph contextualize(DigitalTwin.Configuration configuration,UserScope scope){throw new UnsupportedOperationException();}
    public KnowledgeGraph merge(URL url){throw new UnsupportedOperationException();}
    public boolean isOnline(){return true;}
    public void shutdown(){}
    ContextScope scope(String id){var scope=mock(ContextScope.class);when(scope.getId()).thenReturn(id);return scope;}
    Observation configuration(long id){var value=new ObservationImpl();value.setId(id);var meaning=mock(Observable.class);
      when(meaning.getUrn()).thenReturn("test:Pair");when(meaning.is(SemanticType.CONFIGURATION)).thenReturn(true);value.setObservable(meaning);return value;}
  }
  @Test void historyIsAnImmutableDetachedReadAndUsesReadRouting() {
    var f=new Fixture();var before=f.getConfigurationHistory(f.configuration(41),f.scope("a"));
    assertEquals(List.of("original-beforeimage"),before);assertThrows(UnsupportedOperationException.class,()->before.add("forged"));
    write("MATCH(n:Observation{id:41}) SET n.configurationHistory=n.configurationHistory+['later']",Map.of());
    assertEquals(List.of("original-beforeimage"),before);
    assertEquals(List.of("original-beforeimage","later"),f.getConfigurationHistory(f.configuration(41),f.scope("a")));
    var config=org.mockito.ArgumentCaptor.forClass(SessionConfig.class);verify(f.readDriver,times(2)).session(config.capture());
    assertTrue(config.getAllValues().stream().allMatch(c->c.defaultAccessMode()==AccessMode.READ));
  }
  @Test void missingAndForeignContextObservationsDoNotLeakHistory() {
    var f=new Fixture();assertThrows(RuntimeException.class,()->f.getConfigurationHistory(f.configuration(99),f.scope("a")));
    assertThrows(RuntimeException.class,()->f.getConfigurationHistory(f.configuration(42),f.scope("a")));
    assertThrows(IllegalArgumentException.class,()->f.getConfigurationHistory(f.configuration(41),f.scope("b")));
  }
  @Test void missingHistoryIsEmptyButAnotherPerspectiveOrOwnerIsRejected() {
    var f=new Fixture();write("MATCH(n:Observation{id:41}) REMOVE n.configurationHistory",Map.of());
    assertTrue(f.getConfigurationHistory(f.configuration(41),f.scope("a")).isEmpty());
    write("MATCH(n:Observation{id:41}) SET n._metadata=$metadata",Map.of("metadata",metadata("a",9)));
    assertThrows(SecurityException.class,()->f.getConfigurationHistory(f.configuration(41),f.scope("a")));
    write("MATCH(n:Observation{id:41}) SET n._metadata=$metadata,n.ownerContextId='b'",Map.of("metadata",metadata("a",0)));
    assertThrows(SecurityException.class,()->f.getConfigurationHistory(f.configuration(41),f.scope("a")));
  }
  @Test void fractionalNativeIdentityAndMetadataIntegersCannotBeTruncated() {
    var f=new Fixture();
    for(var malformed:List.of(metadata("a",41.5,0,0,1),metadata("a",41,0.5,0,1),
        metadata("a",41,0,0.5,1),metadata("a",41,0,0,1.5),metadata("a",41.0,0,0,1))) {
      write("MATCH(n:Observation{id:41}) SET n._metadata=$metadata",Map.of("metadata",malformed));
      assertThrows(RuntimeException.class,()->f.getConfigurationHistory(f.configuration(41),f.scope("a")));
    }
    write("MATCH(n:Observation{id:41}) SET n._metadata=$metadata,n.id=41.0",Map.of("metadata",metadata("a",0)));
    assertThrows(RuntimeException.class,()->f.getConfigurationHistory(f.configuration(41),f.scope("a")));
  }
}
