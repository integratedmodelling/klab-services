package org.integratedmodelling.klab.services.application.web;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.integratedmodelling.klab.api.services.KlabService;

/**
 * Serializable description of the service dashboard. Concrete Spring service applications add
 * panels and links through {@code ServiceNetworkedInstance.configureWebUi(...)}; the Vue shell
 * consumes the resulting document from {@code /public/ui/config}.
 */
public record WebUiConfiguration(
    KlabService.Type serviceType,
    String title,
    String subtitle,
    String logoUrl,
    AuthenticationSettings authentication,
    List<DashboardPanel> panels,
    List<DashboardLink> links) {

  /** Public OpenID Connect settings needed by the Keycloak JavaScript adapter. */
  public record AuthenticationSettings(
      boolean enabled, String url, String realm, String clientId) {}

  /** A named Vue panel contributed by a service application. */
  public record DashboardPanel(
      String id,
      String title,
      String description,
      String component,
      int order,
      boolean requiresAuthentication) {}

  /** A service-specific navigation link displayed in the dashboard header. */
  public record DashboardLink(String label, String href, boolean external) {}

  public static Builder builder(KlabService.Type serviceType) {
    return new Builder(serviceType);
  }

  public static final class Builder {

    private final KlabService.Type serviceType;
    private String title;
    private String subtitle = "Status and capabilities for this k.LAB service";
    private String logoUrl = "klab-logo.svg";
    private AuthenticationSettings authentication =
        new AuthenticationSettings(false, null, "im", "k.LAB");
    private final List<DashboardPanel> panels = new ArrayList<>();
    private final List<DashboardLink> links = new ArrayList<>();

    private Builder(KlabService.Type serviceType) {
      this.serviceType = serviceType;
      this.title = "k.LAB " + humanize(serviceType.name()) + " service";
    }

    public Builder title(String title) {
      this.title = title;
      return this;
    }

    public Builder subtitle(String subtitle) {
      this.subtitle = subtitle;
      return this;
    }

    public Builder logoUrl(String logoUrl) {
      this.logoUrl = logoUrl;
      return this;
    }

    public Builder keycloak(String url, String realm, String clientId) {
      boolean enabled = url != null && !url.isBlank();
      this.authentication =
          new AuthenticationSettings(
              enabled,
              enabled ? url : null,
              blankToDefault(realm, "im"),
              blankToDefault(clientId, "k.LAB"));
      return this;
    }

    public Builder panel(DashboardPanel panel) {
      panels.add(panel);
      return this;
    }

    public Builder panel(
        String id,
        String title,
        String description,
        String component,
        int order,
        boolean requiresAuthentication) {
      return panel(
          new DashboardPanel(
              id, title, description, component, order, requiresAuthentication));
    }

    public Builder link(String label, String href, boolean external) {
      links.add(new DashboardLink(label, href, external));
      return this;
    }

    public WebUiConfiguration build() {
      var orderedPanels = new ArrayList<>(panels);
      orderedPanels.sort(Comparator.comparingInt(DashboardPanel::order));
      return new WebUiConfiguration(
          serviceType,
          title,
          subtitle,
          logoUrl,
          authentication,
          List.copyOf(orderedPanels),
          List.copyOf(links));
    }

    private static String humanize(String value) {
      var lower = value.toLowerCase().replace('_', ' ');
      return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String blankToDefault(String value, String defaultValue) {
      return value == null || value.isBlank() ? defaultValue : value;
    }
  }
}
