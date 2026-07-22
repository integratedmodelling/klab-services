package org.integratedmodelling.klab.api.lang.kactors;

import org.integratedmodelling.klab.api.lang.kim.KlabStatement;

/**
 * Any k.Actors code element, including whole behaviors. Actual statements are {@link
 * KActorsStatement} and represent individual executable instructions.
 *
 * @author Ferd
 */
public interface KActorsCodeStatement extends KlabStatement {

  /**
   * The tag (assigned through a <code>#tag</code> in k.Actors code) enables naming a statement so
   * that it can be referred to later. This isn't used in normal control flow but it becomes
   * important when the code structure is used to extract layouts (like in applications) or other
   * conventions that match objects to groups and statements that must be referred to later.
   *
   * @return
   */
  String getTag();
}
