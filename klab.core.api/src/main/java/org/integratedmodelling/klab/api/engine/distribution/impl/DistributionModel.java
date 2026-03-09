package org.integratedmodelling.klab.api.engine.distribution.impl;

import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.distribution.Build;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.engine.distribution.Product;
import org.integratedmodelling.klab.api.engine.distribution.Release;
import org.integratedmodelling.klab.api.utils.Utils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class DistributionModel extends Utils.Properties.Container {

  private String name;
  private Instant releaseDate;
  private List<ProductModel> products = new ArrayList<>();

  public static class ProductModel extends Utils.Properties.Container {

    private String description;
    private Product.ProductType productType;
    private Product.Type type;
    private List<ReleaseModel> releases = new ArrayList<>();

    protected ProductModel(URL url) {
      super(url);
      this.productType =
          Product.ProductType.valueOf(this.properties.getProperty(Product.PRODUCT_CLASS_PROPERTY));
      this.type =
          Product.Type.forOption(this.properties.getProperty(Product.PRODUCT_TYPE_PROPERTY));
      this.description = this.properties.getProperty(Product.PRODUCT_DESCRIPTION_PROPERTY);
      for (var key : this.properties.getProperty(Product.RELEASE_NAMES_PROPERTY).split(",")) {
        var releaseUrl =
            url.toString().substring(0, url.toString().indexOf(Product.PRODUCT_PROPERTIES_FILE))
                + key
                + "/release.properties";
        var release = new ReleaseModel(Utils.URLs.newURL(releaseUrl));
        if (release.isEmpty()) {
          setEmpty(true);
        }
        this.releases.add(release);
      }
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public Product.ProductType getProductType() {
      return productType;
    }

    public void setProductType(Product.ProductType productType) {
      this.productType = productType;
    }

    public Product.Type getType() {
      return type;
    }

    public void setType(Product.Type type) {
      this.type = type;
    }

    public List<ReleaseModel> getReleases() {
      return releases;
    }

    public void setReleases(List<ReleaseModel> releases) {
      this.releases = releases;
    }
  }

  public static class ReleaseModel extends Utils.Properties.Container {

    private String name;
    private List<BuildModel> builds = new ArrayList<>();

    protected ReleaseModel(URL url) {
      super(url);
      this.name = this.properties.getProperty(Release.RELEASE_NAME_PROPERTY);
      for (var key : this.properties.getProperty(Release.BUILD_VERSIONS_PROPERTY).split(",")) {
        var buildUrl =
            url.toString().substring(0, url.toString().indexOf(Release.RELEASE_PROPERTIES_FILE))
                + key
                + "/build.properties";
        var build = new BuildModel(Utils.URLs.newURL(buildUrl));
        if (build.isEmpty()) {
          setEmpty(true);
        }
        this.builds.add(build);
      }
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public List<BuildModel> getBuilds() {
      return builds;
    }

    public void setBuilds(List<BuildModel> builds) {
      this.builds = builds;
    }
  }

  public static class BuildModel extends Utils.Properties.Container {

    private String description;
    private Product.ProductType productType;
    private Product.Type type;
    private List<ReleaseModel> releases = new ArrayList<>();
    private String mainClass;
    private boolean osSpecific;
    private Version version;
    private long timestamp;
    private List<File> files = new ArrayList<>();

    public record File(String hash, String name, long size) {
      public static File of(String string) {
        var parts = string.split("\\s+");
        return new File(
            parts[0],
            parts[1].startsWith("./") ? parts[1].substring(2) : parts[1],
            Long.parseLong(parts[2]));
      }
    }

    protected BuildModel(URL url) {
      super(url);

      this.productType =
          Product.ProductType.valueOf(this.properties.getProperty(Product.PRODUCT_CLASS_PROPERTY));
      this.type =
          Product.Type.forOption(this.properties.getProperty(Product.PRODUCT_TYPE_PROPERTY));
      this.description = this.properties.getProperty(Product.PRODUCT_DESCRIPTION_PROPERTY);
      this.mainClass = this.properties.getProperty(Build.BUILD_MAINCLASS_PROPERTY);
      this.timestamp = Long.parseLong(this.properties.getProperty(Build.BUILD_TIME_PROPERTY));
      this.version = Version.create(this.properties.getProperty(Build.BUILD_VERSION_PROPERTY));
      this.osSpecific =
          Boolean.parseBoolean(this.properties.getProperty(Build.PRODUCT_OSSPECIFIC_PROPERTY));

      var filelistUrl =
          url.toString().substring(0, url.toString().indexOf(Build.BUILD_PROPERTIES_FILE))
              + "filelist.txt";

      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(Utils.URLs.newURL(filelistUrl).openStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.trim().isEmpty()) continue;
          files.add(File.of(line));
        }
      } catch (IOException e) {
        setEmpty(true);
      }
    }

    public List<File> getFiles() {
      return files;
    }

    public void setFiles(List<File> files) {
      this.files = files;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public Product.ProductType getProductType() {
      return productType;
    }

    public void setProductType(Product.ProductType productType) {
      this.productType = productType;
    }

    public Product.Type getType() {
      return type;
    }

    public void setType(Product.Type type) {
      this.type = type;
    }

    public List<ReleaseModel> getReleases() {
      return releases;
    }

    public void setReleases(List<ReleaseModel> releases) {
      this.releases = releases;
    }

    public String getMainClass() {
      return mainClass;
    }

    public void setMainClass(String mainClass) {
      this.mainClass = mainClass;
    }

    public boolean isOsSpecific() {
      return osSpecific;
    }

    public void setOsSpecific(boolean osSpecific) {
      this.osSpecific = osSpecific;
    }

    public Version getVersion() {
      return version;
    }

    public void setVersion(Version version) {
      this.version = version;
    }

    public long getTimestamp() {
      return timestamp;
    }

    public void setTimestamp(long timestamp) {
      this.timestamp = timestamp;
    }
  }

  public DistributionModel(String propertiesUrl) {
    this(Utils.URLs.newURL(propertiesUrl));
  }

  public Set<BuildModel.File> getCommonFiles() {
    var counter = new HashMap<BuildModel.File, Integer>();
    for (var product : products) {
      for (var release : product.getReleases()) {
        for (var build : release.getBuilds()) {
          for (var file : build.getFiles()) {
            counter.put(file, counter.containsKey(file) ? counter.get(file) + 1 : 1);
          }
        }
      }
    }
    return counter.entrySet().stream()
        .filter(e -> e.getValue() > 1)
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }

  public DistributionModel(URL propertiesUrl) {
    super(propertiesUrl);
    if (!isEmpty()) {
      this.name = this.properties.getProperty(Distribution.DISTRIBUTION_NAME_PROPERTY);
      this.releaseDate =
          Instant.parse(this.properties.getProperty(Distribution.DISTRIBUTION_DATE_PROPERTY));
      for (var key :
          this.properties.getProperty(Distribution.DISTRIBUTION_PRODUCTS_PROPERTY).split(",")) {
        var productUrl =
            propertiesUrl
                    .toString()
                    .substring(
                        0,
                        propertiesUrl.toString().indexOf(Distribution.DISTRIBUTION_PROPERTIES_FILE))
                + key
                + "/product.properties";
        var product = new ProductModel(Utils.URLs.newURL(productUrl));
        if (product.isEmpty()) {
          setEmpty(true);
        }
        this.products.add(product);
      }
    }
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Instant getReleaseDate() {
    return releaseDate;
  }

  public void setReleaseDate(Instant releaseDate) {
    this.releaseDate = releaseDate;
  }

  public List<ProductModel> getProducts() {
    return products;
  }

  public void setProducts(List<ProductModel> products) {
    this.products = products;
  }
}
