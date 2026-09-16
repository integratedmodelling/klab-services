package org.integratedmodelling.klab.services.application.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.integratedmodelling.common.authentication.UserIdentityImpl;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.rest.GroupImpl;
import org.integratedmodelling.klab.services.ServiceInstance;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Browser authentication through the hub's existing /api/v2/users/me?remote=true contract.
 * Trust comes from the configured hub's HTTPS response, never from a caller-supplied JWT payload.
 * The hub JWT stays server-side; the browser receives a bounded, short-lived opaque credential.
 */
@Component
public class HubWebAuthentication {
  public static final String ENDPOINT = "/public/ui/authentication";
  static final String PREFIX = "webui_";
  private static final int MAX_SESSIONS = 256;
  private final Environment environment;
  private final Clock clock;
  private final ProfileClient client;
  private final SecureRandom random = new SecureRandom();
  private final Map<String, Session> sessions = new LinkedHashMap<>();
  private volatile String hubUrl;
  private volatile String hubIssuer;

  @FunctionalInterface
  interface ProfileClient {
    JsonNode fetch(URI endpoint, String accessToken) throws Exception;
  }

  @Autowired
  public HubWebAuthentication(Environment environment) {
    this(environment, Clock.systemUTC(), new ProfileClient() {
      private final HttpClient http = HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
      private final ObjectMapper json = new ObjectMapper();

      public JsonNode fetch(URI endpoint, String accessToken) throws Exception {
        var response = http.send(HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(15)).header("Accept", "application/json")
            .header("Authorization", "Bearer " + accessToken).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 204) {
          throw failure(HttpStatus.FORBIDDEN, "Complete your k.LAB hub registration and agreement first.");
        }
        if (response.statusCode() == 401 || response.statusCode() == 403) {
          throw failure(HttpStatus.UNAUTHORIZED, "The hub did not accept this sign-in.");
        }
        if (response.statusCode() != 200 && response.statusCode() != 202) {
          throw failure(HttpStatus.BAD_GATEWAY, "The hub could not complete authentication.");
        }
        return json.readTree(response.body());
      }
    });
  }

  HubWebAuthentication(Environment environment, Clock clock, ProfileClient client) {
    this.environment = environment;
    this.clock = clock;
    this.client = client;
  }

  /** Called only with service startup authentication data, never request parameters. */
  public void configureHub(String url, String issuer) {
    this.hubUrl = url;
    this.hubIssuer = issuer;
  }

  public record Login(String token, String username, long expiresAt) {}
  private record Session(String issuer, String hubToken, UserIdentityImpl user,
                         List<Role> roles, Instant expires, ServiceUserScope scope) {}

  public Login exchange(String accessToken, ServiceInstance<?> instance) {
    if (accessToken == null || accessToken.isBlank() || accessToken.length() > 32768
        || accessToken.startsWith(PREFIX)) {
      throw failure(HttpStatus.UNAUTHORIZED, "A Keycloak access token is required.");
    }
    String url = environment.getProperty("klab.webui.hub.url", hubUrl);
    String issuer = environment.getProperty("klab.webui.hub.issuer", hubIssuer);
    URI endpoint = profileEndpoint(url);
    if (issuer == null || issuer.isBlank()) {
      throw failure(HttpStatus.SERVICE_UNAVAILABLE, "The trusted hub issuer is not configured.");
    }
    try {
      JsonNode profile = client.fetch(endpoint, accessToken);
      String hubToken = profile.path("jwtToken").asText("");
      // Only decode a token just obtained directly from the trusted hub. This is NOT a verifier
      // for tokens supplied by clients. The current hub contract has no public signing-key route.
      var claims = new JwtConsumerBuilder().setSkipAllValidators().setDisableRequireSignature()
          .setSkipSignatureVerification().build().processToClaims(hubToken);
      String username = profile.path("name").asText("");
      Instant now = clock.instant();
      if (username.isBlank() || !username.equals(claims.getSubject())
          || !issuer.equals(claims.getIssuer()) || !claims.getAudience().contains("engine")
          || claims.getExpirationTime() == null || claims.getIssuedAt() == null
          || claims.getIssuedAt().getValue() > now.plusSeconds(30).getEpochSecond()
          || claims.getExpirationTime().getValue() <= now.getEpochSecond()) {
        throw failure(HttpStatus.BAD_GATEWAY, "The hub returned an invalid k.LAB identity.");
      }
      var roles = new ArrayList<Role>();
      var roleNames = claims.getStringListClaimValue("roles");
      if (roleNames != null) {
        for (Role role : Role.values()) if (roleNames.contains(role.name())) roles.add(role);
      }
      if (!roles.contains(Role.ROLE_USER)) {
        throw failure(HttpStatus.FORBIDDEN, "Your hub account has no k.LAB user role.");
      }
      var grant = ServicePermissionPolicy.resolve(instance.klabService().isLocal(),
          instance.getServiceOwner(), hubIssuer, issuer, username, roles);
      if (instance.klabService().isLocal() && !grant.localOwner()) {
        throw failure(HttpStatus.FORBIDDEN,
            "This local service requires its authenticated owner from the startup hub.");
      }
      var user = new UserIdentityImpl();
      user.setUsername(username);
      user.setId(hubToken);
      user.setEmailAddress(profile.path("email").asText(null));
      user.setAuthenticated(true);
      user.setAnonymous(false);
      user.setOnline(true);
      var permissions = claims.getStringListClaimValue("perms");
      if (permissions != null) {
        for (String groupId : permissions) {
          var group = new GroupImpl(groupId);
          group.setName(groupId);
          user.getGroups().add(group);
        }
      }
      Instant expires = Instant.ofEpochSecond(Math.min(now.plusSeconds(300).getEpochSecond(),
          claims.getExpirationTime().getValue()));
      var scope = new ServiceUserScope(user, instance.klabService());
      scope.setId("webui:" + UUID.randomUUID());
      scope.getRoles().addAll(grant.roles());
      scope.setLocal(false);
      scope.setPermissions(grant.permissions());
      byte[] bytes = new byte[32];
      random.nextBytes(bytes);
      String credential = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
      synchronized (sessions) {
        removeExpired();
        if (sessions.size() >= MAX_SESSIONS) {
          throw failure(HttpStatus.SERVICE_UNAVAILABLE, "Too many active browser sessions. Try again later.");
        }
        sessions.put(credential, new Session(issuer, hubToken, user, List.copyOf(grant.roles()), expires, scope));
      }
      return new Login(credential, username, expires.toEpochMilli());
    } catch (ResponseStatusException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw failure(HttpStatus.BAD_GATEWAY, "Hub authentication was interrupted.");
    } catch (Exception e) {
      // Do not expose upstream bodies, tokens, or exception messages to logs or clients.
      throw failure(HttpStatus.BAD_GATEWAY, "The hub returned an unavailable or invalid identity.");
    }
  }

  static URI profileEndpoint(String url) {
    try {
      URI base = URI.create(url);
      if (!"https".equalsIgnoreCase(base.getScheme()) || base.getHost() == null
          || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null) {
        throw new IllegalArgumentException();
      }
      return URI.create(url.replaceAll("/+$", "") + "/api/v2/users/me?remote=true");
    } catch (Exception e) {
      throw failure(HttpStatus.SERVICE_UNAVAILABLE, "Configure a trusted HTTPS hub URL for browser sign-in.");
    }
  }

  public EngineAuthorization authorize(String credential, Map<String, String> headers) {
    synchronized (sessions) {
      removeExpired();
      var session = sessions.get(credential);
      // These sessions own independent user scopes. They cannot select privileged IDE scopes.
      if (session == null || headers.get(ServicesAPI.SCOPE_HEADER) != null) return null;
      var authorization = new EngineAuthorization(session.issuer(), session.user().getUsername(),
          session.hubToken(), headers, session.user().getGroups(), session.roles());
      authorization.setAuthenticated(true);
      authorization.setEmailAddress(session.user().getEmailAddress());
      authorization.setExpiration(session.expires());
      authorization.setScope(session.scope());
      return authorization;
    }
  }

  public void revoke(String credential) {
    synchronized (sessions) { sessions.remove(credential); }
  }

  private void removeExpired() {
    sessions.values().removeIf(session -> !clock.instant().isBefore(session.expires()));
  }

  private static ResponseStatusException failure(HttpStatus status, String message) {
    return new ResponseStatusException(status, message);
  }
}
