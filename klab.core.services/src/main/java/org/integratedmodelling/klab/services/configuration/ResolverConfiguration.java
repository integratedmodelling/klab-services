package org.integratedmodelling.klab.services.configuration;

import java.io.File;
import java.net.URI;
import java.util.*;

import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Version;

import static java.util.Map.entry;

/** Configuration for resolver service */
public class ResolverConfiguration extends ServiceConfiguration {
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

  /*
   * The default ranking strategy in the form that can be overridden in configuration.
   *
   * missing: "im:scale-specificity 5 "
   * missing: "im:scale-coverage 6 "
   */
  public Map<String, Integer> rankingStrategy =
      Map.ofEntries(
          entry("im:lexical-scope", 1),
          entry("im:evidence", 4),
          entry("im:semantic-concordance", 2),
          entry("im:trait-concordance", 3),
          entry("im:time-specificity", 5),
          entry("im:time-coverage", 6),
          entry("im:space-specificity", 7),
          entry("im:space-coverage", 8),
          entry("im:subjective-concordance", 9),
          entry("im:inherency", 10),
          entry("im:scale-coherency", 0),
          entry("im:network-remoteness", 0),
          entry("im:reliability", 100));

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

  public Services getServices() {
    return services;
  }

  public void setServices(Services services) {
    this.services = services;
  }

  public Map<String, Integer> getRankingStrategy() {
    return rankingStrategy;
  }

  public void setRankingStrategy(Map<String, Integer> rankingStrategy) {
    this.rankingStrategy = rankingStrategy;
  }
}
