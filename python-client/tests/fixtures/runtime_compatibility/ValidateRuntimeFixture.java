import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.StringReader;
import java.nio.file.*;
import java.util.*;
import org.eclipse.emf.ecore.*;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.languages.*;
import org.integratedmodelling.languages.api.ParsedObject;
import org.integratedmodelling.languages.worldview.Ontology;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.resources.FileProjectStorage;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.runtime.language.KimObservableVisitor;
import org.integratedmodelling.klab.runtime.language.WorldviewDeclarationRegistry;
import org.integratedmodelling.klab.runtime.language.LanguageAdapter;
import org.integratedmodelling.klab.runtime.language.WorldviewValidationScope;
import org.jgrapht.graph.*;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.traverse.TopologicalOrderIterator;

/** Offline native parser/adapter/visitor/registry checks; never operational or Job acceptance.
 * Reuses the earlier controlled-fixture dependency-order probe, adds explicit whole-worldview
 * declaration checks, and accepts only read-only input and a new result file.
 */
public class ValidateRuntimeFixture {
  public static void main(String[] args) throws Exception {
    if (args.length != 2) throw new IllegalArgumentException("Require fixture projects and a new result file");
    var projects = Path.of(args[0]).toAbsolutePath().normalize();
    var output = Path.of(args[1]).toAbsolutePath().normalize();
    if (!Files.isDirectory(projects, LinkOption.NOFOLLOW_LINKS)
        || !projects.toRealPath().equals(projects)
        || Files.exists(output, LinkOption.NOFOLLOW_LINKS)
        || output.startsWith(projects) || !output.getParent().toRealPath().equals(output.getParent()))
      throw new IllegalArgumentException("Require direct input, separate private output, and no overwrite");
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
    var parser = new WorldviewStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
    var storage = new FileProjectStorage(projects.resolve("imod").toFile(), "imod", null);
    var syntax = new HashMap<String, Ontology>();
    for (var url : storage.listResources(ProjectStorage.ResourceType.ONTOLOGY)) {
      var parsed = parser.parse(new StringReader(Files.readString(Path.of(url.toURI()))));
      if (parsed.hasSyntaxErrors()) throw new IllegalStateException("Fixture ontology syntax is invalid");
      var document = (Ontology) parsed.getRootASTElement();
      if (syntax.putIfAbsent(document.getNamespace().getName(), document) != null)
        throw new IllegalStateException("Duplicate fixture ontology");
    }
    var graph = new DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge.class);
    for (var name : syntax.keySet()) {
      graph.addVertex(name);
      for (var dependency : syntax.get(name).getNamespace().getImported()) {
        if (!syntax.containsKey(dependency)) throw new IllegalStateException("Missing ontology dependency");
        graph.addVertex(dependency); graph.addEdge(dependency, name);
      }
    }
    if (new CycleDetector<String, DefaultEdge>(graph).detectCycles()) throw new IllegalStateException("Ontology cycle");
    var scope = new WorldviewValidationScope();
    var ontologies = new HashMap<String, KimOntology>();
    var namespaces = new HashMap<String, KimNamespace>();
    var validate = org.integratedmodelling.klab.services.resources.storage.WorkspaceManager.class
        .getDeclaredMethod("validateSemanticAsset", KlabDocument.class, KimObservableVisitor.Resolver.class);
    validate.setAccessible(true);
    var preserve = org.integratedmodelling.klab.services.resources.storage.WorkspaceManager.class
        .getDeclaredMethod("preserveDocumentSource", KlabDocument.class, EObject.class);
    preserve.setAccessible(true);
    KimObservableVisitor.Resolver resolver = (urn, kind, context) -> {
      var current = context == null ? null : context.getDocument();
      if (kind == org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.ONTOLOGY)
        return current instanceof KimOntology ontology && urn.equals(ontology.getUrn()) ? ontology : ontologies.get(urn);
      if (kind == org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.NAMESPACE)
        return current instanceof KimNamespace namespace && urn.equals(namespace.getUrn()) ? namespace : namespaces.get(urn);
      if (kind == org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.MODEL
          || kind == org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.DEFINITION) {
        if (current instanceof KimNamespace namespace) {
          var found = find(namespace, urn, kind); if (found != null) return found;
        }
        for (var namespace : namespaces.values()) {
          var found = find(namespace, urn, kind); if (found != null) return found;
        }
      }
      if (kind == org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.CONCEPT
          || kind == org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.CONCEPT_STATEMENT) {
        if (current instanceof KimOntology ontology) {
          var found = find(ontology, urn); if (found != null) return found;
        }
        for (var ontology : ontologies.values()) {
          var found = find(ontology, urn); if (found != null) return found;
        }
      }
      return null;
    };
    var rows = new ArrayList<Map<String, Object>>();
    var order = new TopologicalOrderIterator<String, DefaultEdge>(graph);
    while (order.hasNext()) {
      var name = order.next();
      var notes = new ArrayList<Notification>();
      var parsed = new OntologySyntaxImpl(syntax.get(name), scope) {
        @Override protected void logWarning(ParsedObject target, EObject object, EStructuralFeature feature, String message) { notes.add(org.integratedmodelling.klab.api.services.runtime.Notification.warning(message)); }
        @Override protected void logError(ParsedObject target, EObject object, EStructuralFeature feature, String message) { notes.add(org.integratedmodelling.klab.api.services.runtime.Notification.error(message)); }
      };
      var adapted = LanguageAdapter.INSTANCE.adaptOntology(parsed, "imod", notes, 0L);
      preserve.invoke(null, adapted, syntax.get(name)); validate.invoke(null, adapted, resolver);
      if (!adapted.isInactive()) scope.addNamespace(adapted);
      ontologies.put(adapted.getUrn(), adapted);
      rows.add(Map.of("ontology", name, "errors", errors(adapted), "required_authorities", requirements(adapted)));
    }
    var declarationErrors = new WorldviewDeclarationRegistry(ontologies.values()).errors().stream().map(Notification::getMessage).toList();
    var strategyRows = new ArrayList<Map<String, Object>>();
    var strategyParser = new ObservationStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
    for (var url : storage.listResources(ProjectStorage.ResourceType.STRATEGY)) {
      var parsed = strategyParser.parse(new StringReader(Files.readString(Path.of(url.toURI()))));
      if (parsed.hasSyntaxErrors()) throw new IllegalStateException("Strategy syntax is invalid");
      var notes = new ArrayList<Notification>();
      var adaptedSyntax = (org.integratedmodelling.languages.api.ObservationSyntax.StrategyDocument) new ObservationSyntaxAdapter() {
        @Override protected void logWarning(ParsedObject target, EObject object, EStructuralFeature feature, String message) { notes.add(org.integratedmodelling.klab.api.services.runtime.Notification.warning(message)); }
        @Override protected void logError(ParsedObject target, EObject object, EStructuralFeature feature, String message) { notes.add(org.integratedmodelling.klab.api.services.runtime.Notification.error(message)); }
      }.adapt((org.integratedmodelling.languages.observation.ObservationDocument) parsed.getRootASTElement(), scope);
      var adapted = LanguageAdapter.INSTANCE.adaptStrategies(adaptedSyntax, "imod", notes, 0L);
      validate.invoke(null, adapted, resolver);
      strategyRows.add(Map.of("file", Path.of(url.toURI()).getFileName().toString(), "errors", errors(adapted)));
    }
    var modelParser = new KimStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
    var modelStorage = new FileProjectStorage(projects.resolve("python.fixture").toFile(), "python.fixture", null);
    var models = new HashMap<String, org.integratedmodelling.languages.kim.Model>();
    for (var url : modelStorage.listResources(ProjectStorage.ResourceType.MODEL_NAMESPACE)) {
      var parsed = modelParser.parse(new StringReader(Files.readString(Path.of(url.toURI()))));
      if (parsed.hasSyntaxErrors()) throw new IllegalStateException("Namespace syntax is invalid");
      var model = (org.integratedmodelling.languages.kim.Model) parsed.getRootASTElement(); models.put(model.getNamespace().getName(), model);
    }
    var modelGraph = new DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge.class);
    for (var name : models.keySet()) {
      modelGraph.addVertex(name);
      for (var imported : models.get(name).getNamespace().getImported()) {
        if (!models.containsKey(imported.getName())) throw new IllegalStateException("Missing namespace dependency");
        modelGraph.addVertex(imported.getName()); modelGraph.addEdge(imported.getName(), name);
      }
    }
    if (new CycleDetector<String, DefaultEdge>(modelGraph).detectCycles()) throw new IllegalStateException("Namespace cycle");
    var modelRows = new ArrayList<Map<String, Object>>();
    var modelOrder = new TopologicalOrderIterator<String, DefaultEdge>(modelGraph);
    while (modelOrder.hasNext()) {
      var name = modelOrder.next(); var notes = new ArrayList<Notification>();
      var parsed = new NamespaceSyntaxImpl(models.get(name), scope) {
        @Override protected void logWarning(ParsedObject target, EObject object, EStructuralFeature feature, String message) { notes.add(org.integratedmodelling.klab.api.services.runtime.Notification.warning(message)); }
        @Override protected void logError(ParsedObject target, EObject object, EStructuralFeature feature, String message) { notes.add(org.integratedmodelling.klab.api.services.runtime.Notification.error(message)); }
      };
      var adapted = LanguageAdapter.INSTANCE.adaptNamespace(parsed, "python.fixture", notes, 0L);
      preserve.invoke(null, adapted, models.get(name)); validate.invoke(null, adapted, resolver);
      namespaces.put(name, adapted); modelRows.add(Map.of("namespace", name, "errors", errors(adapted)));
    }
    boolean passed = declarationErrors.isEmpty() && rows.stream().allMatch(r -> ((List<?>) r.get("errors")).isEmpty())
        && strategyRows.stream().allMatch(r -> ((List<?>) r.get("errors")).isEmpty())
        && modelRows.stream().allMatch(r -> ((List<?>) r.get("errors")).isEmpty());
    var result = Map.of("mode", "OFFLINE NATIVE PARSER/ADAPTER/VISITOR/DECLARATION VALIDATION",
        "declaration_validation", passed ? "PASS" : "FAIL", "runtime_operational", "UNKNOWN",
        "ontologies", rows, "strategies", strategyRows, "namespaces", modelRows,
        "declaration_errors", declarationErrors, "services_started", 0, "scientific_jobs", 0);
    Files.write(output, new ObjectMapper().writeValueAsBytes(result), StandardOpenOption.CREATE_NEW);
    System.exit(passed ? 0 : 3);
  }
  static List<String> errors(KlabDocument<?> document) {
    return document.getNotifications().stream().filter(n -> n.getLevel().severity >= Notification.Level.Error.severity)
        .map(Notification::getMessage).toList();
  }
  static List<Map<String, String>> requirements(KimOntology ontology) {
    var result = new ArrayList<Map<String, String>>();
    for (var statement : ontology.getStatements()) requirements(statement, result);
    return result;
  }
  static void requirements(KimConceptStatement statement, List<Map<String, String>> result) {
    if (statement.getAuthorityRequired() != null) result.add(Map.of("authority", statement.getAuthorityRequired(),
        "provider_urn", String.valueOf(statement.getAuthorityParameters().get("urn"))));
    for (var child : statement.getChildren()) requirements(child, result);
  }
  static KlabStatement find(KimNamespace namespace, String urn, org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass kind) {
    for (var statement : namespace.getStatements())
      if (((kind == org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.MODEL && statement instanceof KimModel)
          || (kind == org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.DEFINITION && statement instanceof KimSymbolDefinition))
          && urn.equals(statement.getUrn())) return statement;
    return null;
  }
  static KimConceptStatement find(KimOntology ontology, String urn) {
    for (var statement : ontology.getStatements()) { var found = find(statement, ontology.getUrn(), urn); if (found != null) return found; }
    return null;
  }
  static KimConceptStatement find(KimConceptStatement statement, String namespace, String urn) {
    if (urn.equals(statement.getUrn()) || urn.equals(namespace + ":" + statement.getUrn())) return statement;
    for (var child : statement.getChildren()) { var found = find(child, namespace, urn); if (found != null) return found; }
    return null;
  }
}
