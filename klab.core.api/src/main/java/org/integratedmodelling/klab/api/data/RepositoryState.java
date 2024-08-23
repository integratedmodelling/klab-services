package org.integratedmodelling.klab.api.data;

import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Result of a status git (or other SCMs) call on a repository. Comes with a Project when it's first retrieved
 * and with a ResourceSet when updates to a document or repository operations are invoked on the
 * {@link org.integratedmodelling.klab.api.services.ResourcesService} API.
 * {@link org.integratedmodelling.klab.api.view.modeler.navigation.NavigableContainer}s can use the repo
 * metadata to (re-)assess the status of each document and container for display or notification.
 */
public class RepositoryState {

    public enum Status {

        /**
         *
         */
        CLEAN,

        /**
         *
         */
        UNTRACKED,

        /**
         * Conflicts are present. At the folder or project level, this takes precedence as the overall status
         * over CHANGED.
         */
        CONFLICTED,

        /**
         *
         */
        MODIFIED,
        /**
         * When returned as overall project status, means that the project is connected to a repository but
         * has never been committed.
         */
        ADDED,

        /**
         * Won't apply to anything other than documents
         */
        REMOVED,

        /**
         * The caller has no authorization to inquire or perform the requested operation on the repository.
         */
        UNAUTHORIZED
    }

    /**
     * Basic compound operations that should be supported through server requests. For now implementing Git
     * command sequences, meant to be usable easily and safely by untrained or minimally trained users. When
     * these can't run due to conflict, they should report the problem without causing changes and advice
     * users to use the full Git implementation.
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
        HARD_RESET,

        MERGE_CHANGES_FROM
    }

    private Status overallStatus = Status.UNTRACKED;
    private Set<String> modifiedPaths = new HashSet<>();
    private Set<String> addedPaths = new HashSet<>();
    private Set<String> untrackedPaths = new HashSet<>();
    private Set<String> removedPaths = new HashSet<>();
    private Set<String> untrackedFolders = new HashSet<>();
    private Set<String> conflictingPaths = new HashSet<>();
    private Set<String> uncommittedPaths = new HashSet<>();
    private List<String> branchNames = new ArrayList<>();
    private List<Notification> notifications = new ArrayList<>();
    private String currentBranch;
    private URL repositoryUrl;

    public Set<String> getModifiedPaths() {
        return this.modifiedPaths;
    }

    public Set<String> getAddedPaths() {
        return addedPaths;
    }

    public void setAddedPaths(Set<String> addedPaths) {
        this.addedPaths = addedPaths;
    }

    public Set<String> getConflictingPaths() {
        return conflictingPaths;
    }

    public void setConflictingPaths(Set<String> conflictingPaths) {
        this.conflictingPaths = conflictingPaths;
    }

    public void setModifiedPaths(Set<String> modifiedPaths) {
        this.modifiedPaths = modifiedPaths;
    }

    /**
     * In normal situations this should only return one of UNTRACKED, UNAUTHORIZED, CLEAN, MODIFIED or
     * CONFLICTED.
     *
     * @return
     */
    public Status getOverallStatus() {
        return overallStatus;
    }

    public void setOverallStatus(Status overallStatus) {
        this.overallStatus = overallStatus;
    }

    public Set<String> getRemovedPaths() {
        return removedPaths;
    }

    public void setRemovedPaths(Set<String> removedPaths) {
        this.removedPaths = removedPaths;
    }

    public Set<String> getUncommittedPaths() {
        return uncommittedPaths;
    }

    public void setUncommittedPaths(Set<String> uncommittedPaths) {
        this.uncommittedPaths = uncommittedPaths;
    }

    public Set<String> getUntrackedFolders() {
        return untrackedFolders;
    }

    public void setUntrackedFolders(Set<String> untrackedFolders) {
        this.untrackedFolders = untrackedFolders;
    }

    public Set<String> getUntrackedPaths() {
        return untrackedPaths;
    }

    public void setUntrackedPaths(Set<String> untrackedPaths) {
        this.untrackedPaths = untrackedPaths;
    }

    public List<String> getBranchNames() {
        return branchNames;
    }

    public void setBranchNames(List<String> branchNames) {
        this.branchNames = branchNames;
    }

    public String getCurrentBranch() {
        return currentBranch;
    }

    public void setCurrentBranch(String currentBranch) {
        this.currentBranch = currentBranch;
    }

    public URL getRepositoryUrl() {
        return repositoryUrl;
    }

    public void setRepositoryUrl(URL repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

    public List<Notification> getNotifications() {
        return notifications;
    }

    public void setNotifications(List<Notification> notifications) {
        this.notifications = notifications;
    }

}
