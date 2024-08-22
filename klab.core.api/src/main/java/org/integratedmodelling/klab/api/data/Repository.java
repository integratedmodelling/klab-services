//package org.integratedmodelling.klab.api.data;
//
//import org.integratedmodelling.klab.api.services.runtime.Notification;
//
//import java.net.URL;
//import java.util.ArrayList;
//import java.util.List;
//
///**
// * Descriptor for metadata related to any <em>remote</em> repository an asset may come from. Reported by
// * {@link org.integratedmodelling.klab.api.knowledge.organization.Project} and
// * {@link org.integratedmodelling.klab.api.lang.kim.KlabDocument} and kept in sync by the
// * {@link org.integratedmodelling.klab.api.services.ResourcesService}. Basic, simplified repository operations
// * are available through the {@link org.integratedmodelling.klab.api.ServicesAPI.RESOURCES.ADMIN} endpoints.
// */
//public interface Repository {
//
//    enum Status {
//        CLEAN,
//        UNTRACKED,
//        MODIFIED,
//        /**
//         * Just used with entire projects, which report having a repository by returning this instead of
//         * null.
//         */
//        TRACKED
//    }
//
//    /**
//     * Basic compound operations that should be supported through server requests. For now implementing Git
//     * command sequences, meant to be usable easily and safely by untrained or minimally trained users. When
//     * these can't run due to conflict, they should report the problem without causing changes and advice
//     * users to use the full Git implementation.
//     *
//     * <p>
//     * For now only COMMIT_AND_SWITCH expects a parameter, which will be passed in the parameters array.
//     */
//    public enum Operation {
//        /**
//         * Fetch any remote changes, if no conflicts merge them, then commit the current changes and pull
//         */
//        FETCH_COMMIT_AND_PUSH,
//
//        /**
//         * Fetch remote changes and merge them if no conflicts arise
//         */
//        FETCH_AND_MERGE,
//        /**
//         * Switch to another branch (possibly new) after locally committing any pending changes
//         */
//        COMMIT_AND_SWITCH,
//        /**
//         * Hard reset head deleting all uncommitted changes
//         */
//        HARD_RESET,
//
//        MERGE_CHANGES_FROM
//    }
//
//    /**
//     * Compound repository operations (as implemented in Utils.Git in the common package) return one of these,
//     * which contains notifications (they should be checked for errors before anything else is done) and the
//     * relative paths that were affected. When changes affect a
//     * {@link org.integratedmodelling.klab.api.knowledge.organization.Workspace}, they can be converted into
//     * {@link org.integratedmodelling.klab.api.services.resources.ResourceSet} by a resources server that
//     * knows mutual dependencies.
//     */
//    public static class Modifications {
//
//        private String repositoryName;
//
//        private List<String> addedPaths = new ArrayList<>();
//        private List<String> removedPaths = new ArrayList<>();
//        private List<String> modifiedPaths = new ArrayList<>();
//        private List<Notification> notifications = new ArrayList<>();
//
//        public List<String> getAddedPaths() {
//            return addedPaths;
//        }
//
//        public void setAddedPaths(List<String> addedPaths) {
//            this.addedPaths = addedPaths;
//        }
//
//        public List<String> getRemovedPaths() {
//            return removedPaths;
//        }
//
//        public void setRemovedPaths(List<String> removedPaths) {
//            this.removedPaths = removedPaths;
//        }
//
//        public List<String> getModifiedPaths() {
//            return modifiedPaths;
//        }
//
//        public void setModifiedPaths(List<String> modifiedPaths) {
//            this.modifiedPaths = modifiedPaths;
//        }
//
//        public List<Notification> getNotifications() {
//            return notifications;
//        }
//
//        public void setNotifications(List<Notification> notifications) {
//            this.notifications = notifications;
//        }
//
//        public String getRepositoryName() {
//            return repositoryName;
//        }
//
//        public void setRepositoryName(String repositoryName) {
//            this.repositoryName = repositoryName;
//        }
//    }
//
//    Status getStatus();
//
//    URL getRepositoryUrl();
//
//    String getCurrentBranch();
//
//    List<String> getBranches();
//
//}
