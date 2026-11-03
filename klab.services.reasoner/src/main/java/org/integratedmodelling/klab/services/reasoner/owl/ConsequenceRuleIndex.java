package org.integratedmodelling.klab.services.reasoner.owl;

import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.runtime.language.WorldviewDeclarationRegistry;

/** Immutable source-owned recipes. Generated vocabulary membership never defines this corpus. */
public final class ConsequenceRuleIndex {
  private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
      JacksonConfiguration.newObjectMapper();
  private final Worldview source;
  private final Map<String, String> entries;
  private final Map<String, Set<String>> owners;
  private final Map<String, Set<String>> ingredients;
  private final Map<KimConsequenceRule.Kind, String> unavailable;
  private final Map<String, Set<String>> obligations;

  private ConsequenceRuleIndex(
      Worldview source,
      Map<String, String> entries,
      Map<String, Set<String>> owners,
      Map<String, Set<String>> ingredients,
      Map<KimConsequenceRule.Kind, String> unavailable,
      Map<String, Set<String>> obligations) {
    this.source = source;
    this.entries = Map.copyOf(entries);
    this.owners = freeze(owners);
    this.ingredients = freeze(ingredients);
    this.unavailable = Map.copyOf(unavailable);
    this.obligations = freeze(obligations);
  }

  private static Map<String, Set<String>> freeze(Map<String, Set<String>> values) {
    var copy = new LinkedHashMap<String, Set<String>>();
    values.forEach((k, v) -> copy.put(k, Set.copyOf(v)));
    return Map.copyOf(copy);
  }

  public boolean belongsTo(Worldview source) {
    return this.source == source;
  }

  /** Called in the private source compiler, where complete syntax may be declared safely. */
  public static ConsequenceRuleIndex capture(
      Worldview source, Function<String, Concept> named, Function<KimConcept, Concept> resolve) {
    Objects.requireNonNull(source, "Accepted authored worldview");
    var registry = new WorldviewDeclarationRegistry(source.getOntologies());
    if (!registry.errors().isEmpty())
      throw new IllegalStateException("Consequence declarations have invalid source");
    var entries = new LinkedHashMap<String, String>();
    var owners = new LinkedHashMap<String, Set<String>>();
    var ingredients = new LinkedHashMap<String, Set<String>>();
    var unavailable = new EnumMap<KimConsequenceRule.Kind, String>(KimConsequenceRule.Kind.class);
    var obligations = new LinkedHashMap<String, Set<String>>();
    for (var declaration : registry.ordered()) {
      var statement = declaration.statement();
      var rules = statement.getConsequenceRules();
      for (var clause : statement.getDeclarationClauses()) {
        var kind =
            switch (clause.kind()) {
              case "confersClause" -> KimConsequenceRule.Kind.CONFERS;
              case "impliesClause" -> KimConsequenceRule.Kind.IMPLIES;
              case "emergesFromClause" -> KimConsequenceRule.Kind.EMERGES_FROM;
              default -> null;
            };
        if (kind != null
            && !completeClause(declaration.ontology().getSourceCode(), clause, kind, rules))
          unavailable.put(
              kind,
              "Source clause lacks complete typed consequence item coverage: " + declaration.urn());
      }
      if (!coversLegacy(statement.getTraitsConferred(), KimConsequenceRule.Kind.CONFERS, rules))
        unavailable.put(
            KimConsequenceRule.Kind.CONFERS,
            "Legacy conferred traits lack recipient/source evidence");
      if (!coversLegacy(statement.getImpliedObservables(), KimConsequenceRule.Kind.IMPLIES, rules))
        unavailable.put(
            KimConsequenceRule.Kind.IMPLIES, "Legacy implications lack complete source evidence");
      if (!coversLegacy(
          statement.getEmergenceTriggers(), KimConsequenceRule.Kind.EMERGES_FROM, rules))
        unavailable.put(
            KimConsequenceRule.Kind.EMERGES_FROM,
            "Legacy emergence triggers lack complete source evidence");
      if (rules.isEmpty()) continue;
      var owner = require(named.apply(declaration.urn()));
      var ontology = declaration.ontology();
      String hash = SemanticValidationRequest.sourceHash(ontology.getSourceCode());
      if (hash == null)
        throw new IllegalStateException(
            "Consequence source hash unavailable: " + ontology.getUrn());
      for (var rule : rules) {
        String id =
            digest(
                List.of(
                    declaration.urn(),
                    hash,
                    rule.kind().name(),
                    rule.source().offset(),
                    rule.source().length()));
        var entry =
            new ConsequenceRuleResponse.Entry(
                id,
                declaration.urn(),
                owner,
                rule,
                ontology.getUrn(),
                Long.toString(ontology.getLastUpdateTimestamp()),
                hash);
        try {
          var origin = rule.source();
          var code = ontology.getSourceCode();
          if (origin.offset() < 0
              || origin.length() < 0
              || code == null
              || origin.offset() > code.length() - origin.length()
              || !Objects.equals(
                  code.substring(origin.offset(), origin.offset() + origin.length()),
                  origin.source()))
            throw new IllegalStateException(
                "Rule source span does not match the accepted original document");
          var terms = new LinkedHashSet<String>();
          terms.add(owner.getUrn());
          terms.add(require(resolve.apply(rule.target())).getUrn());
          for (var syntax :
              Arrays.asList(
                  rule.recipient().expression(), rule.secondaryInherency(), rule.within()))
            if (syntax != null) terms.add(require(resolve.apply(syntax)).getUrn());
          if (rule.kind() == KimConsequenceRule.Kind.EMERGES_FROM) {
            for (var leaf : positiveLeaves(rule.target())) {
              var meaning = require(resolve.apply(leaf));
              terms.add(meaning.getUrn());
              ingredients
                  .computeIfAbsent(meaning.getUrn(), ignored -> new LinkedHashSet<>())
                  .add(id);
            }
            if (rule.within() != null)
              for (var leaf : positiveLeaves(rule.within())) {
                var meaning = require(resolve.apply(leaf));
                terms.add(meaning.getUrn());
                ingredients
                    .computeIfAbsent(meaning.getUrn(), ignored -> new LinkedHashSet<>())
                    .add(id);
              }
          }
          owners.computeIfAbsent(owner.getUrn(), ignored -> new LinkedHashSet<>()).add(id);
          entries.put(id, JSON.writeValueAsString(entry));
          obligations.put(id, terms);
        } catch (RuntimeException invalid) {
          unavailable.put(
              rule.kind(),
              "Consequence source cannot be indexed safely: "
                  + declaration.urn()
                  + ": "
                  + invalid.getMessage());
        } catch (java.io.IOException failure) {
          throw new IllegalStateException("Cannot freeze consequence declaration", failure);
        }
      }
    }
    return new ConsequenceRuleIndex(source, entries, owners, ingredients, unavailable, obligations);
  }

  /** Validate item-span coverage only: no semantic expressions are parsed from source text. */
  private static boolean completeClause(
      String code,
      KimConceptStatement.DeclarationClause clause,
      KimConsequenceRule.Kind kind,
      List<KimConsequenceRule> rules) {
    if (code == null
        || clause.offset() < 0
        || clause.length() < 0
        || clause.offset() > code.length() - clause.length()) return false;
    int end = clause.offset() + clause.length();
    var items =
        rules.stream()
            .filter(
                r ->
                    r.kind() == kind
                        && r.source().offset() >= clause.offset()
                        && r.source().length() > 0
                        && r.source().offset() <= end - r.source().length())
            .sorted(Comparator.comparingInt(r -> r.source().offset()))
            .toList();
    if (items.isEmpty()) return false;
    int cursor = clause.offset();
    var words =
        switch (kind) {
          case CONFERS -> List.of("confers");
          case IMPLIES -> List.of("implies");
          case EMERGES_FROM -> List.of("emerges", "from");
        };
    for (var word : words) {
      cursor = triviaEnd(code, cursor, end);
      if (cursor < 0 || !code.startsWith(word, cursor) || cursor + word.length() > end)
        return false;
      cursor += word.length();
      if (cursor < end && Character.isJavaIdentifierPart(code.charAt(cursor))) return false;
    }
    for (int i = 0; i < items.size(); i++) {
      var item = items.get(i).source();
      cursor = triviaEnd(code, cursor, end);
      if (i > 0) {
        if (cursor < 0 || cursor >= end || code.charAt(cursor) != ',') return false;
        cursor = triviaEnd(code, cursor + 1, end);
      }
      if (cursor != item.offset()) return false;
      cursor = item.offset() + item.length();
    }
    return triviaEnd(code, cursor, end) == end;
  }

  private static int triviaEnd(String code, int from, int end) {
    if (from < 0) return -1;
    int cursor = from;
    while (cursor < end) {
      if (Character.isWhitespace(code.charAt(cursor))) {
        cursor++;
        continue;
      }
      if (cursor + 1 < end && code.startsWith("//", cursor)) {
        cursor += 2;
        while (cursor < end && code.charAt(cursor) != '\n' && code.charAt(cursor) != '\r') cursor++;
      } else if (cursor + 1 < end && code.startsWith("/*", cursor)) {
        int close = code.indexOf("*/", cursor + 2);
        if (close < 0 || close + 2 > end) return -1;
        cursor = close + 2;
      } else break;
    }
    return cursor;
  }

  private static boolean coversLegacy(
      List<KimConcept> legacy, KimConsequenceRule.Kind kind, List<KimConsequenceRule> rules) {
    var remaining = new HashMap<String, Integer>();
    for (var rule : rules)
      if (rule.kind() == kind
          && rule.within() == null
          && rule.secondaryInherency() == null
          && rule.recipient().kind() == KimConsequenceRule.RecipientKind.DEFAULT)
        remaining.merge(rule.target().getUrn(), 1, Integer::sum);
    for (var target : legacy) {
      String key = target.getUrn();
      int count = remaining.getOrDefault(key, 0);
      if (count == 0) return false;
      remaining.put(key, count - 1);
    }
    return true;
  }

  private static Concept require(Concept concept) {
    if (concept == null || concept.getUrn() == null || concept.is(SemanticType.NOTHING))
      throw new IllegalStateException("Consequence meaning is unavailable in captured source");
    return concept;
  }

  private static String digest(Object value) {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(value)));
    } catch (Exception failure) {
      throw new IllegalStateException(failure);
    }
  }

  /** Shared source/matcher contract; keep this entry point for source-index callers. */
  public static List<KimConcept> positiveLeaves(KimConcept syntax) {
    return org.integratedmodelling.klab.runtime.consequence.ConsequencePatterns.positiveLeaves(
        syntax);
  }

  static KimConcept unwrapped(KimConcept syntax) {
    return org.integratedmodelling.klab.runtime.consequence.ConsequencePatterns.unwrapped(syntax);
  }

  public record Selection(List<ConsequenceRuleResponse.Entry> rules, Set<String> obligations) {}

  public Selection select(
      ConsequenceRuleQuery query, Function<Concept, Collection<String>> semanticKeys) {
    var requested = query.kinds();
    for (var kind : requested) {
      boolean relevant =
          !query.ownerTriggers().isEmpty()
              || kind == KimConsequenceRule.Kind.EMERGES_FROM
                  && !query.ingredientTriggers().isEmpty();
      if (relevant && unavailable.containsKey(kind))
        throw new IllegalStateException(unavailable.get(kind));
    }
    var ids = new TreeSet<String>();
    for (var trigger : query.ownerTriggers())
      for (var key : semanticKeys.apply(trigger)) ids.addAll(owners.getOrDefault(key, Set.of()));
    for (var trigger : query.ingredientTriggers())
      for (var key : semanticKeys.apply(trigger))
        ids.addAll(ingredients.getOrDefault(key, Set.of()));
    var result = new ArrayList<ConsequenceRuleResponse.Entry>();
    var terms = new LinkedHashSet<String>();
    for (var id : ids) {
      try {
        var entry = JSON.readValue(entries.get(id), ConsequenceRuleResponse.Entry.class);
        if (requested.contains(entry.rule().kind())) {
          result.add(entry);
          terms.addAll(obligations.get(id));
        }
      } catch (java.io.IOException failure) {
        throw new IllegalStateException("Cannot decode captured consequence rule", failure);
      }
    }
    return new Selection(List.copyOf(result), Set.copyOf(terms));
  }

  /** Reflexive inferred semantic keys. Equality is by canonical meaning, never by local labels. */
  public static Collection<String> semanticKeys(OWL owl, Concept trigger) {
    owl.requireAvailableClosure();
    var owned = require(owl.existingMeaning(trigger.getUrn()));
    var keys = new LinkedHashSet<String>();
    keys.add(owned.getUrn());
    for (var parent : owl.getParentClosure(owned)) keys.add(parent.getUrn());
    for (var equivalent : owl.getEquivalentClasses(owl.getOWLClass(owned)).getEntities()) {
      var concept = owl.getConceptFor(equivalent);
      if (concept != null && !concept.is(SemanticType.NOTHING)) keys.add(concept.getUrn());
    }
    return keys;
  }
}
