/**
 * Copyright 2013 BigML
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package org.integratedmodelling.klab.data.histogram;

import java.io.Serial;

public class SumOutOfRangeException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 987685669915428993L;

	public SumOutOfRangeException(String string) {
		super(string);
	}
}
