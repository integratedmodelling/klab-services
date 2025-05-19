package org.integratedmodelling.klab.modeler.configuration;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;

/**
 * Store the configuration of the editors for a resources workspace. Instances
 * of these will be stored in a map indexed by serviceId:workspaceId to remember
 * the workspace configuration.
 */
public class WorkbenchConfiguration implements Serializable {

	@Serial
	private static final long serialVersionUID = 3535564232433347626L;

	public static class DocumentConfiguration {

		private String documentUrn;
		private KnowledgeClass documentType;
		private boolean readOnly;
		private int caretPosition;
		private boolean active;

		public String getDocumentUrn() {
			return documentUrn;
		}

		public void setDocumentUrn(String documentUrn) {
			this.documentUrn = documentUrn;
		}

		public KnowledgeClass getDocumentType() {
			return documentType;
		}

		public void setDocumentType(KnowledgeClass documentType) {
			this.documentType = documentType;
		}

		public boolean isReadOnly() {
			return readOnly;
		}

		public void setReadOnly(boolean readOnly) {
			this.readOnly = readOnly;
		}

		public int getCaretPosition() {
			return caretPosition;
		}

		public void setCaretPosition(int caretPosition) {
			this.caretPosition = caretPosition;
		}

		public boolean isActive() {
			return active;
		}

		public void setActive(boolean active) {
			this.active = active;
		}

	}

	private long lastSaveTimestamp;
	private boolean lastOpenedWorkspace;
	private List<DocumentConfiguration> configuration = new ArrayList<>();

	public long getLastSaveTimestamp() {
		return lastSaveTimestamp;
	}

	public void setLastSaveTimestamp(long lastSaveTimestamp) {
		this.lastSaveTimestamp = lastSaveTimestamp;
	}

	public List<DocumentConfiguration> getConfiguration() {
		return configuration;
	}

	public void setConfiguration(List<DocumentConfiguration> configuration) {
		this.configuration = configuration;
	}

	public boolean isLastOpenedWorkspace() {
		return lastOpenedWorkspace;
	}

	public void setLastOpenedWorkspace(boolean lastOpenedWorkspace) {
		this.lastOpenedWorkspace = lastOpenedWorkspace;
	}

}
