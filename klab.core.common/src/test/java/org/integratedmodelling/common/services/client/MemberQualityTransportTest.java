package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.knowledge.SingularContextBinding;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.services.runtime.objects.*;
import org.junit.jupiter.api.Test;

class MemberQualityTransportTest {
  @Test void typedInputPlanSurvivesActualActuatorInterfaceTransport() throws Exception {
    var value=new org.integratedmodelling.common.runtime.ActuatorImpl();
    var request=new MemberQualityRequest(new SingularContextBinding(10,"region:10","test:Region",
        "t0(1)","test:Gradient","reasoner",7),"each test:Stream","test:Gradient");
    var plan=new org.integratedmodelling.klab.api.services.runtime.MemberQualityInputPlan("test.mapper","test","1","0".repeat(64),
        0,"gradients","test:Gradient of test:Stream",request);plan.validate();
    value.getMemberQualityInputs().put("gradients",plan);
    var mapper=JacksonConfiguration.newObjectMapper();
    var copy=mapper.readValue(mapper.writeValueAsBytes(value),org.integratedmodelling.klab.api.services.runtime.Actuator.class);
    assertEquals(plan,copy.getMemberQualityInputs().get("gradients"));
    assertTrue(copy.getChildren().isEmpty(),"The collection is a port, not a fake child Observation");
  }
  @Test void interfaceCollectionAndNestedObservationIdentitiesRoundTrip() throws Exception {
    var value=new MemberQualityCollectionImpl();
    value.setRequest(new MemberQualityRequest(new SingularContextBinding(10,"region:10","test:Region",
        "t0(1)","test:Gradient","reasoner",7),"each test:Stream","test:Gradient"));
    var member=new ObservationImpl();member.setId(11);member.setUrn("stream:11");
    var quality=new ObservationImpl();quality.setId(12);quality.setUrn("quality:12");quality.setParentId(11);
    value.getEntries().add(new MemberQualityCollectionImpl.EntryImpl(member,quality));
    var mapper=JacksonConfiguration.newObjectMapper();
    var bytes=mapper.writeValueAsBytes(value);
    var copy=mapper.readValue(bytes,MemberQualityCollection.class);
    assertTrue(new String(bytes,java.nio.charset.StandardCharsets.UTF_8).contains("@CLASS"));
    assertEquals(value.getRequest(),copy.getRequest());
    assertEquals(11,copy.getEntries().getFirst().getMember().getId());
    assertEquals(11,copy.getEntries().getFirst().getQuality().getParentId());
    assertEquals(12,copy.getEntries().getFirst().getQuality().getId());
    assertNull(copy.getObserverId());
  }
}
