import org.neo4j.driver.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Read-only local prerequisite check. Never creates layers, contexts, sessions or Jobs. */
public class ModelerSpatialReadCheck {
  public static void main(String[] args) {
    var config = Config.builder().withConnectionTimeout(3, TimeUnit.SECONDS)
        .withMaxTransactionRetryTime(0, TimeUnit.SECONDS).build();
    try (var driver = GraphDatabase.driver("bolt://127.0.0.1:7687", AuthTokens.none(), config);
        var session = driver.session(SessionConfig.builder().withDefaultAccessMode(AccessMode.READ).build())) {
      Set<String> names = new HashSet<>();
      for (var row : session.run("SHOW PROCEDURES YIELD name WHERE name STARTS WITH 'spatial.' RETURN name").list())
        names.add(row.get("name").asString());
      boolean required = names.containsAll(Set.of("spatial.layers", "spatial.addLayer", "spatial.addNode", "spatial.removeLayer"));
      long layers = session.run("CALL spatial.layers() YIELD name RETURN count(name) AS count")
          .single().get("count").asLong();
      System.out.println("{\"procedure_count\":" + names.size() + ",\"required\":" + required
          + ",\"layers_read\":true,\"layer_count\":" + layers + "}");
    } catch (Exception failure) {
      // Exception text may contain database/user details. The class is sufficient for this gate.
      System.out.println("{\"error_type\":\"" + failure.getClass().getSimpleName() + "\"}");
      System.exit(3);
    }
  }
}
