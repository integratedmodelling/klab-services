/**
 * Copyright 2013 BigML
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package org.integratedmodelling.klab.data.histogram;

import java.io.Serial;

public class MixedInsertException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 4335186224822484731L;

	public MixedInsertException() {
		super("Can't mix insert types");
	}
}
