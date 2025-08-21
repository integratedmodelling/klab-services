/*
R * This file is part of k.LAB.
 * 
 * k.LAB is free software: you can redistribute it and/or modify
 * it under the terms of the Affero GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * A copy of the GNU Affero General Public License is distributed in the root
 * directory of the k.LAB distribution (LICENSE.txt). If this cannot be found 
 * see <http://www.gnu.org/licenses/>.
 * 
 * Copyright (C) 2007-2018 integratedmodelling.org and any authors mentioned
 * in author tags. All rights reserved.
 */
package org.integratedmodelling.klab.api.exceptions;

/**
 * An unchecked exception reserved for situations that should never happen in a
 * production environment. To be used in k.LAB code instead of Java's
 * IllegalArgumentException for ease of debugging.
 *
 * @author ferdinando.villa
 * @version $Id: $Id
 */
public class KlabIllegalArgumentException extends KlabException {

	private static final long serialVersionUID = 461213337593957416L;

	/**
	 * Instantiates a new klab illegal status exception.
	 */
	public KlabIllegalArgumentException() {
		super();
	}

	/**
	 * Instantiates a new klab illegal status exception.
	 *
	 * @param message the detail message
	 */
	public KlabIllegalArgumentException(String message) {
		super(message);
	}

	/**
	 * Instantiates a new klab illegal status exception.
	 *
	 * @param cause the cause
	 */
	public KlabIllegalArgumentException(Throwable cause) {
		super(cause);
	}

	/**
	 * Instantiates a new klab illegal status exception.
	 *
	 * @param message Error message
	 * @param cause the cause
	 *
	 */
	public KlabIllegalArgumentException(String message, Throwable cause) {
		super(message, cause);
	}


}
