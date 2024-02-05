package org.integratedmodelling.klab.services.reasoner.configuration;

import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.configuration.Configuration;
import org.integratedmodelling.klab.utilities.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ReasonerConfiguration {

    public static class ProjectConfiguration {

        private String project;
        private String url;
        private boolean loadAtStartup = true;
        private boolean serve = true;
        private Version minimumVersion;

        private List<String> groups = new ArrayList<>();

        public String getProject() {
            return project;
        }

        public void setProject(String project) {
            this.project = project;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public boolean isLoadAtStartup() {
            return loadAtStartup;
        }

        public void setLoadAtStartup(boolean loadAtStartup) {
            this.loadAtStartup = loadAtStartup;
        }

        public boolean isServe() {
            return serve;
        }

        public void setServe(boolean serve) {
            this.serve = serve;
        }

        public Version getMinimumVersion() {
            return minimumVersion;
        }

        public void setMinimumVersion(Version minimumVersion) {
            this.minimumVersion = minimumVersion;
        }

        public List<String> getGroups() {
            return groups;
        }

        public void setGroups(List<String> groups) {
            this.groups = groups;
        }

    }

    public static class Services {

        private boolean assist = true;
        private boolean explain = false;
        private boolean search = true;
        private boolean examples = false;
        private boolean resolve = true;
        private boolean reason = true;

        public boolean isAssist() {
            return assist;
        }

        public void setAssist(boolean assist) {
            this.assist = assist;
        }

        public boolean isExplain() {
            return explain;
        }

        public void setExplain(boolean explain) {
            this.explain = explain;
        }

        public boolean isSearch() {
            return search;
        }

        public void setSearch(boolean search) {
            this.search = search;
        }

        public boolean isExamples() {
            return examples;
        }

        public void setExamples(boolean examples) {
            this.examples = examples;
        }

        public boolean isResolve() {
            return resolve;
        }

        public void setResolve(boolean resolve) {
            this.resolve = resolve;
        }

        public boolean isReason() {
            return reason;
        }

        public void setReason(boolean reason) {
            this.reason = reason;
        }

    }

    private Services services = new Services();
    private int refreshIntervalMinutes = 10;
    private List<String> allowedGroups = new ArrayList<>();
    private String url = null;
    //    private List<ProjectConfiguration> worldview = new ArrayList<>();
    private List<ProjectConfiguration> authorities = new ArrayList<>();

    private String serviceId;

    public int getRefreshIntervalMinutes() {
        return refreshIntervalMinutes;
    }

    public void setRefreshIntervalMinutes(int refreshIntervalMinutes) {
        this.refreshIntervalMinutes = refreshIntervalMinutes;
    }

    public List<String> getAllowedGroups() {
        return allowedGroups;
    }

    public void setAllowedGroups(List<String> allowedGroups) {
        this.allowedGroups = allowedGroups;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
    //    public List<ProjectConfiguration> getWorldview() {
    //        return worldview;
    //    }
    //    public void setWorldview(List<ProjectConfiguration> worldview) {
    //        this.worldview = worldview;
    //    }

    public Services getServices() {
        return services;
    }

    public void setServices(Services services) {
        this.services = services;
    }

    public List<ProjectConfiguration> getAuthorities() {
        return authorities;
    }

    public void setAuthorities(List<ProjectConfiguration> authorities) {
        this.authorities = authorities;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    // this generates a first-boot config with only the im project from the connected resources
    public static void main(String[] deus) {

        ReasonerConfiguration ret = new ReasonerConfiguration();
        //        ProjectConfiguration prt = new ProjectConfiguration();
        //        prt.setProject("im");
        ProjectConfiguration aut1 = new ProjectConfiguration();
        aut1.setProject("GBIF");
        aut1.setServe(false);
        aut1.setUrl("classpath:org.integratedmodelling.klab.services.reasoner.authorities.GBIFAuthority");
        ProjectConfiguration aut2 = new ProjectConfiguration();
        aut2.setProject("CALIPER");
        aut2.setServe(false);
        aut2.setUrl("classpath:org.integratedmodelling.klab.services.reasoner.authorities.CaliperAuthority");
        ProjectConfiguration aut3 = new ProjectConfiguration();
        aut3.setProject("IUPAC");
        aut3.setServe(false);
        aut3.setUrl("classpath:org.integratedmodelling.klab.services.reasoner.authorities.IUPACAuthority");

        //        ret.getWorldview().add(prt);
        ret.getAuthorities().add(aut1);
        ret.getAuthorities().add(aut2);
        ret.getAuthorities().add(aut3);
        ret.setServiceId(UUID.randomUUID().toString());

        Utils.YAML.save(ret, new File(Configuration.INSTANCE.getDataPath() + File.separator + "reasoner" +
                ".yaml"));
    }
}
