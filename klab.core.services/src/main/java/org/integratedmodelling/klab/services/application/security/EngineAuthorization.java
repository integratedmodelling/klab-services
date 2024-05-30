/**
 * - BEGIN LICENSE: 4552165799761088680 -
 * <p>
 * Copyright (C) 2014-2018 by: - J. Luke Scott <luke@cron.works> - Ferdinando Villa
 * <ferdinando.villa@bc3research.org> - any other authors listed in the @author annotations in source files
 * <p>
 * This program is free software; you can redistribute it and/or modify it under the terms of the Affero
 * General Public License Version 3 or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the Affero General Public
 * License for more details.
 * <p>
 * You should have received a copy of the Affero General Public License along with this program; if not, write
 * to the Free Software Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA. The license
 * is also available at: https://www.gnu.org/licenses/agpl.html
 * <p>
 * - END LICENSE -
 */
package org.integratedmodelling.klab.services.application.security;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.*;

import org.integratedmodelling.klab.api.identities.Group;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.rest.AuthenticatedIdentity;
import org.integratedmodelling.klab.rest.IdentityReference;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.CredentialsContainer;

/**
 * The authorization for any calls authenticated through an engine JWT.
 * <p>
 * The field names represent the chosen vocabulary we're using in k.LAB; they might be different in JWT and/or
 * Spring parlance, and therefore the calls coming in and out of this class might feel a bit awkward - for
 * instance, a JWT "subject" is set here as a "username" and is returned using the Spring method
 * getPrincipal().
 */
public class EngineAuthorization extends AbstractAuthenticationToken implements AuthenticatedIdentity {

    private static final int TOKEN_TTL_SECONDS = 60 * 60 * 24 * 7 * 4; // 4 weeks

    protected Instant expiration;

    /**
     * The time at which the token was issued - this would be the JWT token in the case of a typical login by
     * either username or cert file.
     */
    private Instant issuedAt;

    /**
     * The groups associated with the token. These come from the authenticating hub.
     */
    private List<Group> groups = new ArrayList<>();

    /**
     * The ID of the Integrated Modelling partner which owns the directory containing the user being
     * authenticated. For example, "im" is the ID for the main directory
     * <p>
     * Spring equivalent: [none] JWT equivalent: issuer ("iss" claim)
     */
    private final Credentials partnerId;

    /**
     * A collection of partner-specific permissions being granted with this Authentication. For example, a
     * PartnerAuthority [im, public-network] would indicate a "public-network" permission level granted by the
     * "im" partner directory.
     */
    private Collection<Role> roles;

    /**
     * JWT token string, in 3 dot-separated sections. Each section is base 64 encoded.
     */
    private Credentials tokenString;

    /**
     * The username of the logged in user. Usernames will be unique within a partner directory, but there may
     * be duplicates between partners, so usernames should always be identified with respect to the directory
     * in which they are stored.
     */
    private final Credentials username;

    /**
     * ID of the current scope, held in the scope manager. Only service scopes have a null here.
     */
    private String scopeId;
    private boolean local;

    //    /**
//     * A successful authorization should always result in a scope being obtained.
//     */
//    private Scope scope;

//    public EngineAuthorization(String partnerId, String username) {
//        this(partnerId, username, null);
//    }

    public EngineAuthorization(String partnerId, String username, Collection<Role> roles) {
        super(roles);
        this.partnerId = new Credentials(partnerId);
        this.username = new Credentials(username);
        expiration = Instant.now().plusSeconds(TOKEN_TTL_SECONDS);

        // convenience code: mimic what the parent constructor did, but with the <Role>
        // generic type.
        if (roles == null) {
            this.roles = Collections.emptyList();
        } else {
            ArrayList<Role> temp = new ArrayList<>(roles.size());
            temp.addAll(roles);
            this.roles = Collections.unmodifiableList(temp);
        }
    }

//    public static EngineAuthorization anonymous(/*ServiceScope scope*/) {
//        var ret = EngineAuthorization.create(/*scope, null*/);
//        ret.setExpiration(Instant.now().plus(Duration.ofDays(1)));
//        ret.setAuthenticated(true);
//        // no roles, no groups
//        ret.roles = EnumSet.noneOf(Role.class);
//        ret.groups = Collections.emptyList();
//        return ret;
//    }

    /**
     * Create an authorization principal in service scope with full privileges.
     *
     * @param scope
     * @return
     */
//    public static EngineAuthorization create(/*Scope scope, String serviceSecret*/) {
//
//        String partnerIdentity = null;
//        String scopeIdentity = null;
//
//        Identity identity = scope.getIdentity();
//
//        Set<Role> roles = EnumSet.noneOf(Role.class);
//        if (identity instanceof UserIdentity user) {
//            scopeIdentity = user.getUsername();
//            // TODO find out the partner for the user, set partner identity
//        } // TODO partner identity, context, session etc
////        if (scope instanceof ServiceScope serviceScope) {
////            // TODO fix the actual roles we want
////            roles = EnumSet.of(Role.ROLE_ENGINE, Role.ROLE_ADMINISTRATOR, Role.ROLE_USER,
////                    Role.ROLE_DATA_MANAGER);
////            if (scopeIdentity == null) {
////
////            }
////        }
//        if (partnerIdentity == null) {
//            partnerIdentity = scopeIdentity;
//        }
//        var ret = new EngineAuthorization(partnerIdentity, scopeIdentity, roles);
//        ret.setAuthenticated(true);
////        ret.setScope(scope);
////        if (serviceSecret != null) {
////            ret.setTokenString(serviceSecret);
////        }
//        return ret;
//    }

    @Override
    public Credentials getCredentials() {
        return tokenString;
    }

    public Instant getExpiration() {
        return expiration;
    }

    public String getFullyQualifiedUsername() {
        return String.format("user://%s/%s", partnerId.value, username.value);
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    @Override
    public Credentials getPrincipal() {
        return username;
    }

    public boolean isLocal() {
        return local;
    }

    /**
     * convenience method so that callers can retrieve authorities as a Collection<Role> generic type
     */
    //    @Override
    public Collection<Role> getRoles() {
        return roles;
    }

//    public String getTokenString() {
//        return tokenString.value;
//    }

    public String getUsername() {
        return username.value;
    }

    public boolean isAdministrator() {
        return getAuthorities().contains(Role.ROLE_ADMINISTRATOR);
    }

    public String getScopeId() {
        return scopeId;
    }

    /**
     * add an expiration check to the default isAuthenticated() implementation
     */
    @Override
    public boolean isAuthenticated() {
        return super.isAuthenticated() && !isExpired();
    }

    public boolean isExpired() {
        // if (expiration == null) {
        return false;
        // }
        // return DateTimeUtil.utcNow().isAfter(expiration);
    }

    public void setExpiration(Instant expiration) {
        this.expiration = expiration;
    }

    public void setIssuedAt(Instant issuedAt) {
        this.issuedAt = issuedAt;
    }

    public void setTokenString(String tokenString) {
        this.tokenString = new Credentials(tokenString);
    }

    @Override
    public IdentityReference getIdentity() {
        return null;
    }

    public List<Group> getGroups() {
        return groups;
    }

    @Override
    public String getExpiry() {
        return null;
    }

    @Override
    public String getToken() {
        return tokenString.value;
    }

    public void setGroups(List<Group> groups) {
        this.groups = groups;
    }

    public Credentials getPartnerId() {
        return partnerId;
    }

    public void setTokenString(Credentials tokenString) {
        this.tokenString = tokenString;
    }

//    public Scope getScope() {
//        return scope;
//    }
//
//    public void setScope(Scope scope) {
//        this.scope = scope;
//    }

    public void setRoles(Collection<Role> roles) {
        this.roles = roles;
    }

    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }

    public void setLocal(boolean b) {
        this.local = b;
    }

    public class Credentials implements CredentialsContainer {

        private String value;

        public Credentials(String value) {
            this.value = value;
        }

        @Override
        public void eraseCredentials() {
            value = null;
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
