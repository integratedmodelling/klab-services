//package org.integratedmodelling.klab.services.runtime.neo4j;
//
//import org.integratedmodelling.klab.api.data.KnowledgeGraph;
//import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
//
//import java.nio.file.Path;
//
///**
// * Concrete test running the KnowledgeGraphNeo4j contract on the Embedded implementation.
// */
//public class KnowledgeGraphNeo4JEmbeddedTest extends KnowledgeGraphNeo4jContractTest {
//
//  @Override
//  protected KnowledgeGraph createGraph(Path dbPath) throws Exception {
//    // Use the embedded implementation with a temporary directory
//    return new KnowledgeGraphNeo4JEmbedded(dbPath);
//  }
//}
