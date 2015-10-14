/*
 * (c) 2015 Mike Chaberski, distributed under MIT License
 */
package com.github.mike10004.jenkinsbbhook;

/**
 *
 * @author mchaberski
 */
public interface AppParams {

    String getJenkinsBaseUrl();
    boolean isSimulation();
    
}
