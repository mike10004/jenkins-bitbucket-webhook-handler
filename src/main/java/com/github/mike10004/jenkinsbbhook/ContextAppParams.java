/*
 * (c) 2015 Mike Chaberski, distributed under MIT License
 */
package com.github.mike10004.jenkinsbbhook;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;

/**
 *
 * @author mchaberski
 */
public class ContextAppParams implements AppParams {
    
    public static final String PARAM_JENKINS_BASE_URL = "WebhookHandler:jenkinsBaseUrl";
    public static final String PARAM_SIMULATION = "WebhookHandler:simulation";
    
    private final Function<String, String> paramValueProvider;

    public ContextAppParams(Function<String, String> paramValueProvider) {
        this.paramValueProvider = Preconditions.checkNotNull(paramValueProvider);
    }

    @Override
    public String getJenkinsBaseUrl() {
        return paramValueProvider.apply(PARAM_JENKINS_BASE_URL);
    }

    @Override
    public boolean isSimulation() {
        return Boolean.parseBoolean(paramValueProvider.apply(PARAM_SIMULATION));
    }

}
