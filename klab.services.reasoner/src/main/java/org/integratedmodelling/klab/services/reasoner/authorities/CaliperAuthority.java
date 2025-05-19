//package org.integratedmodelling.klab.services.reasoner.authorities;
//
//import org.eclipse.rdf4j.model.IRI;
//import org.eclipse.rdf4j.model.Model;
//import org.eclipse.rdf4j.model.Statement;
//import org.eclipse.rdf4j.model.Value;
//import org.eclipse.rdf4j.rio.RDFFormat;
//import org.eclipse.rdf4j.rio.Rio;
//import org.integratedmodelling.klab.api.collections.Pair;
//import org.integratedmodelling.klab.api.exceptions.KlabIOException;
//import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
//import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
//import org.integratedmodelling.klab.api.services.Authority;
//import org.integratedmodelling.klab.api.services.Codelist;
//import org.integratedmodelling.klab.api.services.resources.objects.AuthorityIdentity;
//import org.integratedmodelling.klab.api.services.resources.objects.AuthorityReference;
//import org.integratedmodelling.klab.configuration.Configuration;
//import org.integratedmodelling.klab.utilities.Utils;
//import org.mapdb.BTreeMap;
//import org.mapdb.DB;
//import org.mapdb.DBMaker;
//import org.mapdb.Serializer;
//import org.springframework.stereotype.Service;
//
//import java.io.File;
//import java.io.InputStream;
//import java.net.URI;
//import java.util.*;
//
////@Authority(id = CaliperAuthority.ID, description = CaliperAuthority.DESCRIPTION, catalogs = {"ISIC", /*
////                                                                                                      * "ICC10",
////                                                                                                      */
////        "ICC"/*
////              * , "WCACROPS"
////              */, "M49"/* , "FPCD" */, "SDGEO", "FOODEX2", /* "CPC20", */ "CPC"/* , "CPC21AG" */,
////        /* "CPC21FERT", *//* "FCL", */ "HS"/*
////                                            * , "WRB"
////                                            */}, version = Version.CURRENT)
//@Service
//public class CaliperAuthority implements Authority {
//
//    private static final long serialVersionUID = 3487169697917169159L;
//
//    public static final String ID = "CALIPER";
//    public static final String DESCRIPTION = "The FAO Caliper portal for statistical classifications";
//    private static final String SCHEME = "{SCHEME}";
//    private static final String QUERY_STRING = "{QUERY_STRING}";
//
//    private static final String DESCRIPTION_QUERY = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
//            + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\r\n"
//            + "PREFIX skos: <http://www.w3.org/2004/02/skos/core#>\r\n" + "SELECT ?code ?label_en ?concept ?broader WHERE {\r\n"
//            + "  ?concept rdf:type skos:Concept . \r\n" + "  ?concept skos:inScheme <{SCHEME}> .\r\n"
//            + "  ?concept skos:prefLabel ?label_en . FILTER(contains(lcase(str(?label_en)), '{QUERY_STRING}')) .\r\n"
//            + "  ?concept skos:notation ?code .\r\n" + "  ?concept skos:broader ?broader .\r\n" + "} order by ?code";
//
//    private static final String SPARQL_ENDPOINT = "https://stats-class.fao.uniroma2.it/AllVoc_Sparql/";
//    private static final Map<String, String> CALIPER_SCHEMES = new HashMap<>();
//    private static final Map<String, String> CALIPER_DESCRIPTIONS = new HashMap<>();
//    private static final Map<String, String> CALIPER_URLS = new HashMap<>();
//
//    static {
//
//        // TODO all this should come from caliper, filtered as needed and cached
//
//        CALIPER_SCHEMES.put("ISIC", "http://stats-class.fao.uniroma2.it/ISIC/rev4/scheme");
//        // CALIPER_SCHEMES.put("ICC10", "http://stats-class.fao.uniroma2.it/ICC/v1.0/scheme");
//        CALIPER_SCHEMES.put("ICC", "http://stats-class.fao.uniroma2.it/ICC/v1.1/scheme");
//        CALIPER_SCHEMES.put("M49", "http://stats-class.fao.uniroma2.it/geo/M49");
//        CALIPER_SCHEMES.put("SDGEO", "http://stats-class.fao.uniroma2.it/geo/M49/SDG-groups");
//        CALIPER_SCHEMES.put("FOODEX2", "http://stats-class.fao.uniroma2.it/foodex2/all");
//        // CALIPER_SCHEMES.put("CPC20", "http://stats-class.fao.uniroma2.it/CPC/v2.0/scheme");
//        CALIPER_SCHEMES.put("CPC", "http://stats-class.fao.uniroma2.it/CPC/v2.1/core");
//        // CALIPER_SCHEMES.put("CPC21FERT", "http://stats-class.fao.uniroma2.it/CPC/v2.1/fert");
//        // CALIPER_SCHEMES.put("FCL", "http://stats-class.fao.uniroma2.it/FCL/v2019/scheme");
//        CALIPER_SCHEMES.put("HS", "http://stats-class.fao.uniroma2.it/HS/fao_mapping_targets/scheme");
//
//        CALIPER_URLS.put("ISIC", "http://stats-class.fao.uniroma2.it/ISIC/rev4");
//        // CALIPER_SCHEMES.put("ICC10", "http://stats-class.fao.uniroma2.it/ICC/v1.0");
//        CALIPER_URLS.put("ICC", "http://stats-class.fao.uniroma2.it/ICC/v1.1");
//        CALIPER_URLS.put("M49", "http://stats-class.fao.uniroma2.it/geo/m49");
//        CALIPER_URLS.put("SDGEO", "http://stats-class.fao.uniroma2.it/geo/M49/SDG-groups");
//        CALIPER_URLS.put("FOODEX2", "http://stats-class.fao.uniroma2.it/foodex2");
//        // CALIPER_SCHEMES.put("CPC20", "http://stats-class.fao.uniroma2.it/CPC/v2.0");
//        CALIPER_URLS.put("CPC", "http://stats-class.fao.uniroma2.it/CPC/v2.1");
//        // CALIPER_SCHEMES.put("CPC21FERT", "http://stats-class.fao.uniroma2.it/CPC/v2.1/fert");
//        // CALIPER_SCHEMES.put("FCL", "http://stats-class.fao.uniroma2.it/FCL/v2019");
//        CALIPER_URLS.put("HS", "http://stats-class.fao.uniroma2.it/HS/fao_mapping_targets");
//
//        CALIPER_DESCRIPTIONS.put("ISIC", "ISIC Rev. 4 is a standard classification of economic activities "
//                + "arranged so that entities can be classified according to the activity they carry out. "
//                + "The categories of ISIC at the most detailed level (classes) are delineated according "
//                + "to what is, in most countries, the customary combination of activities described "
//                + "in statistical units and considers the relative importance of the activities " + "included in these classes.");
//        // CALIPER_DESCRIPTIONS.put("ICC10", "http://stats-class.fao.uniroma2.it/ICC/v1.0/scheme");
//        CALIPER_DESCRIPTIONS.put("ICC",
//                "The Indicative Crop Classification (ICC) was developed for the 2020 round of agricultural censuses. A manual "
//                        + "is available at http://www.fao.org/3/i4913e/i4913e.pdf");
//        CALIPER_DESCRIPTIONS.put("M49",
//                "The list of countries or areas in M49 includes those countries or area "
//                        + "for which statistical data are compiled by the Statistics Division of the United Nations Secretariat. "
//                        + "The names of countries or areas refer to their short form used in day-to-day operations of the United "
//                        + "Nations and not necessarily to their official name as used in formal documents. These names are "
//                        + "based on the United Nations Terminology Database (UNTERM).");
//        CALIPER_DESCRIPTIONS.put("SDGEO",
//                "These are the regional groups used to present data on progress towards "
//                        + "the Sustainable Development Goals worldwide. The country groupings are based on the geographic "
//                        + "regions defined under the Standard Country or Area Codes for Statistical Use (known as M49) of "
//                        + "the United Nations Statistics Division.");
//        CALIPER_DESCRIPTIONS.put("FOODEX2",
//                "FoodEx2 is a standardised food classification and description system published and maintained by EFSA.");
//        // CALIPER_DESCRIPTIONS.put("CPC20", "http://stats-class.fao.uniroma2.it/CPC/v2.0/scheme");
//        CALIPER_DESCRIPTIONS.put("CPC", "CPC constitutes a comprehensive classification of all goods and services. "
//                + "CPC presents categories for all products that can be the object of domestic or international "
//                + "transactions or that can be entered into stocks. The CPC classifies products based on the "
//                + "physical characteristics of goods or on the nature of the services rendered. CPC was developed "
//                + "primarily to enhance harmonization among various fields of economic and related statistics and "
//                + "to strengthen the role of national accounts as an instrument for the coordination of economic statistics.");
//        // CALIPER_DESCRIPTIONS.put("CPC21FERT",
//        // "http://stats-class.fao.uniroma2.it/CPC/v2.1/fert");
//        // CALIPER_DESCRIPTIONS.put("FCL", "http://stats-class.fao.uniroma2.it/FCL/v2019/scheme");
//        CALIPER_DESCRIPTIONS.put("HS",
//                "The Harmonized Commodity Description and Coding System (HS) is maintained by "
//                        + "the UNSD and is subject to copyright: this RDF 'basket' of HS terms is only a service concept scheme "
//                        + "containing codes and labels of HS terms (from all versions) used as correspondences of terms in "
//                        + "other statistical classifications.");
//
//        // CALIPER_SCHEMES.put("WCACROPS", "http://stats-class.fao.uniroma2.it/ICC/v1.0/scheme");
//        // CALIPER_SCHEMES.put("FPCD", "http://stats-class.fao.uniroma2.it/ICC/v1.0/scheme");
//        // CALIPER_SCHEMES.put("CPC21AG", "http://stats-class.fao.uniroma2.it/ICC/v1.0/scheme");
//
//    }
//
//    private DB db;
//    private BTreeMap<String, String> cache;
//    private String catalog;
//    private AuthorityReference capabilities;
//
//    public CaliperAuthority() {
//        this.db = DBMaker.fileDB(Configuration.INSTANCE.getDataPath("authorities") + File.separator + "caliper_ids.db")
//                .transactionEnable().closeOnJvmShutdown().make();
//        this.cache = db.treeMap("caliperAuthority", Serializer.STRING, Serializer.STRING).createOrOpen();
//        this.capabilities = new AuthorityReference();
//        this.capabilities.setSearchable(true);
//        this.capabilities.setFuzzy(true);
//        this.capabilities.setName(ID);
//        this.capabilities.setDescription(DESCRIPTION);
//        for (String s : CALIPER_DESCRIPTIONS.keySet()) {
//            this.capabilities.getSubAuthorities().add(Pair.of(s, CALIPER_DESCRIPTIONS.get(s)));
//        }
//
//    }
//
//    @Override
//    public Identity resolveIdentity(String identityId) {
//
//        if (catalog == null) {
//            throw new KlabIllegalStateException("The CALIPER authority can only be used through its secondary catalogs");
//        }
//
//        Identity source = null;
//        // search cache first
//        String cached = cache.get(identityId);
//        if (cached != null) {
//            source = Utils.Json.parseObject(cached, AuthorityIdentity.class);
//        } else {
//
//            String url = CALIPER_URLS.get(catalog) + "/" + identityId.replace('.', '-') + ".ttl";
//            try (InputStream input = new URI(url).toURL().openStream()) {
//                Model model = Rio.parse(input, RDFFormat.TURTLE);
//                Set<String> parents = new HashSet<>();
//                source = new AuthorityIdentity();
//
//                for (Statement statement : model) {
//
////                    System.out.println("CIAPA EL STATEMENT: " + statement);
//
//                    ((AuthorityIdentity) source).setAuthorityName(ID);
//                    switch(statement.getPredicate().getLocalName()) {
//                    case "notation":
//                        ((AuthorityIdentity) source).setId(stringValue(statement.getObject()));
//                        break;
//                    case "broader":
//                        parents.add(stringValue(statement.getObject()));
//                        break;
//                    case "narrower":
//                        // TODO use to build a graph
//                        break;
//                    case "closeMatch":
//                        // TODO add for metadata
//                        break;
//                    case "exactMatch":
//                        // TODO check if it's in a supported authority, incorporate equivalence if
//                        // so
//                        break;
//                    case "inScheme":
//                        // TODO should be redundant, but nothing wrong with saving in metadata
//                        break;
//                    case "prefLabel":
//                        // TODO check if we ever get a separate description
//                        // TODO check what happens with multilingual descriptions
//                        ((AuthorityIdentity) source).setDescription(stringValue(statement.getObject()));
//                        ((AuthorityIdentity) source).setLabel(stringValue(statement.getObject()));
//                        break;
//                    }
//                }
//
//                ((AuthorityIdentity) source).setConceptName(sanitize(catalog, ((AuthorityIdentity) source).getId()));
//                ((AuthorityIdentity) source).setLocator(ID + "." + catalog + ":" + ((AuthorityIdentity) source).getId());
//                for (String parent : parents) {
//                    if (((AuthorityIdentity) source).getParentIds() == null) {
//                        ((AuthorityIdentity) source).setParentIds(new ArrayList<>());
//                    }
//                    ((AuthorityIdentity) source).getParentIds().add(parent);
//                }
//
//            } catch (Exception e) {
//                throw new KlabIOException(e);
//            }
//
//            cache.put(identityId, Utils.Json.asString(source));
//            db.commit();
//        }
//
//        return source;
//    }
//
//    private String stringValue(Value object) {
//        if (object instanceof IRI) {
//            return ((IRI) object).getLocalName();
//        }
//        return object.stringValue();
//    }
//
//    private String sanitize(String catalog, String id2) {
//        return (catalog == null ? ID : catalog.toUpperCase()) + "_" + id2.replace('.', '_').replace('-', '_');
//    }
//
//    @Override
//    public Capabilities getCapabilities() {
//        return this.capabilities;
//    }
//
//    @Override
//    public List<Identity> search(String query, String catalog) {
//
//        String q = DESCRIPTION_QUERY.replace(SCHEME, CALIPER_SCHEMES.get(catalog)).replace(QUERY_STRING, query);
//        HttpResponse<JsonNode> response = Unirest.post(SPARQL_ENDPOINT).accept("application/sparql-results+json")
//                .contentType("application/sparql-query").body(q).asJson();
//
//        List<Identity> ret = new ArrayList<>();
//
//        if (response.isSuccess()) {
//            try {
//                JSONObject result = response.getBody().getObject();
//                for (Object zoz : result.getJSONObject("results").getJSONArray("bindings")) {
//
//                    JSONObject res = (JSONObject) zoz;
//                    String code = res.getJSONObject("code").getString("value");
//                    String name = res.getJSONObject("label_en").getString("value");
//                    String uri = res.getJSONObject("concept").getString("value");
//
//                    AuthorityIdentity identity = new AuthorityIdentity();
//
//                    identity.setAuthorityName(ID + (catalog == null ? "" : ("." + catalog)));
//                    identity.setBaseIdentity(ID);
//                    identity.setConceptName(code);
//                    identity.setId(code);
//                    identity.setLabel(name);
//                    identity.setDescription(name);
//                    identity.setLocator(ID + (catalog == null ? "" : ("." + catalog)) + ":" + identity.getConceptName());
//
//                    ret.add(identity);
//                    // TODO internal try/catch, add error message to identity
//
//                }
//            } catch (Throwable t) {
//                // TODO monitor the error, return nothing
//                throw new KlabInternalErrorException(t);
//            }
//        }
//
//        return ret;
//    }
//
//    public static void main(String[] args) {
//
//        try (InputStream input = new URI("http://stats-class.fao.uniroma2.it/CPC/v2.0/0.ttl").toURL().openStream()) {
//            Model model = Rio.parse(input, RDFFormat.TURTLE);
//            for (Statement statement : model) {
//                System.out.println("CIAPA EL STATEMENT: " + statement);
//            }
//        } catch (Exception e) {
//            throw new KlabIOException(e);
//        }
//
//    }
//
//	@Override
//	public String getName() {
//		return ID;
//	}
//
//	@Override
//	public Codelist getCodelist() {
//		// TODO this may be less than obvious with Caliper
//		return null;
//	}
//
//    @Override
//    public Authority subAuthority(String catalog) {
//        // TODO Auto-generated method stub
//        return this;
//    }
//
//	@Override
//	public String getServiceName() {
//		return ID;
//	}
//
//}
