/**
 * Copyright 2013 BigML
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package org.integratedmodelling.klab.data.histogram;

import java.io.Serial;

public class BinUpdateException extends Exception {

	@Serial
	private static final long serialVersionUID = -6432862697096059078L;

	public BinUpdateException(String message) {
		super(message);
	}
}
