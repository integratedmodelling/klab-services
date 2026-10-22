package org.integratedmodelling.klab.services.application.ui;

import static org.springframework.http.HttpStatus.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectMaterial;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.runtime.kactors.ApplicationBase;
import org.integratedmodelling.klab.runtime.kactors.compiler.runtime.AgentRegistry;
import org.integratedmodelling.klab.runtime.kactors.view.UiPresentation;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Published code only. Both normal URNs and service classpath aliases require project READ. */
@Component
public final class ServiceUiApplicationLauncher implements UiApplicationLauncher {
  @org.springframework.beans.factory.annotation.Value("${klab.ui.toolkit:quasar}")
  private String toolkit = "quasar";

  @Override
  public org.integratedmodelling.klab.runtime.kactors.view.UiToolkitLease selectToolkit() {
    return instance.klabService().getComponentRegistry().acquireUiToolkit(toolkit);
  }

  private final ServiceNetworkedInstance<?> instance;
  private final java.util.function.BiFunction<
          org.integratedmodelling.klab.api.services.runtime.objects.UserScopeNotification
              .ServiceInfo,
          org.integratedmodelling.klab.services.scopes.ServiceUserScope,
          org.integratedmodelling.klab.api.services.KlabService>
      clients;

  @org.springframework.beans.factory.annotation.Autowired
  public ServiceUiApplicationLauncher(ServiceNetworkedInstance<?> instance) {
    this(
        instance,
        (info, scope) ->
            org.integratedmodelling.common.services.client.ServiceClientCatalog.INSTANCE.getService(
                info, instance.klabService(), scope));
  }

  ServiceUiApplicationLauncher(
      ServiceNetworkedInstance<?> instance,
      java.util.function.BiFunction<
              org.integratedmodelling.klab.api.services.runtime.objects.UserScopeNotification
                  .ServiceInfo,
              org.integratedmodelling.klab.services.scopes.ServiceUserScope,
              org.integratedmodelling.klab.api.services.KlabService>
          clients) {
    this.instance = instance;
    this.clients = clients;
  }

  private void prepareBrowserServices(UserScope scope) {
    if (!(scope instanceof org.integratedmodelling.klab.services.scopes.ServiceUserScope browser)
        || browser.getId() == null
        || !browser.getId().startsWith("webui:")
        || !browser.getServices(ResourcesService.class).isEmpty()) return;
    var host = instance.klabService();
    var topology = host.serviceScope();
    if (topology == null) return;
    for (Class<? extends org.integratedmodelling.klab.api.services.KlabService> type :
        List.of(
            ResourcesService.class, org.integratedmodelling.klab.api.services.Reasoner.class,
            org.integratedmodelling.klab.api.services.Resolver.class, RuntimeService.class)) {
      var trusted = type.isInstance(host) ? host : topology.getService(type);
      if (trusted == null) continue;
      if (trusted == host
          || trusted instanceof org.integratedmodelling.klab.services.base.BaseService
              && trusted.getUrl() == null) {
        browser.addService(trusted);
      } else if (trusted.getUrl() != null) {
        var info =
            new org.integratedmodelling.klab.api.services.runtime.objects.UserScopeNotification
                .ServiceInfo();
        info.setId(trusted.serviceId());
        info.setUrl(trusted.getUrl());
        info.setType(org.integratedmodelling.klab.api.services.KlabService.Type.classify(type));
        info.setStatus(trusted.status());
        // Copy trusted topology, never a service-authenticated client or its credentials.
        browser.addService(clients.apply(info, browser));
      }
    }
  }

  private ResourcesService resources(UserScope scope, String id) {
    prepareBrowserServices(scope);
    ResourcesService preferred;
    try {
      preferred = scope.getService(ResourcesService.class);
    } catch (org.integratedmodelling.klab.api.exceptions.KlabServiceAccessException unavailable) {
      return null;
    }
    if (id == null || preferred != null && id.equals(preferred.serviceId())) return preferred;
    return scope
        .findService(ResourcesService.class, service -> id.equals(service.serviceId()))
        .orElse(null);
  }

  @Override
  public Published resolve(String urn, String requestedLocale, EngineAuthorization user) {
    var scope = (UserScope) user.getScope();
    var resources = resources(scope, null);
    if (resources == null)
      throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Resources unavailable");
    KActorsBehavior behavior;
    java.util.function.Function<String, InputStream> assets;
    String prefix = instance.klabService().serviceId() + ":app:";
    if (urn.startsWith(prefix)) {
      String name = urn.substring(prefix.length());
      if (!name.matches("[a-z][a-z0-9-]{0,63}"))
        throw new ResponseStatusException(BAD_REQUEST, "Invalid bundled application name");
      String root = "META-INF/klab/apps/" + name + "/";
      var loader = getClass().getClassLoader();
      var manifest = new Properties();
      try (var stream = loader.getResourceAsStream(root + "application.properties")) {
        if (stream == null) throw new ResponseStatusException(NOT_FOUND);
        manifest.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
      } catch (IOException error) {
        throw new UncheckedIOException(error);
      }
      String project = manifest.getProperty("project");
      requireRead(resources, project, scope);
      try {
        var source = loader.getResource(root + "main.kactor");
        if (source == null) throw new ResponseStatusException(NOT_FOUND);
        behavior = resources.parseAsset(source, KActorsBehavior.class, scope);
      } catch (Exception error) {
        throw new ResponseStatusException(BAD_REQUEST, "Cannot parse bundled application", error);
      }
      if (behavior
          instanceof org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl impl)
        impl.setProjectName(project);
      assets =
          path -> loader.getResourceAsStream(root + "ui/" + ProjectMaterial.canonicalPath(path));
    } else {
      behavior = resources.retrieve(urn, KActorsBehavior.class, scope);
      if (behavior == null) throw new ResponseStatusException(NOT_FOUND);
      requireRead(resources, behavior.getProjectName(), scope);
      String project = behavior.getProjectName();
      assets =
          path -> {
            var material =
                resources.retrieve(
                    project + "/ui/" + ProjectMaterial.canonicalPath(path),
                    ProjectMaterial.class,
                    scope);
            if (material == null || material.getContent() == null) return null;
            if (material.getContent().length > 131072)
              throw new ResponseStatusException(PAYLOAD_TOO_LARGE, "UI asset too large");
            return new ByteArrayInputStream(material.getContent());
          };
    }
    if (behavior == null
        || behavior.getBehaviorType() != KActorsBehavior.Type.APP
        || (behavior.getPlatform() != KActorsBehavior.Platform.ANY
            && behavior.getPlatform() != KActorsBehavior.Platform.WEB))
      throw new ResponseStatusException(BAD_REQUEST, "A web-compatible application is required");
    // Pin the semantic source independently of a local Resources service's mutable cache.
    var mapper = org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    behavior = mapper.convertValue(behavior, KActorsBehavior.class);
    var properties = behavior.getProperties();
    String fallback = Objects.toString(properties.get("locale"), "en");
    String locale =
        requestedLocale == null || requestedLocale.isBlank() ? fallback : requestedLocale;
    if (!locale.matches("[a-zA-Z]{2,8}(?:-[a-zA-Z0-9]{1,8})*"))
      throw new ResponseStatusException(BAD_REQUEST, "Invalid locale");
    if (properties.get("locales") instanceof Collection<?> locales && !locales.contains(locale))
      locale = fallback;
    String theme = Objects.toString(properties.get("theme"), "light");
    var names = new ArrayList<String>();
    if (properties.get("stylesheets") instanceof Collection<?> sheets)
      sheets.forEach(sheet -> names.add(sheet.toString()));
    else if (properties.get("stylesheets") instanceof String sheet) names.add(sheet);
    var css = new LinkedHashMap<String, String>();
    for (String name : names) {
      if (Set.of("light", "dark").contains(name)) {
        theme = name;
        continue;
      }
      if (!name.matches("[a-zA-Z0-9_/-]+") || name.contains("..") || name.startsWith("/"))
        throw new ResponseStatusException(BAD_REQUEST, "Invalid stylesheet name");
      try (var stream = assets.apply(name + ".css")) {
        if (stream == null)
          throw new ResponseStatusException(NOT_FOUND, "Missing stylesheet " + name);
        byte[] bytes = stream.readNBytes(131073);
        if (bytes.length > 131072) throw new ResponseStatusException(PAYLOAD_TOO_LARGE);
        css.put(name, new String(bytes, StandardCharsets.UTF_8));
      } catch (IOException error) {
        throw new UncheckedIOException(error);
      }
    }
    var translations =
        properties.get("translations") instanceof String bundle
            ? UiPresentation.translations(bundle, locale, assets)
            : Map.<String, String>of();
    return new Published(
        urn,
        behavior.getProjectName(),
        resources.serviceId(),
        behavior,
        new UiPresentation(locale, theme, names, translations),
        Map.copyOf(css));
  }

  private void requireRead(ResourcesService resources, String project, UserScope scope) {
    if (resources == null || project == null || project.isBlank())
      throw new ResponseStatusException(FORBIDDEN);
    var info = resources.info(project, KnowledgeClass.PROJECT, ResourceInfo.class, scope);
    if (info == null || !info.getPermissions().contains(CRUDOperation.READ))
      throw new ResponseStatusException(FORBIDDEN);
  }

  @Override
  public void authorize(Published application, EngineAuthorization user) {
    requireRead(
        resources((UserScope) user.getScope(), application.resourcesServiceId()),
        application.project(),
        (UserScope) user.getScope());
  }

  @Override
  public ApplicationBase start(Published application, EngineAuthorization user) {
    if (!(instance.klabService() instanceof RuntimeService runtime))
      throw new ResponseStatusException(
          SERVICE_UNAVAILABLE, "Launch applications through a runtime service");
    var agent =
        application
            .presentation()
            .constructing(
                () ->
                    runtime.createAgent(
                        application.behavior(),
                        application.urn(),
                        Set.of(RuntimeAgent.CompilationOptions.DO_NOT_START),
                        (UserScope) user.getScope()));
    var peer = AgentRegistry.INSTANCE.getRuntimeAgent(agent.getUrn());
    if (!(peer instanceof ApplicationBase app)) {
      if (peer instanceof org.integratedmodelling.klab.runtime.kactors.RuntimeAgentBase runtimePeer)
        runtimePeer.stop();
      throw new ResponseStatusException(BAD_REQUEST, "Application compilation failed");
    }
    app.ui().presentation(application.presentation());
    if (!agent.start()) {
      agent.stop();
      throw new ResponseStatusException(BAD_REQUEST, "Application startup failed");
    }
    return app;
  }
}
