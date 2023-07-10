package org.integratedmodelling.klab.services.resources.assets;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.MetadataConvention;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.services.runtime.Notification;

public class ProjectImpl implements Project {

	private static final long serialVersionUID = 7618524077068234748L;

	public static class ManifestImpl implements Manifest {

		private static final long serialVersionUID = -6549113149802016133L;
		private String description;
		private ResourcePrivileges privileges = ResourcePrivileges.PUBLIC;
		private Version version = Version.EMPTY_VERSION;
		private Collection<MetadataConvention> metadataConventions = new HashSet<>();
		private List<Pair<String, Version>> prerequisiteProjects = new ArrayList<>();
		private List<Pair<String, Version>> prerequisiteComponents = new ArrayList<>();
		private String worldview;
		private String definedWorldview;

		@Override
		public String getDescription() {
			return this.description;
		}

		@Override
		public String getWorldview() {
			return this.worldview;
		}

		@Override
		public String getDefinedWorldview() {
			return this.definedWorldview;
		}

		@Override
		public ResourcePrivileges getPrivileges() {
			return this.privileges;
		}

		@Override
		public Version getVersion() {
			return this.version;
		}

		@Override
		public Collection<MetadataConvention> getMetadataConventions() {
			return this.metadataConventions;
		}

		@Override
		public List<Pair<String, Version>> getPrerequisiteProjects() {
			return this.prerequisiteProjects;
		}

		@Override
		public List<Pair<String, Version>> getPrerequisiteComponents() {
			return this.prerequisiteComponents;
		}

		public void setDescription(String description) {
			this.description = description;
		}

		public void setPrivileges(ResourcePrivileges privileges) {
			this.privileges = privileges;
		}

		public void setVersion(Version version) {
			this.version = version;
		}

		public void setMetadataConventions(Collection<MetadataConvention> metadataConventions) {
			this.metadataConventions = metadataConventions;
		}

		public void setPrerequisiteProjects(List<Pair<String, Version>> prerequisiteProjects) {
			this.prerequisiteProjects = prerequisiteProjects;
		}

		public void setPrerequisiteComponents(List<Pair<String, Version>> prerequisiteComponents) {
			this.prerequisiteComponents = prerequisiteComponents;
		}

		public void setWorldview(String worldview) {
			this.worldview = worldview;
		}

		public void setDefinedWorldview(String definedWorldview) {
			this.definedWorldview = definedWorldview;
		}

	}

	private Manifest manifest;
	private Metadata metadata = Metadata.create();
	private String name;
	private URL url;
	private List<KimNamespace> namespaces = new ArrayList<>();
	private List<KActorsBehavior> behaviors = new ArrayList<>();
	private List<KActorsBehavior> apps = new ArrayList<>();
	private List<KActorsBehavior> testCases = new ArrayList<>();
	private List<Notification> notifications = new ArrayList<>();
	private List<String> resourceUrns = new ArrayList<>();

	@Override
	public Manifest getManifest() {
		return this.manifest;
	}

	@Override
	public Metadata getMetadata() {
		return this.metadata;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public URL getURL() {
		return this.url;
	}

	@Override
	public List<KimNamespace> getNamespaces() {
		return this.namespaces;
	}

	@Override
	public List<KActorsBehavior> getBehaviors() {
		return this.behaviors;
	}

	@Override
	public List<KActorsBehavior> getApps() {
		return this.apps;
	}

	@Override
	public List<KActorsBehavior> getTestCases() {
		return this.testCases;
	}

	@Override
	public List<Notification> getNotifications() {
		return this.notifications;
	}

	public URL getUrl() {
		return url;
	}

	public void setUrl(URL url) {
		this.url = url;
	}

	public void setManifest(Manifest manifest) {
		this.manifest = manifest;
	}

	public void setMetadata(Metadata metadata) {
		this.metadata = metadata;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setNamespaces(List<KimNamespace> namespaces) {
		this.namespaces = namespaces;
	}

	public void setBehaviors(List<KActorsBehavior> behaviors) {
		this.behaviors = behaviors;
	}

	public void setApps(List<KActorsBehavior> apps) {
		this.apps = apps;
	}

	public void setTestCases(List<KActorsBehavior> testCases) {
		this.testCases = testCases;
	}

	public void setNotifications(List<Notification> notifications) {
		this.notifications = notifications;
	}

	public List<String> getResourceUrns() {
		return resourceUrns;
	}

	public void setResourceUrns(List<String> resourceUrns) {
		this.resourceUrns = resourceUrns;
	}

}
