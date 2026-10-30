package org.integratedmodelling.klab.api.lang.kim;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.integratedmodelling.klab.api.collections.impl.PairImpl;
import org.integratedmodelling.klab.api.data.mediation.impl.NumericRangeImpl;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.lang.kim.impl.KimClassificationImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimClassifierImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimLookupTableImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimTableImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimValueMappingValidator;
import org.junit.jupiter.api.Test;

class KimValueMappingValidatorTest {

  @Test
  void rejectsNonRectangularLookupTables() {
    var table = new KimTableImpl();
    table.setRows(List.of(new KimClassifier[] {number(1), number(2)}, new KimClassifier[] {number(3)}));
    // setRows derives width from the first row, leaving validation to inspect every row.
    var lookup = new KimLookupTableImpl();
    lookup.setTable(table);
    assertThrows(
        KlabValidationException.class,
        () -> KimValueMappingValidator.validateAndNormalize(lookup, true));
  }

  @Test
  void validatesContiguousDiscretizationsAndRejectsGapsAndOverlaps() {
    assertDoesNotThrow(() -> KimValueMappingValidator.validate(discretization(range(null, 0.0, false), range(0.0, null, true))));
    assertThrows(
        KlabValidationException.class,
        () -> KimValueMappingValidator.validate(discretization(range(null, 0.0, false), range(1.0, null, true))));
    assertThrows(
        KlabValidationException.class,
        () -> KimValueMappingValidator.validate(discretization(range(null, 0.0, false), range(0.0, null, false))));
  }

  @Test
  void validatesCoverageAndEveryEndpointWithoutConstrainingClassifications() {
    var partition = discretization(range(0.0, 5.0, false), range(5.0, 10.0, true));
    assertDoesNotThrow(() -> KimValueMappingValidator.validateDiscretization(partition,
        new NumericRangeImpl(0.0, 10.0, false, false)));
    for (var extent : List.of(new NumericRangeImpl(-1.0, 10.0, false, false),
        new NumericRangeImpl(0.0, 11.0, false, false)))
      assertThrows(KlabValidationException.class,
          () -> KimValueMappingValidator.validateDiscretization(partition, extent));
    assertDoesNotThrow(() -> KimValueMappingValidator.validateDiscretization(partition,
        new NumericRangeImpl(0.0, 10.0, true, false)));
    assertThrows(KlabValidationException.class,
        () -> KimValueMappingValidator.validateDiscretization(partition, null));
    var gap = discretization(range(0.0, 4.0, false), range(5.0, 10.0, true));
    assertThrows(KlabValidationException.class, () -> KimValueMappingValidator.validateDiscretization(gap,
        new NumericRangeImpl(0.0, 10.0, false, false)));
    ((KimClassificationImpl) gap).setDiscretization(false);
    assertDoesNotThrow(() -> KimValueMappingValidator.validate(gap));
    assertThrows(KlabValidationException.class,
        () -> KimValueMappingValidator.validate(discretization(range(5.0, 5.0, true))));
    assertThrows(KlabValidationException.class,
        () -> KimValueMappingValidator.validate(discretization(range(Double.NaN, 5.0, false))));
    var doubleExcluded = new KimClassifierImpl();
    doubleExcluded.setIntervalMatch(new NumericRangeImpl(0.0, 5.0, false, true));
    assertThrows(KlabValidationException.class, () -> KimValueMappingValidator.validate(
        discretization(doubleExcluded, range(5.0, 10.0, true))));
  }
  @Test
  void discretizationClipsToApplicabilityAndAllowsOverhangAndOutsideGaps() {
    var first = new KimClassifierImpl();
    first.setIntervalMatch(new NumericRangeImpl(-5.0, 5.0, false, true));
    var partition = discretization(first, range(5.0, 15.0, false));
    var applicability = new NumericRangeImpl(0.0, 10.0, false, false);
    assertDoesNotThrow(() -> KimValueMappingValidator.validateDiscretization(partition, applicability));
    var outsideGap = discretization(range(-20.0, -10.0, false), range(-5.0, 15.0, false));
    assertDoesNotThrow(() -> KimValueMappingValidator.validateDiscretization(outsideGap, applicability));
    var overlap = discretization(range(-5.0, 5.0, false), range(5.0, 15.0, false));
    assertThrows(KlabValidationException.class, () -> KimValueMappingValidator.validateDiscretization(overlap, applicability));
    var missingEndpoint = discretization(range(0.0, 15.0, true));
    assertThrows(KlabValidationException.class, () -> KimValueMappingValidator.validateDiscretization(missingEndpoint, applicability));
  }
  private static KimClassification discretization(KimClassifier... classifiers) {
    var ret = new KimClassificationImpl();
    ret.setDiscretization(true);
    for (var classifier : classifiers) {
      ret.getClassifiers().add(new PairImpl<>(new KimConceptImpl(), classifier));
    }
    return ret;
  }

  private static KimClassifier range(Double lower, Double upper, boolean lowerExclusive) {
    var ret = new KimClassifierImpl();
    ret.setIntervalMatch(new NumericRangeImpl(lower, upper, lowerExclusive, upper == null));
    ret.setType(Artifact.Type.NUMBER);
    return ret;
  }

  private static KimClassifier number(double value) {
    var ret = new KimClassifierImpl();
    ret.setNumberMatch(value);
    ret.setType(Artifact.Type.NUMBER);
    return ret;
  }
}
