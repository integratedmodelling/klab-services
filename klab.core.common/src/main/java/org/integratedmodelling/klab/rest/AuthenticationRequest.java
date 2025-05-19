package org.integratedmodelling.klab.rest;

import org.integratedmodelling.klab.api.authentication.KlabCertificate;

import java.util.Objects;

public abstract class AuthenticationRequest {
    protected String name;
    protected String certificate;
    protected String key;
    protected KlabCertificate.Level level;
    private String email;

    public AuthenticationRequest() {
        super();
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getCertificate() {
        return certificate;
    }

    public void setCertificate(String certificate) {
        this.certificate = certificate;
    }

    public KlabCertificate.Level getLevel() {
        return level;
    }

    public void setLevel(KlabCertificate.Level level) {
        this.level = level;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        AuthenticationRequest that = (AuthenticationRequest) o;
        return Objects.equals(name, that.name) && Objects.equals(certificate, that.certificate) && Objects.equals(key, that.key) && level == that.level && Objects.equals(email, that.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, certificate, key, level, email);
    }
}
