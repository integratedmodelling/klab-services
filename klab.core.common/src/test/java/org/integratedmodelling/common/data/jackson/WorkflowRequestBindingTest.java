package org.integratedmodelling.common.data.jackson;

import static org.junit.jupiter.api.Assertions.*;
import org.integratedmodelling.klab.api.services.resources.workflow.Flow;
import org.integratedmodelling.klab.api.services.resources.workflow.impl.FlowImpl;
import org.integratedmodelling.common.utils.Utils;
import org.junit.jupiter.api.Test;

class WorkflowRequestBindingTest {
  @Test void plainBrowserAttachmentAndInitializationRequestsBindDeclaredImplementations() throws Exception {
    var mapper = JacksonConfiguration.newObjectMapper();
    var upload = mapper.readValue("""
        {"type":"bootstrap-proposal","fileName":"r2.json","mediaType":"application/vnd.klab.proposal+yaml","content":"AQID"}
        """, Flow.AttachmentUpload.class);
    assertInstanceOf(FlowImpl.AttachmentUploadImpl.class, upload); assertArrayEquals(new byte[]{1, 2, 3}, upload.getContent());
    assertEquals("r2.json", upload.getFileName());
    var initialization = mapper.readValue("""
        {"initialState":{"schemaId":"editing","assetUrn":"test.ontology","assetType":"ONTOLOGY","metadata":{"context":"preserved"}},
         "attachments":[{"type":"supporting-material","fileName":"context.txt","mediaType":"text/plain","content":"AQID"}]}
        """, Flow.InitializationRequest.class);
    assertInstanceOf(FlowImpl.InitializationRequestImpl.class, initialization);
    assertEquals("preserved", initialization.getInitialState().getMetadata().get("context"));
    assertArrayEquals(new byte[]{1, 2, 3}, initialization.getAttachments().getFirst().getContent());
  }
  @Test void taggedJavaRequestsRoundTripAndUnrelatedClassMarkersAreRejected() throws Exception {
    var upload = Flow.AttachmentUpload.create(); upload.setType("bootstrap-proposal"); upload.setFileName("r2.json");
    upload.setMediaType("application/vnd.klab.proposal+yaml"); upload.setContent(new byte[]{1, 2, 3});
    var restored = Utils.Json.parseObject(Utils.Json.asString(upload), Flow.AttachmentUpload.class);
    assertEquals(upload.getFileName(), restored.getFileName()); assertArrayEquals(upload.getContent(), restored.getContent());
    var mapper = JacksonConfiguration.newObjectMapper();
    assertThrows(Exception.class, () -> mapper.readValue("{\"@CLASS\":\"java.util.LinkedHashMap\"}", Flow.AttachmentUpload.class));
  }
}
