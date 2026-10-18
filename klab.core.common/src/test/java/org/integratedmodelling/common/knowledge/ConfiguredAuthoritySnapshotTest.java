package org.integratedmodelling.common.knowledge;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.klab.api.services.*;
import org.junit.jupiter.api.Test;

class ConfiguredAuthoritySnapshotTest {
  @Test void stateAndNestedConfigurationAreOwnedAndJsonRoundtrips() throws Exception {
    var nested=new LinkedHashMap<String,Object>();nested.put("selection",new ArrayList<>(List.of("M49")));
    var request=new Authority.ConfigurationRequest("w","UN.M49","geo:Identity",Map.of("urn","provider","options",nested));
    byte[] state={1,2,3};var snapshot=new ConfiguredAuthoritySnapshot("test-v1","provider",request,state);
    state[0]=9;nested.put("other","changed");assertArrayEquals(new byte[]{1,2,3},snapshot.state());
    assertFalse(((Map<?,?>)snapshot.request().parameters().get("options")).containsKey("other"));
    var value=snapshot.state();value[0]=8;assertEquals(1,snapshot.state()[0]);
    var json=new com.fasterxml.jackson.databind.ObjectMapper();
    var restored=json.readValue(json.writeValueAsBytes(snapshot),ConfiguredAuthoritySnapshot.class);
    assertEquals(snapshot.request(),restored.request());assertArrayEquals(snapshot.state(),restored.state());
    assertEquals(snapshot,restored);assertEquals(snapshot.hashCode(),restored.hashCode());
  }

  @Test void boundsAndProviderIdentityAreRequired() {
    var request=new Authority.ConfigurationRequest("w","UN.M49","geo:Identity",Map.of("urn","provider"));
    assertThrows(IllegalArgumentException.class,()->new ConfiguredAuthoritySnapshot("test","other",request,new byte[]{1}));
    assertThrows(IllegalArgumentException.class,()->new ConfiguredAuthoritySnapshot("test","provider",request,new byte[0]));
    assertThrows(IllegalArgumentException.class,()->new ConfiguredAuthoritySnapshot("test","provider",request,new byte[ConfiguredAuthoritySnapshot.MAX_STATE_BYTES+1]));
    var cycle=new HashMap<String,Object>();cycle.put("self",cycle);
    var cyclic=new Authority.ConfigurationRequest("w","UN.M49","geo:Identity",Map.of("urn","provider","nested",cycle));
    assertThrows(IllegalArgumentException.class,()->new ConfiguredAuthoritySnapshot("test","provider",cyclic,new byte[]{1}));
  }
}
