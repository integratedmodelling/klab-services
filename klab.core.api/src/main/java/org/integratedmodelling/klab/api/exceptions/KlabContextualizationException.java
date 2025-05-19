/*
 * This file is part of k.LAB.
 *
 * k.LAB is free software: you can redistribute it and/or modify it under the terms of the Affero
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * A copy of the GNU Affero General Public License is distributed in the root directory of the k.LAB
 * distribution (LICENSE.txt). If this cannot be found see <http://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2007-2018 integratedmodelling.org and any authors mentioned in author tags. All
 * rights reserved.
 */
package org.integratedmodelling.klab.api.exceptions;

// TODO: Auto-generated Javadoc

import java.io.Serial;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;

/**
 * The Class KlabValidationException.
 *
 * @author ferdinando.villa
 * @version $Id: $Id
 */
public class KlabContextualizationException extends KlabException {

  @Serial private static final long serialVersionUID = 461213337593957416L;

  private Observation observation;

  public KlabContextualizationException() {}

  /** Instantiates a new klab validation exception. */
  public KlabContextualizationException(Observation observation) {
    super("Exception contextualizing " + observation);
    this.observation = observation;
  }

  /**
   * Instantiates a new klab validation exception.
   *
   * @param arg0 the arg 0
   */
  public KlabContextualizationException(Observation observation, String arg0) {
    super(arg0);
    this.observation = observation;
  }

  /**
   * Instantiates a new klab validation exception.
   *
   * @param e the e
   */
  public KlabContextualizationException(Observation observation, Throwable e) {
    super(e);
    this.observation = observation;
  }

  public Observation getObservation() {
    return observation;
  }

  public void setObservation(Observation observation) {
    this.observation = observation;
  }
}
