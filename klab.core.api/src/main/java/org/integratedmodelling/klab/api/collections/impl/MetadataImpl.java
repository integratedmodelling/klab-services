/*
 * This file is part of k.LAB.
 * 
 * k.LAB is free software: you can redistribute it and/or modify it under the terms of the Affero
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * A copy of the GNU Affero General Public License is distributed in the root directory of the k.LAB
 * distribution (LICENSE.txt). If this cannot be found see <http://www.gnu.org/licenses/>.
 * 
 * Copyright (C) 2007-2018 integratedmodelling.org and any authors mentioned in author tags. All
 * rights reserved.
 */
package org.integratedmodelling.klab.api.collections.impl;

import java.util.LinkedHashMap;
import java.util.Map;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Metadata;

/**
 * The Class Metadata.
 *
 * @author ferdinando.villa
 * @version $Id: $Id
 */
public class MetadataImpl extends ParametersImpl<String> implements Metadata {

    private static final long serialVersionUID = -8898359231943508540L;

    /**
     * Instantiates a new metadata.
     *
     * @param metadata the metadata
     */
    public MetadataImpl(Parameters<String> metadata) {
        putAll(metadata);
    }

    /**
     * Instantiates a new metadata.
     *
     * @param data the data
     */
    public MetadataImpl(Map<String, Object> data) {
        super(data);
    }

    /**
     * Instantiates a new metadata.
     */
    public MetadataImpl() {
    }

    /**
     * Copy.
     *
     * @return the metadata
     */
    public MetadataImpl copy() {
        MetadataImpl ret = new MetadataImpl();
        ret.putAll(this);
        return ret;
    }

    @Override
    public Object getCaseInsensitive(String attr) {

        for (String s : keySet()) {
            if (s.compareToIgnoreCase(attr) == 0) {
                return get(s);
            }
        }

        return null;
    }

    public String getTitle() {
        return get(DC_TITLE, "No title");
    }

    public String getOriginator() {
        return get(DC_ORIGINATOR, "No originator");
    }

    public String getKeywords() {
        return get(IM_KEYWORDS, "No keyword list");
    }

    public String getDescription() {
        return get(DC_COMMENT, "No description");
    }

    public String getUrl() {
        return get(DC_URL, "No URL");
    }

}
