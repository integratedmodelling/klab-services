package org.integratedmodelling.klab.services.runtime.neo4j;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.KnowledgeGraph.Query;
import org.integratedmodelling.klab.api.data.KnowledgeGraph.QueryException;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel.Fields;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel.Labels;

/** Parameterized graph queries. Caller values never become Cypher syntax or identifiers. */
public final class Neo4jQueryCompiler {
  public record Statement(String cypher, Map<String, Object> parameters) {}

  private final Map<String, Object> parameters = new LinkedHashMap<>();
  private final Set<KnowledgeGraphQuery<?>> visiting =
      Collections.newSetFromMap(new IdentityHashMap<>());
  private int aliases;
  private int queryCount;
  private String contextParameter;

  private static final String OWNERSHIP =
      java.util.stream.Stream.of(
              GraphModel.Relationship.HAS_CHILD,
              GraphModel.Relationship.HAS_MEMBER,
              GraphModel.Relationship.HAS_PROVENANCE,
              GraphModel.Relationship.HAS_DATAFLOW,
              GraphModel.Relationship.HAS_DATA,
              GraphModel.Relationship.HAS_GEOMETRY,
              GraphModel.Relationship.HAS_ACTIVITY,
              GraphModel.Relationship.HAS_PLAN,
              GraphModel.Relationship.CONTEXTUALIZED_BY,
              GraphModel.Relationship.HAS_AGENT,
              GraphModel.Relationship.BY_AGENT,
              GraphModel.Relationship.CREATED,
              GraphModel.Relationship.RESOLVED)
          .map(Enum::name)
          .collect(Collectors.joining("|"));

  public static Statement compile(KnowledgeGraphQuery<?> query, String contextId) {
    if (contextId == null || contextId.isBlank()) throw invalid("A context is required");
    var compiler = new Neo4jQueryCompiler();
    compiler.contextParameter = compiler.parameter(contextId);
    compiler.validate(query, true);
    boolean links = query.getResultType() == KnowledgeGraphQuery.AssetType.LINK;
    String match =
        links ? "MATCH (s)-[n]->(t)" : "MATCH (n" + labelSuffix(query.getResultType()) + ")";
    String visibility =
        links ? compiler.visible("s") + " AND " + compiler.visible("t") : compiler.visible("n");
    // Geometry records are descriptors, not RuntimeAsset implementations.
    if (query.getResultType() == KnowledgeGraphQuery.AssetType.ANY)
      visibility += " AND NOT n:" + Labels.GEOMETRY;
    if (links) visibility += " AND NOT s:" + Labels.GEOMETRY + " AND NOT t:" + Labels.GEOMETRY;
    String code =
        match
            + " WHERE "
            + visibility
            + " AND ("
            + compiler.predicate(query, links)
            + ")"
            + (links ? " RETURN DISTINCT n, s, t" : " RETURN DISTINCT n");
    var order = new ArrayList<String>();
    for (var item : query.getOrdering()) {
      order.add(property("n", item.field()) + " " + item.direction().name());
    }
    // Deterministic tie-breaker for pagination, including relationships without application IDs.
    order.add("elementId(n) ASC");
    code += " ORDER BY " + String.join(", ", order);
    if (query.getOffset() > 0) code += " SKIP " + compiler.parameter(query.getOffset());
    if (query.getLimit() >= 0) code += " LIMIT " + compiler.parameter(query.getLimit());
    return new Statement(code, Map.copyOf(compiler.parameters));
  }

  private void validate(KnowledgeGraphQuery<?> query, boolean root) {
    if (query == null || query.getResultType() == null || query.getType() == null)
      throw invalid("Missing query type");
    if (++queryCount > 128 || !visiting.add(query)) throw invalid("Cyclic or oversized query tree");
    if (query.getMinimumDepth() < 0
        || query.getDepth() < query.getMinimumDepth()
        || query.getDepth() > 64)
      throw invalid("Hop bounds must satisfy 0 <= minimum <= maximum <= 64");
    if (query.getLimit() < -1 || query.getOffset() < 0) throw invalid("Invalid pagination");
    if (query.getCriteria() == null
        || query.getAssetQueryCriteria() == null
        || query.getOrdering() == null
        || query.getChildren() == null
        || query.getRelationshipQueryCriteria() == null) throw invalid("Null query collections");
    if (!root
        && (query.getLimit() != -1 || query.getOffset() != 0 || !query.getOrdering().isEmpty()))
      throw invalid("Apply pagination and ordering to the combined query, not its children");
    for (var order : query.getOrdering()) {
      if (order == null || order.direction() == null) throw invalid("Invalid ordering");
      property("n", order.field());
    }
    for (var criterion : allCriteria(query)) validateCriterion(criterion);
    for (var field : query.getRelationshipQueryCriteria().keySet()) property("n", field);
    if (query.getType() == KnowledgeGraphQuery.QueryType.QUERY) {
      if (!query.getChildren().isEmpty()) throw invalid("A leaf query cannot have children");
      if (query.getSource() != null && query.getTarget() != null)
        throw invalid("Use between for two endpoints");
    } else {
      int count = query.getType() == KnowledgeGraphQuery.QueryType.NOT ? 1 : 2;
      if (query.getChildren().size() < count || (count == 1 && query.getChildren().size() != 1))
        throw invalid("Invalid Boolean query arity");
      if (query.getSource() != null
          || query.getTarget() != null
          || query.getRelationshipSource() != null
          || query.getRelationshipTarget() != null
          || query.getRelationship() != null
          || query.getId() != -1) throw invalid("Put anchors and IDs on Boolean query leaves");
      for (var child : query.getChildren()) {
        if (child == null || child.getResultType() != query.getResultType())
          throw invalid("Mixed result types");
        validate(child, false);
      }
    }
    labelSuffix(query.getResultType());
    visiting.remove(query);
  }

  private static List<Query.Criterion> allCriteria(KnowledgeGraphQuery<?> query) {
    var result = new ArrayList<>(query.getCriteria());
    // Backward compatibility for the old string-valued wire representation.
    for (var legacy : query.getAssetQueryCriteria()) {
      try {
        var operator = Query.Operator.valueOf(legacy.getSecond());
        Object value = legacy.getThird();
        if (value != null
            && (Fields.ID.equals(legacy.getFirst())
                || operator == Query.Operator.LT
                || operator == Query.Operator.LE
                || operator == Query.Operator.GT
                || operator == Query.Operator.GE)) {
          value = new java.math.BigDecimal(value.toString());
        }
        result.add(new Query.Criterion(legacy.getFirst(), operator, value));
      } catch (RuntimeException e) {
        throw invalid("Invalid legacy query criterion");
      }
    }
    return result;
  }

  private static void validateCriterion(Query.Criterion criterion) {
    if (criterion == null || criterion.operator() == null)
      throw invalid("Missing predicate operator");
    property("n", criterion.field());
    if (criterion.operator() == Query.Operator.INTERSECT
        || criterion.operator() == Query.Operator.COVERS
        || criterion.operator() == Query.Operator.NEAREST)
      throw new QueryException(
          QueryException.Code.UNSUPPORTED_QUERY,
          "Spatial predicates require a geometry/CRS query contract: " + criterion.operator());
    if (criterion.argument() == null && criterion.operator() != Query.Operator.EQUALS)
      throw invalid("Only EQUALS accepts null (property absence)");
  }

  private String predicate(KnowledgeGraphQuery<?> query, boolean links) {
    var clauses = new ArrayList<String>();
    if (query.getType() != KnowledgeGraphQuery.QueryType.QUERY) {
      var children =
          query.getChildren().stream().map(child -> "(" + predicate(child, links) + ")").toList();
      clauses.add(
          query.getType() == KnowledgeGraphQuery.QueryType.NOT
              ? "NOT " + children.getFirst()
              : String.join(
                  query.getType() == KnowledgeGraphQuery.QueryType.AND ? " AND " : " OR ",
                  children));
    } else if (query.getId() != -1) {
      if (links) throw invalid("Relationship lookup uses between, not asset IDs");
      Object id = query.getId();
      if (query.getId() == -1000) return property("n", Fields.ID) + " = " + contextParameter;
      if (query.getId() < 0) throw invalid("Transient observation lookup requires the scope cache");
      return property("n", Fields.ID) + " = " + parameter(id);
    } else if (links) {
      if (query.getDepth() != 1 || query.getMinimumDepth() != 1)
        throw invalid("Link results require exactly one hop");
      if (query.getRelationship() != null)
        clauses.add("type(n) = " + parameter(query.getRelationship().name()));
      var source =
          query.getRelationshipSource() == null ? query.getSource() : query.getRelationshipSource();
      var target =
          query.getRelationshipTarget() == null ? query.getTarget() : query.getRelationshipTarget();
      if (source != null) clauses.add(anchor(source, "s"));
      if (target != null) clauses.add(anchor(target, "t"));
      query
          .getRelationshipQueryCriteria()
          .forEach((field, value) -> clauses.add(equalsProperty("n", field, value)));
    } else {
      if (query.getRelationshipSource() != null || query.getRelationshipTarget() != null)
        throw invalid("between requires Link results");
      var endpoint = query.getSource() == null ? query.getTarget() : query.getSource();
      if (endpoint != null) {
        String alias = "a" + aliases++;
        String path = "p" + aliases++;
        String edge =
            "["
                + (query.getRelationship() == null ? "" : ":" + query.getRelationship().name())
                + "*"
                + query.getMinimumDepth()
                + ".."
                + query.getDepth()
                + "]";
        String pattern =
            query.getSource() == null
                ? "(n)-" + edge + "->(" + alias + ")"
                : "(" + alias + ")-" + edge + "->(n)";
        var restrictions = new ArrayList<String>();
        restrictions.add(anchor(endpoint, alias));
        restrictions.add("all(v IN nodes(" + path + ") WHERE " + visible("v") + ")");
        query
            .getRelationshipQueryCriteria()
            .forEach(
                (field, value) ->
                    restrictions.add(
                        "all(e IN relationships("
                            + path
                            + ") WHERE "
                            + equalsProperty("e", field, value)
                            + ")"));
        clauses.add(
            "EXISTS { MATCH "
                + path
                + "="
                + pattern
                + " WHERE "
                + String.join(" AND ", restrictions)
                + " }");
      } else if (query.getRelationship() != null
          || !query.getRelationshipQueryCriteria().isEmpty()) {
        throw invalid("A relationship traversal requires an anchor");
      }
    }
    for (var criterion : allCriteria(query)) clauses.add(criterion(criterion));
    return clauses.isEmpty() ? "true" : "(" + String.join(") AND (", clauses) + ")";
  }

  private String anchor(KnowledgeGraphQuery.Asset asset, String alias) {
    if (asset.getType() == null) throw invalid("Missing anchor type");
    String suffix = labelSuffix(asset.getType());
    String type = suffix.isEmpty() ? "true" : alias + suffix;
    Object value = asset.getId();
    String field = Fields.ID;
    if (value == null) {
      switch (asset.getType()) {
        case SCOPE -> {
          return type + " AND " + property(alias, field) + " = " + contextParameter;
        }
        case DATAFLOW -> {
          return type
              + " AND "
              + property(alias, field)
              + " = "
              + contextParameter
              + " + '.DATAFLOW'";
        }
        case PROVENANCE -> {
          return type
              + " AND "
              + property(alias, field)
              + " = "
              + contextParameter
              + " + '.PROVENANCE'";
        }
        case ACTUATOR, DATA, PLAN -> {
          if (asset.getUrn() != null) {
            try {
              value = Long.parseLong(asset.getUrn());
            } catch (NumberFormatException e) {
              throw invalid("Invalid numeric anchor");
            }
          }
        }
        case AGENT -> {
          field = Fields.NAME;
          value = asset.getUrn();
        }
        default -> {
          field = Fields.URN;
          value = asset.getUrn();
        }
      }
    }
    return value == null
        ? type
        : type + " AND " + property(alias, field) + " = " + parameter(value);
  }

  private String criterion(Query.Criterion criterion) {
    String field = property("n", criterion.field());
    Object value = criterion.argument();
    if (criterion.operator() == Query.Operator.EQUALS)
      return equalsProperty("n", criterion.field(), value);
    if (criterion.operator() == Query.Operator.LIKE) {
      if (!(value instanceof String pattern)) throw invalid("LIKE requires a string");
      var regex = new StringBuilder("(?s)");
      for (int i = 0; i < pattern.length(); i++) {
        char ch = pattern.charAt(i);
        regex.append(ch == '%' ? ".*" : ch == '_' ? "." : Pattern.quote(String.valueOf(ch)));
      }
      return field + " =~ " + parameter(regex.toString());
    }
    if (criterion.operator() == Query.Operator.BEFORE
        || criterion.operator() == Query.Operator.AFTER) {
      if (value instanceof String text) {
        try {
          value = Instant.parse(text).toEpochMilli();
        } catch (RuntimeException e) {
          throw invalid("Temporal predicates require epoch milliseconds or an ISO instant");
        }
      }
      if (!(value instanceof Number)) throw invalid("Temporal predicates require an instant");
    }
    String operator =
        switch (criterion.operator()) {
          case LT, BEFORE -> "<";
          case LE -> "<=";
          case GT, AFTER -> ">";
          case GE -> ">=";
          default -> throw invalid("Invalid comparison");
        };
    return field + " " + operator + " " + parameter(value);
  }

  private String equalsProperty(String alias, String field, Object value) {
    return property(alias, field) + (value == null ? " IS NULL" : " = " + parameter(value));
  }

  private String visible(String variable) {
    return "EXISTS { MATCH (root:"
        + Labels.CONTEXT
        + " {"
        + Fields.ID
        + ": "
        + contextParameter
        + "})-[:"
        + OWNERSHIP
        + "*0..64]->("
        + variable
        + ") }";
  }

  private String parameter(Object value) {
    if (value instanceof java.math.BigDecimal decimal) {
      try {
        value = decimal.longValueExact();
      } catch (ArithmeticException e) {
        value = decimal.doubleValue();
      }
    }
    if (value instanceof Number number && !Double.isFinite(number.doubleValue()))
      throw invalid("Non-finite numeric argument");
    if (!(value instanceof String
        || value instanceof Number
        || value instanceof Boolean
        || value instanceof List<?>)) throw invalid("Unsupported parameter value");
    String key = "p" + parameters.size();
    parameters.put(key, value);
    return "$" + key;
  }

  private static String property(String alias, String field) {
    if (field == null || !Fields.ALL.contains(field))
      throw invalid("Unknown graph field: " + field);
    return alias + ".`" + field + "`";
  }

  private static String labelSuffix(KnowledgeGraphQuery.AssetType type) {
    return switch (type) {
      case ANY, LINK -> "";
      case SCOPE -> ":" + Labels.CONTEXT;
      case DATAFLOW -> ":" + Labels.DATAFLOW;
      case PROVENANCE -> ":" + Labels.PROVENANCE;
      case ACTUATOR -> ":" + Labels.ACTUATOR;
      case ACTIVITY -> ":" + Labels.ACTIVITY;
      case AGENT -> ":" + Labels.AGENT;
      case PLAN -> ":" + Labels.PLAN;
      case OBSERVATION -> ":" + Labels.OBSERVATION;
      case COHORT -> ":" + Labels.COHORT;
      case DATA -> ":" + Labels.DATA;
      default ->
          throw new QueryException(
              QueryException.Code.UNSUPPORTED_QUERY, "Not a runtime graph result: " + type);
    };
  }

  private static QueryException invalid(String message) {
    return new QueryException(QueryException.Code.INVALID_QUERY, message);
  }
}
