package org.integratedmodelling.klab.runtime.kactors.messages;

import java.io.Serializable;

import org.integratedmodelling.klab.api.collections.Literal;
import org.integratedmodelling.klab.api.services.runtime.kactors.VM;

/**
 * Set an agent's state
 * 
 * @author Ferd
 *
 */
public class SetState implements Serializable, VM.AgentMessage {

	private static final long serialVersionUID = -4670805652995373573L;

	private String key;
	private Literal value;

	public SetState() {}
	
	public SetState(String key, Object value) {
		this.key = key;
		this.value = Literal.of(value);
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public Literal getValue() {
		return value;
	}

	public void setValue(Literal value) {
		this.value = value;
	}

}
