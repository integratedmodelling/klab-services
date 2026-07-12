package org.integratedmodelling.klab.api.lang.kactors;

import org.integratedmodelling.klab.api.lang.Statement;
import org.integratedmodelling.klab.api.lang.kim.KlabStatement;

/**
 * Any k.Actors code element, including whole behaviors. Actual statements are {@link
 * KActorsStatement} and represent individual executable instructions.
 *
 * @author Ferd
 */
public interface KActorsCodeStatement extends KlabStatement {

  String getTag();
}
