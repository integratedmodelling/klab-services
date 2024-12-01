package org.integratedmodelling.klab.api.data;

import java.lang.annotation.*;

/**
 * Used to tag arguments that will be changed by the call that annotates them when passed as parameters.
 */
@Documented
@Target(ElementType.PARAMETER)
public @interface Mutable {
}
