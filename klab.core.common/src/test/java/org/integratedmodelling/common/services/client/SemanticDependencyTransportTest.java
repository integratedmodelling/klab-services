package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.services.ReasonerCapabilitiesImpl;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.junit.jupiter.api.Test;

class SemanticDependencyTransportTest {
  @Test void scopedProofAndResponseOwnedSignatureSessionRoundTripWithoutStampingLegacy() throws Exception {
    var mapper=JacksonConfiguration.newObjectMapper();String base="semantic-content-v2:"+"a".repeat(64);
    var request=new SemanticDependencyRequest("service",2,base,List.of("w:Tree"));
    var witness=new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,SemanticDependencyWitness.Status.COMPLETE,
        "service",2,base,request.inputHash(),List.of(),null);
    assertEquals(request,mapper.readValue(mapper.writeValueAsBytes(request),SemanticDependencyRequest.class));
    assertEquals(witness,mapper.readValue(mapper.writeValueAsBytes(witness),SemanticDependencyWitness.class));
    var response=new SemanticValidationResponse();response.setSemanticSessionId("session");response.setSemanticContentFingerprint(base);
    response.setSemanticObligations(request.obligations());response.setSemanticObligationsComplete(true);response.setSemanticWitness(witness);
    var restored=mapper.readValue(mapper.writeValueAsBytes(response),SemanticValidationResponse.class);
    assertEquals("session",restored.getSemanticSessionId());assertEquals(request.obligations(),restored.getSemanticObligations());
    assertTrue(restored.isSemanticObligationsComplete());assertEquals(witness,restored.getSemanticWitness());
    var legacy=mapper.readValue("{}",SemanticValidationResponse.class);
    assertNull(legacy.getSemanticSessionId());assertNull(legacy.getSemanticWitness());assertFalse(legacy.isSemanticObligationsComplete());
    var capability=new ReasonerCapabilitiesImpl();capability.setSemanticSessionId("owner");
    assertEquals("owner",mapper.readValue(mapper.writeValueAsBytes(capability),ReasonerCapabilitiesImpl.class).getSemanticSessionId());
  }
}
