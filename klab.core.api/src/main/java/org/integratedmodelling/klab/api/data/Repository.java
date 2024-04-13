package org.integratedmodelling.klab.api.data;

import java.net.URL;
import java.util.List;

/**
 * Descriptor for metadata related to any <em>remote</em> repository an asset may come from. Reported by
 * {@link org.integratedmodelling.klab.api.knowledge.organization.Project} and
 * {@link org.integratedmodelling.klab.api.lang.kim.KlabDocument} and kept in sync by the
 * {@link org.integratedmodelling.klab.api.services.ResourcesService}. Basic, simplified repository operations
 * are available through the {@link org.integratedmodelling.klab.api.ServicesAPI.RESOURCES.ADMIN} endpoints.
 */
public interface Repository {

    enum Status {
        CLEAN,
        UNTRACKED,
        MODIFIED,
        /**
         * Just for entire projects, which report having a repository by returning this instead of null.
         */
        TRACKED
    }

    /**
     * Basic operations supported through server requests. For now just the simplest Git commands, meant to be
     * usable by minimally schooled apes.
     * <p>
     * Only SWITCH_BRANCH expects a parameter, which will be passed in the parameters array.
     */
    public enum Operation {
        FETCH_COMMIT_AND_PUSH,
        PULL,
        SWITCH_BRANCH,
        HARD_RESET
    }

    Status getStatus();

    URL getRepositoryUrl();

    String getCurrentBranch();

    List<String> getBranches();
}
