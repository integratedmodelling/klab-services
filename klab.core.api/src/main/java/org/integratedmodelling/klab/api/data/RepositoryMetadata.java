package org.integratedmodelling.klab.api.data;

import java.net.URL;

/**
 * Descriptor for metadata related to the remote repository an asset may come from. Reported by
 * {@link org.integratedmodelling.klab.api.knowledge.organization.Project} and
 * {@link org.integratedmodelling.klab.api.lang.kim.KlabDocument} and kept in sync by the
 * {@link org.integratedmodelling.klab.api.services.ResourcesService}.
 */
public interface RepositoryMetadata {

    enum Status {
        CLEAN,
        UNTRACKED,
        MODIFIED
    }

    Status getStatus();

    URL getRepositoryUrl();

    String getCurrentBranch();

}
