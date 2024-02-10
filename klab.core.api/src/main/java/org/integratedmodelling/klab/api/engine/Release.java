package org.integratedmodelling.klab.api.engine;

import org.integratedmodelling.klab.api.data.Version;

public interface Release {
    
    public static final String DEVELOP_RELEASE = "develop";
    public static final String LATEST_RELEASE = "latest";
    public static final String RELEASE = "release";
    public static final String DEFAULT_RELEASE_URL = "https://products.integratedmodelling.org/klab/";
    public static final String PRODUCT_PROPERTIES_FILE = "product.properties";
    public static final String PRODUCT_PROPERTIES_PROP = "klab.product.name";
	/**
	 * True if build is locally available
	 * 
	 * @param build
	 * @return
	 */
	boolean isInstalled();

	/**
	 * True if build is remotely available
	 * 
	 * @param build
	 * @return
	 */
	RunningInstance.Result isAvailable();
	
	
	/**
	 * True if build is up to date
	 * 
	 * @param build
	 * @return
	 */
	RunningInstance.Result isUpToDate();

	/**
	 * Date of deployment of build.
	 * 
	 * @param build
	 * @return
	 */
	long getBuildDate();
	
	/**
	 * Version of specified build.
	 * 
	 * @return
	 */
	Version getBuildVersion();

	
	
}
