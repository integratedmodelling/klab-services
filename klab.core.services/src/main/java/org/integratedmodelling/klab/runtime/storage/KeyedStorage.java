package org.integratedmodelling.klab.runtime.storage;

import org.integratedmodelling.klab.api.geometry.Offset;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;

/**
 * Base storage providing the general methods. Children enable either boxed I/O
 * or faster native operation (recommended). The runtime makes the choice.
 * 
 * @author Ferd
 *
 */
public class KeyedStorage extends IntStorage {


    public KeyedStorage(Scale scale, StorageScope scope) {
        super(scale, scope);
    }

    @Override
	public Type getType() {
		return Type.KEYED;
	}

	public void set(Object value, Offset locator) {
		
	}

}
