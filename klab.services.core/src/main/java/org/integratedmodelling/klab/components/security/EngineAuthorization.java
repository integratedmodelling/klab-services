/**
 * - BEGIN LICENSE: 4552165799761088680 -
 *
 * Copyright (C) 2014-2018 by: - J. Luke Scott <luke@cron.works> - Ferdinando Villa
 * <ferdinando.villa@bc3research.org> - any other authors listed in the @author annotations in
 * source files
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * Affero General Public License Version 3 or any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the Affero
 * General Public License for more details.
 *
 * You should have received a copy of the Affero General Public License along with this program; if
 * not, write to the Free Software Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA. The license is also available at: https://www.gnu.org/licenses/agpl.html
 *
 * - END LICENSE -
 */
package org.integratedmodelling.klab.components.security;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

import org.integratedmodelling.klab.api.identities.Group;
import org.integratedmodelling.klab.rest.AuthenticatedIdentity;
import org.integratedmodelling.klab.rest.GroupImpl;
import org.integratedmodelling.klab.rest.IdentityReference;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.CredentialsContainer;

/**
 * The authorization for any calls authenticated through an engine JWT.
 *
 * The field names represent the chosen vocabulary we're using in k.LAB; they might be different in
 * JWT and/or Spring parlance, and therefore the calls coming in and out of this class might feel a
 * bit awkward - for instance, a JWT "subject" is set here as a "username" and is returned using the
 * Spring method getPrincipal().
 */
public class EngineAuthorization extends AbstractAuthenticationToken implements AuthenticatedIdentity {

    private static final long serialVersionUID = -7156637554497821495L;

    private static final int TOKEN_TTL_SECONDS = 60 * 60 * 24 * 7 * 4; // 4 weeks

    protected Instant expiration;

    /**
     * The time at which the token was issued - this would be the JWT token in the case of a typical
     * login by either username or cert file.
     */
    private Instant issuedAt;

    /**
     * The groups associated with the token. These come from the authenticating hub.
     */
    private List<Group> groups = new ArrayList<>();

    /**
     * The ID of the Integrated Modelling partner which owns the directory containing the user being
     * authenticated. For example, "im" is the ID for the main directory
     *
     * Spring equivalent: [none] JWT equivalent: issuer ("iss" claim)
     */
    private final Credentials partnerId;

    /**
     * A collection of partner-specific permissions being granted with this Authentication. For
     * example, a PartnerAuthority [im, public-network] would indicate a "public-network" permission
     * level granted by the "im" partner directory.
     */
    private final Collection<Role> roles;

    /**
     * JWT token string, in 3 dot-separated sections. Each section is base 64 encoded.
     */
    private Credentials tokenString;

    /**
     * The username of the logged in user. Usernames will be unique within a partner directory, but
     * there may be duplicates between partners, so usernames should always be identified with
     * respect to the directory in which they are stored.
     */
    private final Credentials username;

    public EngineAuthorization(String partnerId, String username) {
        this(partnerId, username, null);
    }

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

    /**
     * convenience method so that callers can retrieve authorities as a Collection<Role> generic
     * type
     */
//    @Override
    public Collection<Role> getRoles() {
        return roles;
    }

    public String getTokenString() {
        return tokenString.value;
    }

    public String getUsername() {
        return username.value;
    }

    public boolean isAdministrator() {
        return getAuthorities().contains(Role.ROLE_ADMINISTRATOR);
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
        return null;
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
