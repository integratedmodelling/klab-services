/**
 * Copyright 2013 BigML
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package org.integratedmodelling.klab.data.histogram;

import java.text.DecimalFormat;
import org.json.simple.JSONArray;

public class SumResult<T extends Target<T>> {
  public SumResult(double count, T targetSum) {
    _count = count;
    _targetSum = targetSum;
  }
  
  public double getCount() {
    return _count;
  }
  
  public T getTargetSum() {
    return _targetSum;
  }
  
  @SuppressWarnings("unchecked")
  public JSONArray toJSON(DecimalFormat format) {
    JSONArray jsonArray = new JSONArray();
    jsonArray.add(Utils.roundNumber(_count, format));
    _targetSum.addJSON(jsonArray, format);
    return jsonArray;
  }
  
  @Override
  public String toString() {
    return toJSON(new DecimalFormat(SPDTHistogram.DEFAULT_FORMAT_STRING)).toString();
  }
  
  private final double _count;
  private final T _targetSum;
}
