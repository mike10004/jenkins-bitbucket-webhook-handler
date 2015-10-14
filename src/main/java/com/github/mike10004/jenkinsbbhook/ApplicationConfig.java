/*
 * (c) 2015 Mike Chaberski, distributed under MIT License
 */
package com.github.mike10004.jenkinsbbhook;

import java.util.Set;
import javax.ws.rs.core.Application;

/**
 *
 * @author mchaberski
 */
@javax.ws.rs.ApplicationPath("webhook")
public class ApplicationConfig extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> resources = new java.util.HashSet<>();
        addRestResourceClasses(resources);
        resources.add(WebhookHandler.class);
        return resources;
    }

    /**
     * Do not modify addRestResourceClasses() method.
     * It is automatically populated with
     * all resources defined in the project.
     * If required, comment out calling this method in getClasses().
     */
    private void addRestResourceClasses(Set<Class<?>> resources) {
        resources.add(com.github.mike10004.jenkinsbbhook.DefaultExceptionMapper.class);
        resources.add(com.github.mike10004.jenkinsbbhook.WebhookHandler.class);
    }
    
}
