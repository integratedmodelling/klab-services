package org.integratedmodelling.klab.services.resources;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resources.ModelDiscoveryMode;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.junit.jupiter.api.Test;

class ModelDiscoveryModeQueryTest {
  @Test void transportedParameterRoutesExplicitPreparationAndMissingModeRetainsExecutableDefault() throws Exception {
    var provider=mock(ResourcesProvider.class,CALLS_REAL_METHODS);var scope=mock(ContextScope.class);
    var observable=mock(Observable.class);var geometry=mock(Geometry.class);var result=new ResourceSet();
    doReturn(result).when(provider).resolveModels(eq(observable),eq(geometry),isNull(),eq(List.of()),eq(scope),any());
    // Query parameters carry enum names, not Java-only enum objects.
    var mapper=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    var wire=mapper.readValue(mapper.writeValueAsBytes(Map.of(ModelDiscoveryMode.PARAMETER,ModelDiscoveryMode.PREPARABLE.name())),Map.class);
    var query=Parameters.<String>create("observable",observable,"geometry",geometry,ModelDiscoveryMode.PARAMETER,wire.get(ModelDiscoveryMode.PARAMETER));
    assertEquals(List.of(result),provider.query(query,KlabAsset.KnowledgeClass.MODEL,ResourceSet.class,scope));
    verify(provider).resolveModels(observable,geometry,null,List.of(),scope,ModelDiscoveryMode.PREPARABLE);
    query.remove(ModelDiscoveryMode.PARAMETER);
    assertEquals(List.of(result),provider.query(query,KlabAsset.KnowledgeClass.MODEL,ResourceSet.class,scope));
    verify(provider).resolveModels(observable,geometry,null,List.of(),scope,ModelDiscoveryMode.EXECUTABLE);
    query.put(ModelDiscoveryMode.PARAMETER,"UNKNOWN");
    assertThrows(IllegalArgumentException.class,()->provider.query(query,KlabAsset.KnowledgeClass.MODEL,ResourceSet.class,scope));
    verify(provider,times(2)).resolveModels(eq(observable),eq(geometry),isNull(),eq(List.of()),eq(scope),any());
  }
}
