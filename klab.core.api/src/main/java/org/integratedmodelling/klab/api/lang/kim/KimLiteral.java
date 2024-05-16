package org.integratedmodelling.klab.api.lang.kim;

import org.integratedmodelling.klab.api.data.ValueType;

public interface KimLiteral extends KimAsset {

    ValueType getValueType();

    <T> T get(Class<? extends T> valueClass);


}
