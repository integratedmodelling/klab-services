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
package org.integratedmodelling.klab.api.lang.impl;

import java.util.Map;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.collections.impl.ParametersImpl;
import org.integratedmodelling.klab.api.lang.Annotation;

/**
 * The Class Metadata.
 *
 * @author ferdinando.villa
 * @version $Id: $Id
 */
public class AnnotationImpl extends ParametersImpl<String> implements Annotation {

    private static final long serialVersionUID = -8898359231943508540L;
    
    private static String ANNOTATION_NAME_KEY = "#name";
    private static String ANNOTATION_CLASS_KEY = "#class";

    /**
     * Instantiates a new metadata.
     *
     * @param metadata the metadata
     */
    public AnnotationImpl(Parameters<String> data) {
        putAll(data);
    }

    /**
     * Instantiates a new metadata.
     *
     * @param data the data
     */
    public AnnotationImpl(Map<String, Object> data) {
        super(data);
    }

    /**
     * Instantiates a new metadata.
     */
    public AnnotationImpl() {
    }

    @Override
    public String getName() {
        return get(ANNOTATION_NAME_KEY, String.class);
    }

    @Override
    public String getContentClass() {
        return get(ANNOTATION_CLASS_KEY, String.class);
    }

    public void setName(String name) {
        put(ANNOTATION_NAME_KEY, name);
    }
    
    public void setClass(String cls) {
        put(ANNOTATION_CLASS_KEY, cls);
    }

}
