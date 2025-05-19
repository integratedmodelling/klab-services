package org.integratedmodelling.klab.api.data;

import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.impl.NDCursor;
import org.integratedmodelling.klab.api.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class Cursors {

  // use to validate that there are as many varying dimensions as the passed parameters and they
  // have
  // the stated dimensionalities
  private static List<long[]> checkDimensions(Geometry geometry, int... requiredVaryingDimensionality) {
    int n = 0;
    List<long[]> ret = new ArrayList<>();
    for (var dimension : geometry.getDimensions().stream().filter(d -> d.size() > 1).toList()) {
      if (requiredVaryingDimensionality.length <= n
          || dimension.getShape().size() != requiredVaryingDimensionality[n]) {
        throw new KlabIllegalStateException("Requested cursor is incompatible with the scanned geometry");
      } else {
        ret.add(Utils.Numbers.longArrayFromCollection(dimension.getShape()));
      }
      n++;
    }
    return ret;
  }

}
