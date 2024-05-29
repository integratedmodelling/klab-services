package org.integratedmodelling.klab.runtime.kactors.messages;

import org.integratedmodelling.klab.api.lang.kactors.beans.TestStatistics;
import org.integratedmodelling.klab.api.services.runtime.kactors.VM;

public class ScriptEvent implements VM.AgentMessage {

	private static final long serialVersionUID = -281452849932984836L;

	public enum Type {
		CASE_START, CASE_END, TEST_START, TEST_END, SCRIPT_START, SCRIPT_END
	}

	private Type type;
	private String id;
	private TestStatistics data;

	public ScriptEvent() {
	}

	public ScriptEvent(String name, Type type) {
		this.id = name;
		this.type = type;
	}

	public ScriptEvent(String name, Type type, TestStatistics data) {
		this.id = name;
		this.type = type;
		this.data = data;
	}

	public Type getType() {
		return type;
	}

	public void setType(Type type) {
		this.type = type;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public TestStatistics getData() {
		return data;
	}

	public void setData(TestStatistics data) {
		this.data = data;
	}

	@Override
	public String toString() {
		return "ScriptEvent [type=" + type + ", id=" + id + ", data=" + data + "]";
	}

}
