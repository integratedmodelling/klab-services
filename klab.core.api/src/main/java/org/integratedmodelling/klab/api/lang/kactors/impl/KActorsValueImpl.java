package org.integratedmodelling.klab.api.lang.kactors.impl;

import org.integratedmodelling.klab.api.data.ValueType;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.Arguments;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.Verb;
import org.integratedmodelling.klab.api.lang.kactors.KActorsValue;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

public class KActorsValueImpl extends KActorsCodeStatementImpl implements KActorsValue {

  @Serial private static final long serialVersionUID = 8055708952216648277L;

  private ValueType type;
  private Object statedValue;
  private boolean exclusive;
  private boolean deferred;
  private String cast;

  @Override
  public ValueType getType() {
    return this.type;
  }

  @Override
  public <T> T getValue(Class<T> cls) {
    if (statedValue == null) {
      return null;
    }
    if (cls.isInstance(statedValue)) {
      return cls.cast(statedValue);
    }
    throw new KlabIllegalStateException(
        "k.Actors value is a "
            + statedValue.getClass().getName()
            + ", not a "
            + cls.getName());
  }

  @Override
  public <T> T as(Class<? extends T> cls) {
    return statedValue == null ? null : cls.cast(statedValue);
  }

  public Object getStatedValue() {
    return statedValue;
  }

  @Override
  public boolean isExclusive() {
    return this.exclusive;
  }

  @Override
  public boolean isDeferred() {
    return this.deferred;
  }

  @Override
  public String getCast() {
    return cast;
  }

  public void setType(ValueType type) {
    this.type = type;
  }

  public void setStatedValue(Object statedValue) {
    this.statedValue = statedValue;
  }

  public void setExclusive(boolean exclusive) {
    this.exclusive = exclusive;
  }

  public void setDeferred(boolean deferred) {
    this.deferred = deferred;
  }

  public void setCast(String cast) {
    this.cast = cast;
  }

  @Override
  public <T> T format(CodeAppender<T> appender) {
    return null;
  }

  @Override
  public String getUrn() {
    return "";
  }

  @Override
  public void visit(Visitor visitor) {}

  public String toString() {
    return "kval[" + type + "=" + statedValue + "]";
  }
}
