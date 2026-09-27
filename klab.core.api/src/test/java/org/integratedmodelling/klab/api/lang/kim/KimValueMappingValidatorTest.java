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
