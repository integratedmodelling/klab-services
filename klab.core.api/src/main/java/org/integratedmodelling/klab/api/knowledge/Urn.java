package org.integratedmodelling.klab.api.knowledge;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.integratedmodelling.klab.api.data.Version;


/**
 * Simple helper to decompose a URN into its constituents and access them with
 * proper semantics.
 * 
 * URN is formatted as <node name>:<originator>:<namespace>:<resource id>
 * 
 * @author Ferd
 *
 */
public class Urn {

	final public static String SINGLE_PARAMETER_KEY = "value";
    final public static String KLAB_URN_PREFIX = "urn:klab:";
    final public static String LOCAL_URN_PREFIX = "urn:klab:local:";
    final public static String VOID_URN_PREFIX = "urn:klab:void:";
    final public static String LOCAL_FILE_PREFIX = "file:";

    final public static Pattern URN_RESOURCE_PATTERN = Pattern.compile("[A-z]+:[A-z]+:[A-z]+:[A-z]+(#.+)?");
    final public static Pattern URN_KIM_OBJECT_PATTERN = Pattern.compile("[a-z]+(\\.[a-z]+)+");
    final public static Pattern URN_CONCEPT_PATTERN = Pattern.compile("[a-z]+:[A-Z]+");
    
	private String urn;
	private String fullUrn;
	private String[] tokens;
	private Map<String, String> parameters = new HashMap<>();

	public enum Type {
	    /**
	     * A resource URN
	     */
	    RESOURCE,
	    /**
	     * A model, acknowledgement, namespace/scenario, or define
	     */
	    KIM_OBJECT,
	    /**
	     * A concept or observable (no guarantee that it's meaningful)
	     */
	    OBSERVABLE,
	    /**
	     * An http-based URL, observable only when it points to a remote observation (no guarantee)
	     */
	    REMOTE_URL,
	    /**
	     * Returned by classify() when the passed string cannot be understood as one of the above
	     */
	    UNKNOWN
	}
	
	
	/**
	 * Pass a valid URN string. For now does no validation.
	 * 
	 * @param urn
	 */
	public Urn(String urn) {
		fullUrn = urn;
		if (urn.startsWith(KLAB_URN_PREFIX)) {
			urn = urn.substring(KLAB_URN_PREFIX.length());
		}
		if (urn.contains("#")) {
			if (urn.contains("#")) {
				String[] uu = urn.split("#");
				urn = uu[0];
				for (String s : uu[1].split("&")) {
					if (s.contains("=")) {
						String[] kv = s.split("=");
						parameters.put(kv[0], kv[1]);
					} else {
						if (parameters.containsKey(SINGLE_PARAMETER_KEY)) {
							parameters.put(SINGLE_PARAMETER_KEY, parameters.get(SINGLE_PARAMETER_KEY) + "," + s);
						} else {
							parameters.put(SINGLE_PARAMETER_KEY, s);
						}
					}
				}
			}
		}
		this.urn = urn;
		this.tokens = urn.split(":");
	}

	public Urn(String urn, Map<String, String> urnParameters) {
		this(urn);
		if (urnParameters != null && !urnParameters.isEmpty()) {
			this.parameters.putAll(urnParameters);
			String s = "";
			for (String key : urnParameters.keySet()) {
				s += (s.isEmpty() ? "" : "&") + ("value".equals(key) ? "" : (key + "="));
				String val = urnParameters.get(key);
				s += val.replace(",", "&");
			}
			this.fullUrn += "#" + s;
		}
	}

	/**
	 * Node name, mandatory in all URNs. In universal ones it will be "klab". In
	 * local ones, it will be "local".
	 * 
	 * @return the node name.
	 */
	public String getNodeName() {
		return tokens[0];
	}

	/**
	 * Whether the URN should be processed by the same engine that generates it.
	 * 
	 * @return true if local
	 */
	public boolean isLocal() {
		return getNodeName().equals("local");
	}
	
	/**
	 * Return either an empty array for no parameter present, or an array of
	 * values with one or more values for the passed parameter set in the url
	 * as independent parts. E.g. url#a&b&C would return a, b, C.
	 * @param parameter
	 * @return
	 */
	public String[] getSplitParameter(String parameter) {
		if (parameters.containsKey(parameter)) {
			String ss = parameters.get(parameter);
			if (ss == null) {
				ss = "";
			}
			return ss.split(",");
		}
		return new String[] {};
	}

	/**
	 * Whether the URN can be processed by any node. In this case, the URN has no
	 * attached data and the catalog name is the ID of the adapter that will process
	 * it. If we don't have the adapter, we will choose a node among those that do,
	 * using the load factor or some other criterion.
	 * 
	 * @return true if universal.
	 */
	public boolean isUniversal() {
		return getNodeName().equals("klab");
	}

	/**
	 * Return the catalog for the resource. In local resources, this is the
	 * originator ID. In universal resources, this is the adapter ID. Never null.
	 * 
	 * @return the originator
	 */
	public String getCatalog() {
		return tokens[1];
	}

	/**
	 * Return the namespace of the resource.
	 */
	public String getNamespace() {
		return tokens.length > 2 ? tokens[2] : null;
	}

	/**
	 * Return the resource ID. Never null.
	 * 
	 * @return the resource id.
	 */
	public String getResourceId() {
		return tokens.length > 3 ? tokens[3] : null;
	}

	/**
	 * Return the version, if any.
	 * 
	 * @return
	 */
	public Version getVersion() {
		return tokens.length > 4 ? new Version(tokens[4]) : null;
	}

	/**
	 * Unmodified URN string without parameters
	 * 
	 * @return the unmodified URN.
	 */
	public String getUrn() {
		return urn;
	}

	@Override
	public String toString() {
		return fullUrn;
	}

	public Map<String, String> getParameters() {
		return parameters;
	}
		
	public static Type classify(String urn) {
	    
	    if (urn.startsWith("http") && urn.contains("//:")) {
	        return Type.REMOTE_URL;
	    } else if (URN_RESOURCE_PATTERN.matcher(urn).find()) {
	        return Type.RESOURCE;
	    } else if (URN_KIM_OBJECT_PATTERN.matcher(urn).find()) {
            return Type.KIM_OBJECT;
        } else if (URN_CONCEPT_PATTERN.matcher(urn).find()) {
            return Type.OBSERVABLE;
        }
	    
	    return Type.UNKNOWN;
	}

}