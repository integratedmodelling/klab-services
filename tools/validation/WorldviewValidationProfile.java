import java.nio.file.*;
import java.util.*;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.services.reasoner.internal.*;

/** Run with the Reasoner distribution classpath and a Resources list/WORLDVIEW JSON snapshot. */
class WorldviewValidationProfile {
  public static void main(String[] args) throws Exception {
    var mapper = JacksonConfiguration.newObjectMapper();
    String json = Files.readString(Path.of(args[0]));
    boolean semanticOnly = args.length > 3 && args[3].equals("semantic-only");
    if (semanticOnly) json = json.replace("CHEM:CID_962", "chemistry:ChemicalIdentity")
        .replace("TAXA:[6MB3T]", "life:TaxonomicIdentity");
    var world = mapper.readValue(json, Worldview[].class)[0];
    if (semanticOnly) {
      // Explicit profiling fixture: exclude provider discovery and external identity resolution.
      // External identities above become local roots; authored applicability clauses remain intact.
      world.getNotifications().clear();
      ((org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl)world).setAuthorityBindings(List.of());
      for (var statement : world.allConceptStatements())
        ((org.integratedmodelling.klab.api.lang.kim.impl.KimConceptStatementImpl)statement).setAuthorityRequired(null);
    }
    org.integratedmodelling.klab.api.scope.Scope owner = null;
    if (args.length > 3 && args[3].equals("live-owner")) {
      var auth = org.integratedmodelling.common.authentication.Authentication.INSTANCE;
      var identity = auth.authenticate(org.integratedmodelling.common.services.client.engine.SettingsImpl.forServiceOwner(
          org.integratedmodelling.klab.api.services.KlabService.Type.REASONER));
      var scope = new org.integratedmodelling.klab.services.UserServiceScope(
          (org.integratedmodelling.klab.api.identities.UserIdentity)identity.getFirst(),
          org.integratedmodelling.klab.api.services.KlabService.Type.REASONER, identity.getSecond());
      owner = scope;
      long deadline = System.nanoTime() + java.time.Duration.ofSeconds(45).toNanos();
      while (scope.getServices(org.integratedmodelling.klab.api.services.ResourcesService.class).isEmpty()
          && System.nanoTime() < deadline) Thread.sleep(250);
      var services = scope.getServices(org.integratedmodelling.klab.api.services.ResourcesService.class);
      System.out.println("PROFILE resourceServices=" + services.stream().map(svc -> svc.getUrl().toString()).toList());
      var local = services.stream().filter(svc -> svc.isLocal()).findFirst().orElseThrow();
      long discovery = System.nanoTime();
      world = local.list(Worldview.class, null).getFirst();
      System.out.println("PROFILE discoveryMs=" + (System.nanoTime()-discovery)/1_000_000
          + " bindings=" + world.getAuthorityBindings().stream().map(b -> b.localId()).toList());
      mapper.writeValue(Path.of(args[1]).resolveSibling("authenticated-worldview.json").toFile(), new Worldview[]{world});
    }
    var factory = SemanticEnvironmentFactory.fromLocalCatalog(Path.of(args[1]), Path.of(args[2]));
    factory.enableAuthorityBootstrap();
    var cpu = java.lang.management.ManagementFactory.getThreadMXBean();
    long cpuStarted = cpu.getCurrentThreadCpuTime();
    long started = System.nanoTime();
    var input = factory.capture(world, owner);
    System.out.println("PROFILE captureMs=" + (System.nanoTime()-started)/1_000_000
        + " beanStatus=" + input.beanStatus() + " diagnostics=" + input.diagnostics());
    if (input.beanStatus() != CandidatePublicationCoordinator.BeanStatus.VALID) return;
    started = System.nanoTime();
    cpuStarted = cpu.getCurrentThreadCpuTime();
    var result = factory.build(input, () -> false);
    System.out.println("PROFILE buildMs=" + (System.nanoTime()-started)/1_000_000
        + " cpuMs=" + (cpu.getCurrentThreadCpuTime()-cpuStarted)/1_000_000
        + " status=" + result.logicalStatus() + " diagnostics=" + result.diagnostics());
    if (result.state() != null) {
      System.out.println("PROFILE metrics=" + result.state().actor().owl().compilationMetrics());
      result.state().close();
    }
    if (owner != null) owner.close();
  }
}
