package org.integratedmodelling.klab.services.resources.lang;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Codelist;
import org.integratedmodelling.klab.api.knowledge.impl.CodelistImpl;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;

/** Derives transportable codelists from {@code @code(scheme, integer)} concept annotations. */
public final class ConceptCodelistBuilder {

  private ConceptCodelistBuilder() {}

  public static Codelist build(
      String rootConceptUrn,
      KimConceptStatement root,
      String serviceId,
      Function<String, KimConcept> conceptResolver) {
    var result = new CodelistImpl();
    result.setUrn(rootConceptUrn);
    result.setName(rootConceptUrn);
    result.setDescription(root.getDocstring());
    result.setRootConceptId(rootConceptUrn);
    result.setServiceId(serviceId);
    result.setType(Artifact.Type.CONCEPT);
    var seenCodes = new HashMap<String, Map<Long, String>>();
    var seenConceptSchemes = new HashSet<String>();
    for (var child : root.getChildren())
      collect(child, result, seenCodes, seenConceptSchemes, conceptResolver);
    var authorities = result.getAuthorityIds();
    if (authorities.size() == 1) result.setAuthorityId(authorities.iterator().next());
    return result;
  }

  private static void collect(
      KimConceptStatement statement,
      CodelistImpl target,
      Map<String, Map<Long, String>> seenCodes,
      Set<String> seenConceptSchemes,
      Function<String, KimConcept> conceptResolver) {
    String conceptUrn = statement.getNamespace() + ":" + statement.getUrn();
    for (var annotation : statement.getAnnotations()) {
      if (!"code".equals(annotation.getName())) continue;
      if (statement.isAbstract())
        throw new KlabIllegalArgumentException(
            "Abstract concept " + conceptUrn + " cannot be a @code result");
      var arguments = annotation.getUnnamedArguments();
      if (arguments.size() != 2
          || !(arguments.getFirst() instanceof String scheme)
          || scheme.isBlank()
          || !scheme.matches("[a-z][a-z0-9]*(?:[._/-][a-z0-9]+)*"))
        throw new KlabIllegalArgumentException(
            "Invalid @code annotation on " + conceptUrn
                + ": expected @code(\"lower-case-scheme\", integer)");
      long code = integralCode(arguments.get(1), conceptUrn);
      if (!seenConceptSchemes.add(conceptUrn + "\u0000" + scheme))
        throw new KlabIllegalArgumentException(
            "Concept " + conceptUrn + " declares more than one code for scheme " + scheme);
      var existing =
          seenCodes
              .computeIfAbsent(scheme, ignored -> new HashMap<>())
              .putIfAbsent(code, conceptUrn);
      if (existing != null && !existing.equals(conceptUrn))
        throw new KlabIllegalArgumentException(
            "Code " + code + " in scheme " + scheme + " is declared by both " + existing
                + " and " + conceptUrn + " below " + target.getRootConceptId());
      var value = conceptResolver.apply(conceptUrn);
      if (value == null)
        throw new KlabIllegalArgumentException(
            "Cannot create codelist value for concept " + conceptUrn);
      target
          .getEntries()
          .add(new CodelistImpl.Entry(scheme, code, value, statement.getDocstring(), true));
    }
    for (var child : statement.getChildren())
      collect(child, target, seenCodes, seenConceptSchemes, conceptResolver);
  }

  private static long integralCode(Object value, String conceptUrn) {
    if (!(value instanceof Number number))
      throw new KlabIllegalArgumentException(
          "Invalid @code annotation on " + conceptUrn + ": the code must be an integer");
    try {
      if (number instanceof java.math.BigInteger integer) return integer.longValueExact();
      if (number instanceof java.math.BigDecimal decimal) return decimal.longValueExact();
    } catch (ArithmeticException e) {
      throw invalidCode(conceptUrn);
    }
    if (number instanceof Byte || number instanceof Short || number instanceof Integer
        || number instanceof Long) return number.longValue();
    double code = number.doubleValue();
    if (!Double.isFinite(code)
        || Math.rint(code) != code
        || code >= 0x1.0p63
        || code < -0x1.0p63)
      throw invalidCode(conceptUrn);
    return number.longValue();
  }

  private static KlabIllegalArgumentException invalidCode(String conceptUrn) {
    return new KlabIllegalArgumentException(
        "Invalid @code annotation on " + conceptUrn
            + ": the code must be a signed 64-bit integer");
  }
}
