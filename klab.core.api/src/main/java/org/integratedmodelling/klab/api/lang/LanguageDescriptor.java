package org.integratedmodelling.klab.api.lang;

import org.integratedmodelling.klab.api.data.Version;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Structure returned by the language info point in the {@link
 * org.integratedmodelling.klab.api.services.ResourcesService}. May grow as needed.
 */
public class LanguageDescriptor implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private Map<KlabLanguage, LanguageInfo> languages = new HashMap<>();

  public static class LanguageInfo implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    private String name;
    private String id;
    private String shortId;
    private Version version;
    private List<String> keywords = new ArrayList<>();

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getShortId() {
      return shortId;
    }

    public void setShortId(String shortId) {
      this.shortId = shortId;
    }

    public Version getVersion() {
      return version;
    }

    public void setVersion(Version version) {
      this.version = version;
    }

    public List<String> getKeywords() {
      return keywords;
    }

    public void setKeywords(List<String> keywords) {
      this.keywords = keywords;
    }
  }

  public Map<KlabLanguage, LanguageInfo> getLanguages() {
    return languages;
  }

  public void setLanguages(Map<KlabLanguage, LanguageInfo> languages) {
    this.languages = languages;
  }
}
