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
     * Basic operations supported through server requests. For now implementing Git command sequences, meant
     * to be usable easily and safely by untrained or minimally trained users. When these can't run due to
     * conflict, they should report the problem without causing changes and advice users to use the full Git
     * implementation.
     *
     * <p>
     * For now only COMMIT_AND_SWITCH expects a parameter, which will be passed in the parameters array.
     */
    public enum Operation {
        /**
         * Fetch any remote changes, if no conflicts merge them, then commit the current changes and pull
         */
        FETCH_COMMIT_AND_PUSH,

        /**
         * Fetch remote changes and merge them if no conflicts arise
         */
        FETCH_AND_MERGE,
        /**
         * Switch to another branch (possibly new) after locally committing any pending changes
         */
        COMMIT_AND_SWITCH,
        /**
         * Hard reset head deleting all uncommitted changes
         */
        HARD_RESET
    }

    Status getStatus();

    URL getRepositoryUrl();

    String getCurrentBranch();

    List<String> getBranches();
}
