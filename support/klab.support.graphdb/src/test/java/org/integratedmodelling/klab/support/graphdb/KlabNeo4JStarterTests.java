package org.integratedmodelling.klab.support.graphdb;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class KlabNeo4JStarterTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointIsUp() throws Exception {
        mockMvc.perform(get("/health").with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void capabilitiesExposesBoltUrl() throws Exception {
        mockMvc.perform(get("/capabilities").with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.neo4j.boltUrl", containsString("bolt://")));
    }

    @Test
    void nonLocalhostCallIsRejected() throws Exception {
        mockMvc.perform(get("/health").with(request -> {
                    request.setRemoteAddr("10.1.2.3");
                    return request;
                }))
                .andExpect(status().isForbidden());
    }
}
