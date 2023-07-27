package org.integratedmodelling.klab.services.reasoner.authorities;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.integratedmodelling.klab.api.services.Authority;
import org.integratedmodelling.klab.api.services.Codelist;
import org.integratedmodelling.klab.api.services.resources.objects.AuthorityIdentity;
import org.integratedmodelling.klab.api.services.resources.objects.AuthorityReference;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.configuration.Configuration;
import org.integratedmodelling.klab.logging.Logging;
import org.integratedmodelling.klab.utilities.Utils;
import org.integratedmodelling.klab.utils.UrlEscape;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import kong.unirest.HttpResponse;
import kong.unirest.Unirest;

/**
 * Uses the NIH online service from https://cactus.nci.nih.gov/chemical/structure (see
 * https://cactus.nci.nih.gov/chemical/structure_documentation) with caching.
 * 
 * @author Ferd
 *
 */
// @Authority(id = IUPACAuthority.ID, description = IUPACAuthority.DESCRIPTION, version =
// Version.CURRENT)
public class IUPACAuthority implements Authority {

    private static final long serialVersionUID = 6650535874804189123L;

    public static final String ID = "IUPAC";
    public static final String DESCRIPTION = "The IUPAC authority resolves chemical species names, InChl strings and identifiers.\n\n The only requirement for the"
            + " identifier is to be unambiguous. The NIH resolution service supports several languages, so IUPAC:H2O or IUPAC:agua will be equivalent.";
    private static final String KEY_PATTERN = "[A-Z]{14}-[A-Z]{10}-[A-Z]";
    private static final String RESOLVER_URL = "https://cactus.nci.nih.gov/chemical/structure";

    private DB db = null;
    private ConcurrentNavigableMap<String, String> cache = null;
    private Pattern pattern;
    private AuthorityReference capabilities;

    public IUPACAuthority() {
        this.db = DBMaker.fileDB(Configuration.INSTANCE.getDataPath("authorities") + File.separator + "iupac_ids.db")
                .closeOnJvmShutdown().transactionEnable().make();
        this.cache = db.treeMap("iupacAuthority", Serializer.STRING, Serializer.STRING).createOrOpen();
        Unirest.config().verifySsl(false);
        this.capabilities = new AuthorityReference();
        this.capabilities.setSearchable(true);
        this.capabilities.setFuzzy(true);
        this.capabilities.setDescription(DESCRIPTION);
        this.capabilities.getDocumentationFormats().add("text/plain");
        this.capabilities.getDocumentationFormats().add("image/gif");
        this.capabilities.setName(ID);
    }

    @Override
    public Identity resolveIdentity(String identityId) {

        String value = this.cache.get(identityId);
        if (value != null) {
            return Utils.Json.parseObject(value, AuthorityIdentity.class);
        }

        String original = identityId;
        String standardKey = null;
        String standardName = null;

        AuthorityIdentity ret = new AuthorityIdentity();
        if (!isStdKey(identityId)) {
            standardKey = getIdentity(identityId);
            if (standardKey == null) {
                ret.getNotifications().add(
                        Notification.of("Identity " + identityId + " is unknown to authority " + ID, Notification.Level.Error));
            }
        }
        String officialName = null;
        if (standardKey != null) {
            standardName = getInChl(identityId);
            officialName = getIUPACName(identityId);
            if (officialName == null) {
                ret.getNotifications()
                        .add(Notification.of("Identity " + identityId + " has no common name in " + ID, Notification.Level.Info));
                officialName = standardName;
            }
            if (standardName == null) {
                ret.getNotifications().add(
                        Notification.of("Identity " + identityId + " has has no official IUPAC name", Notification.Level.Error));
            }
        }

        ret.setAuthorityName(ID);
        ret.setLocator(ID + ":" + standardKey);

        if (standardKey != null && standardName != null) {
            ret.setConceptName(standardKey.toLowerCase().replace('-', '_'));
            ret.setDescription(officialName + " (" + getFormula(identityId) + ")");
            ret.setLabel(original);
            ret.setId(identityId);
        }
        boolean ws = Utils.Strings.containsWhitespace(original);
        ret.setLocator(ID + (ws ? ":'" : ":") + original + (ws ? "'" : ""));

        /*
         * cache also the errors
         */
        this.cache.put(original, Utils.Json.asString(ret));
        this.db.commit();

        return ret;
    }

    /**
     * Check for the official ID in XXXXXXXXXXXXXX-YYYYYYYYYY-Z format, with 14, 12 and 1 uppercase
     * characters.
     * 
     * @param identityId
     * @return
     */
    private boolean isStdKey(String identityId) {
        if (this.pattern == null) {
            this.pattern = Pattern.compile(KEY_PATTERN);
        }
        Matcher matcher = pattern.matcher(identityId);
        return matcher.matches();
    }

    // @Override
    // public void document(String identityId, String mediaType, OutputStream destination) {
    // switch(mediaType) {
    // case "text/plain":
    // break;
    // case "image/png":
    // // https://cactus.nci.nih.gov/chemical/structure/aspirin/image
    // break;
    // }
    // }

    @Override
    public List<Identity> search(String query, String catalog) {
        return Collections.singletonList(resolveIdentity(query));
    }

    @Override
    public Capabilities getCapabilities() {
        return this.capabilities;
    }
    public List<String> getNames(String identity) {
        List<String> ret = new ArrayList<>();
        HttpResponse<String> response = Unirest.get(RESOLVER_URL + "/" + UrlEscape.escapeurl(identity) + "/" + "names")
                .asString();
        if (response.isSuccess()) {
            for (String ss : response.getBody().split("\\r?\\n")) {
                ret.add(ss);
            }
        }
        return ret;
    }

    public String getFormula(String identity) {
        HttpResponse<String> response = Unirest.get(RESOLVER_URL + "/" + UrlEscape.escapeurl(identity) + "/" + "formula")
                .asString();
        if (response.isSuccess()) {
            return response.getBody();
        }
        return null;
    }

    public String getInChl(String identity) {
        HttpResponse<String> response = Unirest.get(RESOLVER_URL + "/" + UrlEscape.escapeurl(identity) + "/" + "stdinchi")
                .asString();
        if (response.isSuccess()) {
            String ret = response.getBody();
            if (ret.contains("=")) {
                int idx = ret.indexOf('=');
                ret = ret.substring(idx + 1);
            }
            return ret;
        }
        return null;
    }

    public String getIUPACName(String identity) {
        try {
            HttpResponse<String> response = Unirest.get(RESOLVER_URL + "/" + UrlEscape.escapeurl(identity) + "/" + "iupac_name")
                    .asString();
            if (response.isSuccess()) {
                return response.getBody();
            }
        } catch (Throwable t) {
            Logging.INSTANCE.error("IUPAC: name call to IUPAC authority caused exception: " + t.getMessage());
        }
        return null;
    }

    public String getIdentity(String query) {

        String ret = null;
        String url = RESOLVER_URL + "/" + UrlEscape.escapeurl(query) + "/" + "stdinchikey";
        try {
            HttpResponse<String> response = Unirest.get(url).asString();
            if (response.isSuccess()) {
                ret = response.getBody();
                if (ret.contains("=")) {
                    int idx = ret.indexOf('=');
                    ret = ret.substring(idx + 1);
                }
            } else {
                Logging.INSTANCE.error("IUPAC: call to " + url + " returned status code " + response.getStatus());
            }
        } catch (Throwable t) {
            Logging.INSTANCE.error("IUPAC: call to " + url + " caused exception: " + t.getMessage());
        }
        return ret;
    }

    public static void main(String[] args) {
        IUPACAuthority auth = new IUPACAuthority();
        for (String c : new String[]{"XLYOFNOQVPJJNP-UHFFFAOYSA-N", "Water", "Aspirin", "Cyanometaacrylate", "Polyacrylamide"}) {
            System.out.println("Looking up " + c);
            System.out.println("  Standard key: " + (auth.isStdKey(c) ? "YES" : "NO"));
            String identity = auth.getIdentity(c);
            if (identity == null) {
                System.out.println("  Identity: UNKNOWN");
            } else {
                System.out.println("  Identity: " + identity);
                System.out.println("  IUPAC name: " + auth.getIUPACName(identity));
                System.out.println("  InChl: " + auth.getInChl(identity));
                System.out.println("  Brute formula: " + auth.getFormula(identity));
                System.out.println("  Names:");
                for (String s : auth.getNames(identity)) {
                    System.out.println("    " + s);
                }
            }
        }
    }

    // @Override
    public boolean setup(Map<String, String> options) {
        if ("true".equals(options.get("clearcache"))) {
            try {
                cache.clear();
                db.commit();
            } catch (Throwable t) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String getName() {
        return ID;
    }

    @Override
    public Codelist getCodelist() {
        return null;
    }

    @Override
    public Authority subAuthority(String catalog) {
        // TODO Auto-generated method stub
        return this;
    }

	@Override
	public String getServiceName() {
		return ID;
	}

}
