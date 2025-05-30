//package org.integratedmodelling.klab.runtime.kactors.messages;
//
//import org.integratedmodelling.klab.api.services.runtime.kactors.VM;
//
//import java.io.Serializable;
//
///**
// * Set an agent's state
// *
// * @author Ferd
// *
// */
//public class SetState implements Serializable, VM.AgentMessage {
//
//	private static final long serialVersionUID = -4670805652995373573L;
//
//	private String key;
//	private Object value;
//
//	public SetState() {}
//
//	public SetState(String key, Object value) {
//		this.key = key;
//		this.value = value;
//	}
//
//	public String getKey() {
//		return key;
//	}
//
//	public void setKey(String key) {
//		this.key = key;
//	}
//
//	public Object getValue() {
//		return value;
//	}
//
//	public void setValue(Object value) {
//		this.value = value;
//	}
//
//}
