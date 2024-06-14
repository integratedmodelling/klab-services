package org.integratedmodelling.klab.services.reasoner.authorities;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentNavigableMap;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.services.Authority;
import org.integratedmodelling.klab.api.services.Codelist;
import org.integratedmodelling.klab.api.services.resources.objects.AuthorityIdentity;
import org.integratedmodelling.klab.api.services.resources.objects.AuthorityReference;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.utilities.Utils;
// import org.integratedmodelling.klab.rest.AuthorityIdentity;
// import org.integratedmodelling.klab.rest.AuthorityReference;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.serializer.GroupSerializer;
import org.springframework.web.client.RestTemplate;

// @Authority(id = GBIFAuthority.ID, description = GBIFAuthority.DESCRIPTION, catalogs = {
// "KINGDOM", "PHYLUM", "CLASS",
// "ORDER", "FAMILY", "GENUS", "SPECIES" }, version = Version.CURRENT)
public class GBIFAuthority implements Authority {

    private static final long serialVersionUID = -7041302223240589498L;

    static final int pageSize = 100;
    static final public String ID = "GBIF";
    static final public String DESCRIPTION = "Global Biodiversity Information Facility (GBIF)\n\n"
            + "GBIF provides stable identities for taxonomic entities. The available catalogs "
            + " authority provides k.LAB identities at different taxonomic ranks.\n\n"
            + "For more details, see the GBIF project at http://gbif.org";

    public static final String KINGDOM_RANK = "kingdom";
    public static final String PHYLUM_RANK = "phlyum";
    public static final String CLASS_RANK = "class";
    public static final String ORDER_RANK = "order";
    public static final String FAMILY_RANK = "family";
    public static final String GENUS_RANK = "genus";
    public static final String SPECIES_RANK = "species";

    private String rank = null;
    private AuthorityReference capabilities;
    
    // protected String rank;
    transient private RestTemplate client = new RestTemplate();

    transient DB db = null;
    transient ConcurrentNavigableMap<String, String> cache = null;
    
    static protected List<String> ranks = null;

    static {
        ranks = new ArrayList<>();
        ranks.add(KINGDOM_RANK);
        ranks.add(PHYLUM_RANK);
        ranks.add(CLASS_RANK);
        ranks.add(ORDER_RANK);
        ranks.add(FAMILY_RANK);
        ranks.add(GENUS_RANK);
        ranks.add(SPECIES_RANK);
    }

    public GBIFAuthority() {
        
        this.db = DBMaker.fileDB(ServiceConfiguration.INSTANCE.getDataPath("authorities") + File.separator + "gbif_ids.db")
                .transactionEnable().closeOnJvmShutdown().make();
        this.cache = db.treeMap("gbifAuthority", GroupSerializer.STRING, GroupSerializer.STRING).createOrOpen();

        this.capabilities = new AuthorityReference();
        this.capabilities.setSearchable(true);
        this.capabilities.setDescription(DESCRIPTION);
        this.capabilities.getDocumentationFormats().add("text/plain");
        this.capabilities.getSubAuthorities().add(Pair.of("", "Any rank"));
        for (String rank : ranks) {
            this.capabilities.getSubAuthorities().add(Pair.of(rank.toUpperCase(), org.integratedmodelling.common.utils.Utils.Strings.capitalize(rank) + " rank"));
        }
        this.capabilities.setName(ID);

    }

    @Override
    public Identity resolveIdentity(String identityId) {

        Identity source = null;
        // search cache first
        String cached = cache.get(identityId);
        if (cached != null) {
            source = Utils.Json.parseObject(cached, AuthorityIdentity.class);
        } else {

            if (rank != null) {
                rank = rank.toLowerCase();
                int rankIndex = -1;
                for (int i = 0; i < ranks.size(); i++) {
                    if (rank.equals(ranks.get(i))) {
                        rankIndex = i;
                        break;
                    }
                }
                if (rankIndex < 0) {
                    throw new KlabValidationException("GBIF authority: invalid catalog " + ranks);
                }
            }

            // if not in there, use network
            try {
                source = parseResult(client.getForObject(getDescribeURL(identityId), Map.class));
                // TODO check that the catalog is what we expect
                cache.put(identityId, Utils.Json.asString(source));
                db.commit();
            } catch (Throwable t) {
                // just return null
            }
        }

        if (rank != null) {
            /*
             * TODO verify that the catalog is what we passed.
             */
        }

        return source;
    }

    public Identity parseResult(Map<?, ?> o) {

        if (o == null) {
            return null;
        }

        AuthorityIdentity result = new AuthorityIdentity();

        Map<String, String> desc = new HashMap<>();

        String key = getString(o, "key");
        desc.put(KINGDOM_RANK, getString(o, "kingdom"));
        desc.put(PHYLUM_RANK, getString(o, "phylum"));
        desc.put(CLASS_RANK, getString(o, "class"));
        desc.put(ORDER_RANK, getString(o, "order"));
        desc.put(FAMILY_RANK, getString(o, "family"));

        String parent = getString(o, "parent");
        String parentKey = getString(o, "parentKey");
        // String kingdomKey = getString(o, "kingdomKey");
        // String classKey = getString(o, "classKey");
        // String phylumKey = getString(o, "phylumKey");
        // String orderKey = getString(o, "orderKey");
        // String familyKey = getString(o, "familyKey");
        String authorship = getString(o, "authorship");
        String canonicalName = getString(o, "canonicalName");

        String parents = null;
        String rank = null;
        if (parent != null) {
            for (int i = ranks.size() - 1; i >= 0; i--) {
                if (parent.equals(desc.get(ranks.get(i)))) {
                    rank = ranks.get(i + 1);
                    parents = desc.get(ranks.get(i));
                } else if (parents != null && desc.get(ranks.get(i)) != null) {
                    parents += ", " + desc.get(ranks.get(i));
                }
            }
        }

        result.setAuthorityName(ID);
        result.setId(key);
        result.setLabel(canonicalName);
        result.setDescription((rank == null ? "" : (org.integratedmodelling.common.utils.Utils.Strings.capitalize(rank) + ": ")) + canonicalName
                + ((authorship == null || authorship.isEmpty()) ? "" : (" (" + authorship + ")"))
                + (parents == null ? "" : (". " + parents + ".")));
        result.setConceptName("gbif" + key);
        result.setLocator(ID + ":" + key);
        if (parentKey != null) {
            result.setParentIds(Collections.singletonList(parentKey));
        }
        return result;
    }

    @Override
    public List<Identity> search(String query, String catalog) {
        List<Identity> ret = new ArrayList<>();
        Object[] results = client.getForObject(getSearchURL(query, catalog, 0), Object[].class);
        for (Object o : results) {
            if (o instanceof Map<?, ?> && ((Map<?, ?>) o).containsKey("key")) {
                Identity r = parseResult((Map<?, ?>) o);
                if (r != null) {
                    ret.add(r);
                }
            }
        }
        return ret;
    }

    private String getString(Object o, String string) {
        return ((Map<?, ?>) o).containsKey(string) ? ((Map<?, ?>) o).get(string).toString() : null;
    }

    private URI getSearchURL(String query, String catalog, int page) {
        String ret = "http://api.gbif.org/v1/species/suggest?q=" + Utils.Escape.forURL(query) + "&limit=100&rank=" + catalog;
        if (page > 0) {
            ret += "&offset=" + (page * pageSize);
        }
        try {
            return new URI(ret);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    //
    private URI getDescribeURL(String id) {
        try {
            return new URI("http://api.gbif.org/v1/species/" + id);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    // @Override
    // public void document(String identityId, String mediaType, OutputStream destination) {
    // // TODO Auto-generated method stub
    //
    // }

    @Override
    public Capabilities getCapabilities() {
        return capabilities;
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
		// TODO Auto-generated method stub
		return ID;
	}
}