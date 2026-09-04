package org.integratedmodelling.klab.services.review;

import java.net.http.*;
import java.net.URI;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Publish to Zenodo and obtain a DOI. At the moment Zenodo remains the only DOI client that enables
 * free access.
 *
 * <p>Configure and add to Workflow instrumentation when ready.
 */
public class ZenodoDOIClient {

  private static final String BASE_URL = "https://zenodo.org/api";
  private final String token;
  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper mapper = new ObjectMapper();

  public ZenodoDOIClient(String accessToken) {
    this.token = accessToken;
  }

  /** Create a deposition — Zenodo auto-reserves a DOI and returns it. */
  public String createDeposition(String title, String creatorName, String uploadType)
      throws Exception {
    String json =
        """
            {
              "metadata": {
                "title": "%s",
                "upload_type": "%s",
                "creators": [{"name": "%s"}]
              }
            }
            """
            .formatted(title, uploadType, creatorName);

    HttpRequest req =
        HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/deposit/depositions"))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

    HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
    JsonNode root = mapper.readTree(res.body());
    return root.get("id").asText(); // deposition ID
  }

  /** Publish the deposition — the reserved DOI becomes active (registered with DataCite). */
  public String publishDeposition(String depositionId) throws Exception {
    HttpRequest req =
        HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/deposit/depositions/" + depositionId + "/actions/publish"))
            .header("Authorization", "Bearer " + token)
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();

    HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
    JsonNode root = mapper.readTree(res.body());
    return root.get("metadata").get("doi").asText(); // e.g. "10.5281/zenodo.12345678"
  }

  public static void main(String[] args) throws Exception {
    var client = new ZenodoDOIClient(System.getenv("ZENODO_TOKEN"));
    // don't run with a real token or you'll publish bull
    String id = client.createDeposition("My Dataset", "Doe, Jane", "dataset");
    String doi = client.publishDeposition(id);
    System.out.println("DOI: https://doi.org/" + doi);
  }
}
