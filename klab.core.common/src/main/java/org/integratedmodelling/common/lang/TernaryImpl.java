package org.integratedmodelling.common.lang;

import java.io.Serial;
import org.integratedmodelling.klab.api.lang.Ternary;

/** Default mutable implementation used by language adapters. */
public class TernaryImpl implements Ternary {

  @Serial private static final long serialVersionUID = -7553273640377806738L;

  private Object condition;
  private Object trueCase;
  private Object falseCase;

  @Override
  public Object getCondition() {
    return condition;
  }

  @Override
  public Object getTrueCase() {
    return trueCase;
  }

  @Override
  public Object getFalseCase() {
    return falseCase;
  }

  public void setCondition(Object condition) {
    this.condition = condition;
  }

  public void setTrueCase(Object trueCase) {
    this.trueCase = trueCase;
  }

  public void setFalseCase(Object falseCase) {
    this.falseCase = falseCase;
  }
}
