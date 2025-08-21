package org.integratedmodelling.klab.services.resources.storage;

import java.util.function.Function;
import java.util.regex.Pattern;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;

/**
 * A class that can take a Resource object and either create or sanitize its URN. The URN is a
 * four-part string with colon separators: - First part: service name - Second part: catalog - Third
 * part: namespace - Fourth part: resource name (corresponds to the localName of the resource)
 */
public class UrnManager {

  // Pattern for valid characters in URN components (lowercase letters, numbers, dots, underscores)
  private static final Pattern VALID_COMPONENT_PATTERN = Pattern.compile("^[a-z0-9_.]+$");

  // Pattern for valid dot-separated path (like Java package names)
  private static final Pattern VALID_PATH_PATTERN =
      Pattern.compile("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*$");

  /**
   * Creates or sanitizes a URN for the given resource.
   *
   * <p>Legacy resources should be turned into new patterns for staging resources. "local" becomes
   * the name of the submitter/certificate holder; the catalog should be "staging". The namespace
   * and ID can remain the same.
   *
   * @param resource The resource to create or sanitize a URN for
   * @param service The service that will host the resource
   * @param isUniqueChecker A function that checks if a URN is unique
   * @return A sanitized URN string
   */
  public String createOrSanitizeUrn(
      Resource resource,
      ResourcesProvider service,
      ResourceInfo.Stage stage,
      UserScope scope,
      Function<String, Boolean> isUniqueChecker) {
    String currentUrn = resource.getUrn();

    if (currentUrn == null || Urn.UNDEFINED_URN.equals(currentUrn)) {
      // Create a new URN from scratch
      return createUrn(resource, service.serviceName(), isUniqueChecker);
    } else {
      // Sanitize the existing URN
      return sanitizeUrn(resource, currentUrn, service, isUniqueChecker);
    }
  }

  /**
   * Creates a new URN for the given resource.
   *
   * @param resource The resource to create a URN for
   * @param serviceName The name of the service
   * @param isUniqueChecker A function that checks if a URN is unique
   * @return A new URN string
   */
  private String createUrn(
      Resource resource, String serviceName, Function<String, Boolean> isUniqueChecker) {

    // Sanitize the service name
    String sanitizedServiceName = sanitizeComponent(serviceName);

    // A new resource's catalog is always "staging"
    String catalog = "staging";

    // Use the resource's project name as the namespace, or "default" if not available
    String namespace = resource.getLocalProjectName();
    if (namespace == null || namespace.isEmpty()) {
      namespace = "default";
    }
    String sanitizedNamespace = sanitizeComponent(namespace);

    // Use the resource's local name as the resource name, or generate one if not available
    String resourceName = resource.getLocalName();
    if (resourceName == null || resourceName.isEmpty()) {
      resourceName = generateResourceName(resource);
    }
    String sanitizedResourceName = sanitizeComponent(resourceName);

    // Combine the components to form the URN
    String urn =
        String.format(
            "%s:%s:%s:%s",
            sanitizedServiceName, catalog, sanitizedNamespace, sanitizedResourceName);

    // Ensure the URN is unique
    return ensureUniqueUrn(urn, isUniqueChecker);
  }

  /**
   * Sanitizes an existing URN.
   *
   * @param resource The resource with the URN to sanitize
   * @param currentUrn The current URN to sanitize
   * @param isUniqueChecker A function that checks if a URN is unique
   * @return A sanitized URN string
   */
  private String sanitizeUrn(
      Resource resource,
      String currentUrn,
      KlabService service,
      Function<String, Boolean> isUniqueChecker) {
    // Parse the current URN
    String[] components = currentUrn.split(":");

    // Ensure we have exactly 4 components
    if (components.length != 4) {
      // If not, create a new URN
      return createUrn(resource, "klab", isUniqueChecker);
    }

    // this is for backwards compatibility
    boolean isStaging = "local".equals(components[0]);

    // Sanitize each component
    String sanitizedServiceName =
        isStaging ? service.serviceName() : sanitizeComponent(components[0]);
    String sanitizedCatalog = isStaging ? "staging" : sanitizeComponent(components[1]);
    String sanitizedNamespace = sanitizeComponent(components[2]);
    String sanitizedResourceName = sanitizeComponent(components[3]);

    // Update the resource name from the resource's local name if available
    if (resource.getLocalName() != null && !resource.getLocalName().isEmpty()) {
      sanitizedResourceName = sanitizeComponent(resource.getLocalName());
    }

    // Combine the components to form the URN
    String urn =
        String.format(
            "%s:%s:%s:%s",
            sanitizedServiceName, sanitizedCatalog, sanitizedNamespace, sanitizedResourceName);

    // Ensure the URN is unique
    return ensureUniqueUrn(urn, isUniqueChecker);
  }

  /**
   * Sanitizes a URN component to ensure it contains only lowercase, meaningful components without
   * strange characters. Each component may be a dot-separated path using a hierarchical convention
   * similar to Java package names.
   *
   * @param component The component to sanitize
   * @return A sanitized component
   */
  private String sanitizeComponent(String component) {

    if (component == null || component.isEmpty()) {
      return "default";
    }

    // Convert to lowercase
    component = component.toLowerCase();

    // Check if it's already a valid dot-separated path
    if (VALID_PATH_PATTERN.matcher(component).matches()) {
      return component;
    }

    // Replace invalid characters with underscores
    component = component.replaceAll("[^a-z0-9_.]", "_");

    // Ensure it doesn't start with a number or underscore
    if (!component.isEmpty() && !Character.isLetter(component.charAt(0))) {
      component = "x" + component;
    }

    // Handle consecutive dots and ensure each segment starts with a letter
    String[] segments = component.split("\\.");
    StringBuilder result = new StringBuilder();

    for (int i = 0; i < segments.length; i++) {
      String segment = segments[i];

      // Skip empty segments
      if (segment.isEmpty()) {
        continue;
      }

      // Ensure segment starts with a letter
      if (!Character.isLetter(segment.charAt(0))) {
        segment = "x" + segment;
      }

      // Add the segment to the result
      if (result.length() > 0) {
        result.append(".");
      }
      result.append(segment);
    }

    // If we end up with an empty string, use "default"
    if (result.length() == 0) {
      return "default";
    }

    return result.toString();
  }

  /**
   * Generates a resource name based on the resource's properties.
   *
   * @param resource The resource to generate a name for
   * @return A generated resource name
   */
  private String generateResourceName(Resource resource) {
    // Try to use the resource's type as a base for the name
    if (resource.getType() != null) {
      return resource.getType().name().toLowerCase() + "_" + System.currentTimeMillis();
    }

    // If no type is available, use a generic name
    return "resource_" + System.currentTimeMillis();
  }

  /**
   * Ensures that the URN is unique by appending a suffix if necessary.
   *
   * @param urn The URN to check for uniqueness
   * @param isUniqueChecker A function that checks if a URN is unique
   * @return A unique URN
   */
  private String ensureUniqueUrn(String urn, Function<String, Boolean> isUniqueChecker) {
    if (isUniqueChecker.apply(urn)) {
      return urn;
    }

    // If not unique, append a suffix
    int suffix = 1;
    String baseUrn = urn;

    while (!isUniqueChecker.apply(urn)) {
      urn = baseUrn + "_" + suffix;
      suffix++;
    }

    return urn;
  }
}
