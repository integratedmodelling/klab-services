package org.integratedmodelling.klab.api.lang.kim.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.integratedmodelling.klab.api.data.mediation.NumericRange;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.lang.kim.KimClassification;
import org.integratedmodelling.klab.api.lang.kim.KimClassifier;
import org.integratedmodelling.klab.api.lang.kim.KimLookupTable;

/** Structural and transport-boundary validation for classification and lookup syntax beans. */
public final class KimValueMappingValidator {

  private KimValueMappingValidator() {}

  public static void validate(KimClassification classification) {
    if (classification.getClassifiers() == null || classification.getClassifiers().isEmpty()) {
      throw new KlabValidationException("A classification must contain at least one rule");
    }
    for (int i = 0; i < classification.getClassifiers().size(); i++) {
      var rule = classification.getClassifiers().get(i);
      if (rule.getFirst() == null || rule.getSecond() == null) {
        throw new KlabValidationException("Classification rule " + (i + 1) + " is incomplete");
      }
    }
    if (classification.isDiscretization()) {
      validateDiscretization(classification);
    }
  }

  /**
   * Validate and normalize a lookup table. When {@code unresolved} is true, only information that
   * is available before resolving a named table is checked.
   */
  public static void validateAndNormalize(KimLookupTable lookup, boolean unresolved) {
    if (unresolved && lookup.getUrn() != null) return;
    if (lookup.getTable() == null) {
      if (unresolved && lookup.getUrn() != null) return;
      throw new KlabValidationException("A lookup table has no table data");
    }
    var table = lookup.getTable();
    int columns = table.getColumnCount();
    if (table.getRowCount() == 0 || columns == 0) {
      throw new KlabValidationException("A lookup table must contain at least one non-empty row");
    }
    for (int i = 0; i < table.getRowCount(); i++) {
      if (table.row(i) == null || table.row(i).length != columns) {
        throw new KlabValidationException("Lookup table row " + (i + 1) + " is not rectangular");
      }
    }
    if (table.getHeaders() != null
        && !table.getHeaders().isEmpty()
        && table.getHeaders().size() != columns) {
      throw new KlabValidationException("Lookup table header width does not match its rows");
    }

    if (unresolved) return;
    if (lookup.isTwoWay()) {
      validateTwoWay(lookup);
      return;
    }

    var implementation = (KimLookupTableImpl) lookup;
    var arguments = new ArrayList<>(lookup.getArguments());
    if (arguments.isEmpty() && table.getHeaders() != null && !table.getHeaders().isEmpty()) {
      for (int i = 0; i < table.getHeaders().size(); i++) {
        var argument = new KimLookupTable.Argument();
        argument.id = i == table.getHeaders().size() - 1 ? "?" : table.getHeaders().get(i);
        arguments.add(argument);
      }
    } else if (arguments.size() == columns - 1) {
      var result = new KimLookupTable.Argument();
      result.id = "?";
      arguments.add(result);
    }
    while (arguments.size() < columns) {
      var ignored = new KimLookupTable.Argument();
      ignored.id = "*";
      arguments.add(ignored);
    }
    if (arguments.size() != columns) {
      throw new KlabValidationException(
          "Lookup table has " + columns + " columns but " + arguments.size() + " arguments");
    }
    int resultColumn = -1;
    for (int i = 0; i < arguments.size(); i++) {
      if ("?".equals(arguments.get(i).id)) {
        if (resultColumn >= 0) {
          throw new KlabValidationException("A lookup table must have exactly one result column");
        }
        resultColumn = i;
      }
    }
    if (resultColumn < 0) {
      throw new KlabValidationException("A lookup table must identify one result column with ?");
    }
    implementation.setArguments(arguments);
    implementation.setLookupColumnIndex(resultColumn);
    Artifact.Type resultType = null;
    for (int row = 0; row < table.getRowCount(); row++) {
      Artifact.Type candidate = table.row(row)[resultColumn].getType();
      if (resultType == null || resultType == Artifact.Type.VALUE) resultType = candidate;
      else if (candidate != Artifact.Type.VALUE && candidate != resultType) {
        throw new KlabValidationException("The lookup result column must have one value type");
      }
    }
    implementation.setLookupType(resultType == null ? Artifact.Type.VALUE : resultType);
  }

  private static void validateTwoWay(KimLookupTable lookup) {
    var table = lookup.getTable();
    if (lookup.getRowClassifiers().size() != table.getRowCount()) {
      throw new KlabValidationException("A two-way table needs one classifier for each row");
    }
    if (lookup.getColumnClassifiers().size() != table.getColumnCount()) {
      throw new KlabValidationException("A two-way table needs one classifier for each column");
    }
    if (!lookup.getArguments().isEmpty()) {
      if (lookup.getArguments().size() != 2) {
        throw new KlabValidationException("A two-way table requires exactly two arguments");
      }
      boolean row = false;
      boolean column = false;
      for (var argument : lookup.getArguments()) {
        row |= argument.dimension == KimLookupTable.Argument.Dimension.ROW;
        column |= argument.dimension == KimLookupTable.Argument.Dimension.COLUMN;
      }
      if (!row || !column) {
        throw new KlabValidationException("Two-way arguments must identify one row and one column");
      }
    }
    Artifact.Type resultType = null;
    for (var values : table.rows()) {
      for (var value : values) {
        if (resultType == null || resultType == Artifact.Type.VALUE) resultType = value.getType();
        else if (value.getType() != Artifact.Type.VALUE && value.getType() != resultType) {
          throw new KlabValidationException("A two-way table result matrix must have one value type");
        }
      }
    }
    ((KimLookupTableImpl) lookup)
        .setLookupType(resultType == null ? Artifact.Type.VALUE : resultType);
  }

  private static void validateDiscretization(KimClassification classification) {
    List<NumericRange> ranges = new ArrayList<>();
    for (var rule : classification.getClassifiers()) {
      KimClassifier classifier = rule.getSecond();
      if (classifier.isNegated() || classifier.getIntervalMatch() == null) {
        throw new KlabValidationException(
            "Discretization rules must be non-negated numeric intervals");
      }
      ranges.add(classifier.getIntervalMatch());
    }
    ranges.sort(Comparator.comparingDouble(NumericRange::getLowerBound));
    for (int i = 1; i < ranges.size(); i++) {
      NumericRange previous = ranges.get(i - 1);
      NumericRange current = ranges.get(i);
      int boundary = Double.compare(previous.getUpperBound(), current.getLowerBound());
      if (boundary != 0) {
        throw new KlabValidationException(
            boundary > 0 ? "Discretization intervals overlap" : "Discretization intervals have a gap");
      }
      if (previous.isUpperExclusive() == current.isLowerExclusive()) {
        throw new KlabValidationException(
            previous.isUpperExclusive()
                ? "Discretization intervals exclude their shared boundary"
                : "Discretization intervals overlap at their shared boundary");
      }
    }
  }
}
