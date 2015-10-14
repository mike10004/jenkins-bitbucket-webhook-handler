/*
 * (c) 2015 Mike Chaberski, distributed under MIT License
 */
package com.github.mike10004.jenkinsbbhook;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import javax.servlet.ServletContext;

/**
 *
 * @author mchaberski
 */
public class ServletInitParamValueProvider implements Function<String, String> {
    
    private final ServletContext servletContext;

    public ServletInitParamValueProvider(ServletContext servletContext) {
        this.servletContext = Preconditions.checkNotNull(servletContext);
    }

    @Override
    public String apply(String paramName) {
        return servletContext.getInitParameter(paramName);
    }
    
}
