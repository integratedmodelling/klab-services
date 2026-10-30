import java.util.*;
import org.integratedmodelling.klab.authority.pubchem.PubChemAuthority;
import org.integratedmodelling.klab.api.services.Authority;

public class InsertionBenchmark {
  public static void main(String[] args) {
    var provider = new PubChemAuthority();
    var config = provider.configure(new Authority.ConfigurationRequest("benchmark", "CHEM", "chemistry:Identity",
        Map.of("urn", PubChemAuthority.URN, "gifDepictions", false)));
    var graph = new TreeMap<String, List<String>>();
    long start = System.nanoTime();
    try {
      collect(provider, config, args[0], graph);
      System.out.println("totalMs=" + (System.nanoTime() - start) / 1_000_000 + " nodes=" + graph.size());
      graph.forEach((id, parents) -> System.out.println(id + " -> " + parents));
    } finally { provider.releaseConfiguration(config); }
  }
  static void collect(Authority provider, String config, String id, Map<String, List<String>> graph) {
    if (id.equals("chemistry:Identity") || graph.containsKey(id)) return;
    long started = System.nanoTime();
    var identity = provider.resolveIdentity(config, id);
    System.err.println("lookup " + id + " ms=" + (System.nanoTime() - started) / 1_000_000);
    if (identity == null || identity.getNotifications().stream().anyMatch(n -> n.getLevel()
        == org.integratedmodelling.klab.api.services.runtime.Notification.Level.Error))
      throw new IllegalStateException("Lookup failed for " + id);
    var parents = new TreeSet<String>(identity.getParentIds());
    if (identity.getBaseIdentity() != null) parents.add(identity.getBaseIdentity());
    graph.put(id, List.copyOf(parents));
    for (var parent : parents) collect(provider, config, parent, graph);
  }
}
