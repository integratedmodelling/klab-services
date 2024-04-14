package org.integratedmodelling.klab.utilities;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.Statement;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.data.Notification;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Utils extends org.integratedmodelling.common.utils.Utils {

    public static class Annotations {

        public static boolean hasAnnotation(Observable observable, String s) {
            for (Annotation annotation : observable.getAnnotations()) {
                if (annotation.getName().equals(s)) {
                    return true;
                }
            }
            return false;
        }

        public static Annotation getAnnotation(Observable observable, String s) {
            for (Annotation annotation : observable.getAnnotations()) {
                if (annotation.getName().equals(s)) {
                    return annotation;
                }
            }
            return null;
        }

        public static boolean hasAnnotation(Statement object, String s) {
            for (Annotation annotation : object.getAnnotations()) {
                if (annotation.getName().equals(s)) {
                    return true;
                }
            }
            return false;
        }

        public static Annotation getAnnotation(Statement object, String s) {
            for (Annotation annotation : object.getAnnotations()) {
                if (annotation.getName().equals(s)) {
                    return annotation;
                }
            }
            return null;
        }

        /**
         * Shorthand to check whether the default parameter (list or individual value) of an annotation
         * contains the passed string.
         *
         * @param string
         * @return
         */
        public static boolean defaultsContain(Annotation annotation, String string) {
            if (annotation.get(ServiceCall.DEFAULT_PARAMETER_NAME) instanceof List) {
                return ((List<?>) annotation.get(ServiceCall.DEFAULT_PARAMETER_NAME)).contains(string);
            } else if (annotation.get(ServiceCall.DEFAULT_PARAMETER_NAME) != null) {
                return annotation.get(ServiceCall.DEFAULT_PARAMETER_NAME).equals(string);
            }
            return false;
        }

        /**
         * Simple methods that are messy to keep writing explicitly
         *
         * @param annotations
         * @param id
         * @return
         */
        public static Annotation getAnnotation(List<Annotation> annotations, String id) {
            for (Annotation annotation : annotations) {
                if (id.equals(annotation.getName())) {
                    return annotation;
                }
            }
            return null;
        }

        public static Parameters<String> collectVariables(List<Annotation> annotations) {
            Parameters<String> ret = Parameters.create();
            for (Annotation annotation : annotations) {
                if ("var".equals(annotation.getName())) {
                    for (String key : annotation.getNamedKeys()) {
                        ret.put(key, annotation.get(key));
                    }
                }
            }
            return ret;
        }

        /**
         * Collect the annotations from an k.IM object and its semantic lineage, ensuring that downstream
         * annotations of the same name override those upstream. Any string parameter filters the annotations
         * collected.
         *
         * @param objects
         * @return all annotations from upstream
         */
        public static Collection<Annotation> collectAnnotations(Object... objects) {

            Map<String, Annotation> ret = new HashMap<>();
            for (Object object : objects) {
                if (object instanceof KlabAsset) {
                    collectAnnotations((Knowledge) object, ret);
                } else if (object instanceof Statement) {
                    collectAnnotations((Statement) object, ret);
                } else if (object instanceof Artifact) {
                    for (Annotation annotation : ((Artifact) object).getAnnotations()) {
                        if (!ret.containsKey(annotation.getName())) {
                            ret.put(annotation.getName(), annotation);
                        }
                    }
                }
            }
            return ret.values();
        }

        /**
         * Collect the annotations from anything semantic lineage, ensuring that downstream annotations of the
         * same name override those upstream.
         *
         * @param object
         * @return all annotations from upstream
         */
        public static Collection<Annotation> collectAnnotations(KlabAsset object) {
            Map<String, Annotation> ret = new HashMap<>();
            collectAnnotations(object, ret);
            return ret.values();
        }

        private static void collectAnnotations(KlabAsset object, Map<String, Annotation> collection) {

            //            for (Annotation annotation : object.getAnnotations()) {
            //                if (!collection.containsKey(annotation.getName())) {
            //                    Annotation a = new AnnotationImpl(annotation);
            //                    collection.put(a.getName(), a);
            //                }
            //            }

            if (object instanceof KimObservable) {

                // /*
                // * collect from roles, traits and main in this order
                // */
                // // for (IConcept role : Roles.INSTANCE.getRoles(((IObservable)
                // // object).getType())) {
                // // collectAnnotations(role, collection);
                // // }
                // for (IConcept trait : Traits.INSTANCE.getTraits(((IObservable)
                // object).getType())) {
                // // FIXME REMOVE ugly hack: landcover is a type, but it's used as an attribute in
                // // various places so the change
                // // is deep. This makes landcover colormaps end up in places they shouldn't be.
                // // TODO check - may not be relevant anymore now that landcover is correctly a
                // type of and not a trait.
                // if (!trait.getNamespace().equals("landcover")) {
                // collectAnnotations(trait, collection);
                // }
                // }
                //
                // collectAnnotations(((IObservable) object).getType(), collection);
            } else if (object instanceof KimConcept) {
                // IKimObject mobject = Resources.INSTANCE.getModelObject(object.toString());
                // if (mobject != null) {
                // collectAnnotations(mobject, collection);
                // }
                // if (((IConcept) object).is(Type.CLASS)) {
                // // collect annotations from what is classified
                // IConcept classified = Observables.INSTANCE.getDescribedType((IConcept) object);
                // if (classified != null) {
                // collectAnnotations(classified, collection);
                // }
                // }
                // for (IConcept parent : ((IConcept) object).getParents()) {
                // if (!CoreOntology.CORE_ONTOLOGY_NAME.equals(parent.getNamespace())) {
                // collectAnnotations(parent, collection);
                // }
                // }
            } else if (object instanceof Concept) {
                // TODO
            } else if (object instanceof Observable) {
                // TODO
            } else if (object instanceof Model) {
                collectAnnotations(((Model) object).getObservables().get(0), collection);
            } else if (object instanceof Instance) {
                collectAnnotations(((Instance) object).getObservable(), collection);
            }
            //
            // if (getParent(object) != null) {
            // collectAnnotations(object.getParent(), collection);
            // }

        }

        // private void collectAnnotations(Knowledge object, Map<String, IAnnotation> collection) {
        //
        // for (Annotation annotation : object.getAnnotations()) {
        // if (!collection.containsKey(annotation.getName())) {
        // collection.put(annotation.getName(), annotation);
        // }
        // }
        //
        // }
        //
        // private void collectAnnotations(ISemantic object, Map<String, IAnnotation> collection) {
        //
        // if (object instanceof IObservable) {
        //
        // for (IAnnotation annotation : ((IObservable)object).getAnnotations()) {
        // if (!collection.containsKey(annotation.getName())) {
        // collection.put(annotation.getName(), annotation);
        // }
        // }
        //
        // /*
        // * collect from roles, traits and main in this order
        // */
        // // for (IConcept role : Roles.INSTANCE.getRoles(((IObservable)
        // // object).getType())) {
        // // collectAnnotations(role, collection);
        // // }
        // for (IConcept trait : Traits.INSTANCE.getTraits(((IObservable) object).getType())) {
        // // FIXME REMOVE ugly hack: landcover is a type, but it's used as an attribute in
        // // various places so the change
        // // is deep. This makes landcover colormaps end up in places they shouldn't be.
        // // TODO check - may not be relevant anymore now that landcover is correctly a type of and
        // not a trait.
        // if (!trait.getNamespace().equals("landcover")) {
        // collectAnnotations(trait, collection);
        // }
        // }
        //
        // collectAnnotations(((IObservable) object).getType(), collection);
        //
        // } else if (object instanceof IConcept) {
        // IKimObject mobject = Resources.INSTANCE.getModelObject(object.toString());
        // if (mobject != null) {
        // collectAnnotations(mobject, collection);
        // }
        // if (((IConcept) object).is(Type.CLASS)) {
        // // collect annotations from what is classified
        // IConcept classified = Observables.INSTANCE.getDescribedType((IConcept) object);
        // if (classified != null) {
        // collectAnnotations(classified, collection);
        // }
        // }
        // for (IConcept parent : ((IConcept) object).getParents()) {
        // if (!CoreOntology.CORE_ONTOLOGY_NAME.equals(parent.getNamespace())) {
        // collectAnnotations(parent, collection);
        // }
        // }
        // }
        // }

    }


    public static class Classpath {

        /**
         * Extract the OWL assets in the classpath (under /knowledge/**) to the specified filesystem
         * directory.
         *
         * @param destinationDirectory
         * @throws IOException
         */
        public static void extractKnowledgeFromClasspath(File destinationDirectory) {
            try {
                PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
                org.springframework.core.io.Resource[] resources = resolver.getResources("/knowledge/**");
                for (org.springframework.core.io.Resource resource : resources) {

                    String path = null;
                    if (resource instanceof FileSystemResource) {
                        path = ((FileSystemResource) resource).getPath();
                    } else if (resource instanceof ClassPathResource) {
                        path = ((ClassPathResource) resource).getPath();
                    }
                    if (path == null) {
                        throw new KlabIOException("internal: cannot establish path for resource " + resource);
                    }

                    if (!path.endsWith("owl")) {
                        continue;
                    }

                    String filePath = path.substring(path.indexOf("knowledge/") + "knowledge/".length());

                    int pind = filePath.lastIndexOf('/');
                    if (pind >= 0) {
                        String fileDir = filePath.substring(0, pind);
                        File destDir = new File(destinationDirectory + File.separator + fileDir);
                        destDir.mkdirs();
                    }
                    File dest = new File(destinationDirectory + File.separator + filePath);
                    InputStream is = resource.getInputStream();
                    FileUtils.copyInputStreamToFile(is, dest);
                    is.close();
                }
            } catch (IOException ex) {
                throw new KlabIOException(ex);
            }
        }

        /**
         * Only works for a flat hierarchy!
         *
         * @param resourcePattern
         * @param destinationDirectory
         */
        public static void extractResourcesFromClasspath(String resourcePattern, File destinationDirectory) {

            try {
                PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
                org.springframework.core.io.Resource[] resources = resolver.getResources(resourcePattern);
                for (org.springframework.core.io.Resource resource : resources) {

                    String path = null;
                    if (resource instanceof FileSystemResource) {
                        path = ((FileSystemResource) resource).getPath();
                    } else if (resource instanceof ClassPathResource) {
                        path = ((ClassPathResource) resource).getPath();
                    }
                    if (path == null) {
                        throw new KlabIOException("internal: cannot establish path for resource " + resource);
                    }
                    String fileName = org.integratedmodelling.klab.api.utils.Utils.Files.getFileName(path);
                    File dest = new File(destinationDirectory + File.separator + fileName);
                    InputStream is = resource.getInputStream();
                    FileUtils.copyInputStreamToFile(is, dest);
                    is.close();
                }
            } catch (IOException ex) {
                throw new KlabIOException(ex);
            }
        }
    }

    public static class Files extends org.integratedmodelling.klab.api.utils.Utils.Files {

        static public final Set<String> JAVA_ARCHIVE_EXTENSIONS = Set.of("zip", "jar");

        public static void deleteDirectory(File pdir) {
            try {
                FileUtils.deleteDirectory(pdir);
            } catch (IOException e) {
                throw new KlabIOException(e);
            }
        }

        public static void touch(File file) {
            try {
                FileUtils.touch(file);
            } catch (IOException e) {
                throw new KlabIOException(e);
            }
        }

        public static boolean deleteQuietly(File pdir) {
            return FileUtils.deleteQuietly(pdir);
        }

        public static void copyDirectory(File directory, File backupDir) {
            try {
                FileUtils.copyDirectory(directory, backupDir);
            } catch (IOException e) {
                throw new KlabIOException(e);
            }
        }

        //        public static void writeStringToFile(String string, File file) {
        //            try {
        //                FileUtils.write(file, string, StandardCharsets.UTF_8);
        //            } catch (IOException e) {
        //                throw new KlabIOException(e);
        //            }
        //        }

    }

    public static class Git {

        public static final String MAIN_BRANCH = "master";

        /**
         * Perform a fetch, if no issues do a merge, then commit any changes and push to origin. Use any
         * credentials installed for the origin repository.
         *
         * @param localRepository
         * @return notifications describing any error. Empty notifications means all OK. May have no errors
         * but warnings, no info. Use {@link Utils.Notifications#hasErrors(Collection)} to check.
         */
        public static List<Notification> fetchCommitAndPush(File localRepository) {

            List<Notification> ret = new ArrayList<>();

            return ret;

        }

        /**
         * Perform a safe pull operations from origin, using any installed credentials.
         *
         * @param localRepository
         * @return notifications describing any error. Empty notifications means all OK. May have no errors
         * but warnings, no info. Use {@link Utils.Notifications#hasErrors(Collection)} to check.
         */
        public static List<Notification> fetchAndMerge(File localRepository) {

            List<Notification> ret = new ArrayList<>();

            return ret;

        }

        /**
         * Commit any current changes before switching to the passed branch (either remote or local). If the
         * branch is new, create it based on current and instrument it for push/pull to/from origin.
         *
         * @param localRepository
         * @param branch
         * @return notifications describing any error. Empty notifications means all OK. May have no errors
         * but warnings, no info. Use {@link Utils.Notifications#hasErrors(Collection)} to check.
         */
        public static List<Notification> commitAndSwitch(File localRepository, String branch) {

            List<Notification> ret = new ArrayList<>();

            return ret;

        }

        /**
         * Perform a hard reset, bringing the current repository to the last commit.
         *
         * @param localRepository
         * @return notifications describing any error. Empty notifications means all OK. May have no errors
         * but warnings, no info. Use {@link Utils.Notifications#hasErrors(Collection)} to check.
         */
        public static List<Notification> hardReset(File localRepository) {

            List<Notification> ret = new ArrayList<>();

            return ret;

        }


        /**
         * Clone repository.
         * <p>
         * TODO use authentication
         *
         * @param gitUrl           the git url
         * @param directory        the directory
         * @param removeIfExisting the remove if existing
         * @return the string
         */
        public static String clone(String gitUrl, File directory, boolean removeIfExisting) {

            String dirname = URLs.getURLBaseName(gitUrl);

            File pdir = new File(directory + File.separator + dirname);
            if (pdir.exists()) {
                if (removeIfExisting) {
                    try {
                        Files.deleteDirectory(pdir);
                    } catch (Throwable e) {
                        throw new KlabIOException(e);
                    }
                } else {
                    throw new KlabIOException("git clone: directory " + pdir + " already exists");
                }
            }

            String[] pdefs = gitUrl.split("#");
            String branch;
            if (pdefs.length < 2) {
                branch = MAIN_BRANCH;
            } else {
                branch = branchExists(pdefs[0], pdefs[1]) ? pdefs[1] : MAIN_BRANCH;
            }
            String url = pdefs[0];

            Logging.INSTANCE.info("cloning Git repository " + url + " branch " + branch + " ...");

            try (org.eclipse.jgit.api.Git result =
                         org.eclipse.jgit.api.Git.cloneRepository().setURI(url).setBranch(branch)
                                                 .setDirectory(pdir).call()) {

                Logging.INSTANCE.info("cloned Git repository: " + result.getRepository());

                if (!branch.equals(MAIN_BRANCH)) {
                    result.checkout().setName(branch).setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                          .setStartPoint("origin/" + branch).call();

                    Logging.INSTANCE.info("switched repository: " + result.getRepository() + " to branch " + branch);
                }

            } catch (Throwable e) {
                throw new KlabIOException(e);
            }

            return dirname;
        }

        /**
         * Pull local repository in passed directory.
         * <p>
         * TODO use authentication
         *
         * @param localRepository main directory (containing .git/)
         */
        public static void pull(File localRepository) {

            try (Repository localRepo = new FileRepository(localRepository + File.separator + ".git")) {
                try (org.eclipse.jgit.api.Git git = new org.eclipse.jgit.api.Git(localRepo)) {

                    Logging.INSTANCE.info("fetch/merge changes in repository: " + git.getRepository());

                    PullCommand pullCmd = git.pull();
                    PullResult result = pullCmd.call();
                    // return result != null && result.getFetchResult() != null &&
                    // result.getFetchResult().

                } catch (Throwable e) {
                    throw new KlabIOException("error pulling repository " + localRepository + ": " + e.getLocalizedMessage());
                }
            } catch (IOException e) {
                throw new KlabIOException(e);
            }
        }

        /**
         * If a Git repository with the repository name corresponding to the URL exists in gitDirectory, pull
         * it from origin; otherwise clone it from the passed Git URL.
         * <p>
         * TODO: Assumes branch is already set correctly if repo is pulled. Should check branch and
         * checkout if necessary.
         * <p>
         * TODO use authentication
         *
         * @param gitUrl       the git url
         * @param gitDirectory the git directory
         * @return the string
         */
        public static String requireUpdatedRepository(String gitUrl, File gitDirectory) {

            String repositoryName = URLs.getURLBaseName(gitUrl);

            File repoDir = new File(gitDirectory + File.separator + repositoryName);
            File gitDir = new File(repoDir + File.separator + ".git");

            if (gitDir.exists() && gitDir.isDirectory() && gitDir.canRead() && repoDir.exists()) {

                pull(repoDir);
                /*
                 * TODO check branch and switch/pull if necessary
                 */
            } else {
                if (gitDir.exists()) {
                    Files.deleteQuietly(gitDir);
                }
                clone(gitUrl, gitDirectory, true);
            }

            return repositoryName;
        }

        /**
         * Checks if is remote git URL.
         *
         * @param string the string
         * @return a boolean.
         */
        public static boolean isRemoteGitURL(String string) {
            return string.startsWith("http:") || string.startsWith("git:") || string.startsWith("https:")
                    || string.startsWith("git@");
        }

        /**
         * Check if remote branch exists
         *
         * @param gitUrl the remote repository
         * @param branch the branch (without refs/heads/)
         * @return true if branch exists
         */
        public static boolean branchExists(String gitUrl, String branch) {
            final LsRemoteCommand lsCmd = new LsRemoteCommand(null);
            lsCmd.setRemote(gitUrl);
            try {
                return lsCmd.call().stream().filter(ref -> ref.getName().equals("refs/heads/" + branch)).count() == 1;
            } catch (GitAPIException e) {
                e.printStackTrace();
                return false;
            }
        }
    }

}
