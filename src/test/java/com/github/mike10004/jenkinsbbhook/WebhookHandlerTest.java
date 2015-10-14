/*
 * (c) 2015 Mike Chaberski, distributed under MIT License
 */
package com.github.mike10004.jenkinsbbhook;

import com.github.mike10004.jenkinsbbhook.WebhookHandler.CrumbData;
import com.google.common.base.Supplier;
import com.google.gson.Gson;
import static javax.servlet.http.HttpServletResponse.SC_ACCEPTED;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.junit.MockServerRule;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockServletContext;

/**
 *
 * @author mchaberski
 */
public class WebhookHandlerTest {
    
    @Rule
    public MockServerRule mockServerRule = new MockServerRule(this);
    
    public WebhookHandlerTest() {
    }

    @Test
    public void testRelayBuildRequest() throws Exception {
        System.out.println("testRelayBuildRequest");
        MockServerClient jenkinsServer = new MockServerClient("localhost", mockServerRule.getHttpPort());
        CrumbData crumbData = new CrumbData("5647382910", ".crumb");
        String crumbDataJson = new Gson().toJson(crumbData);
        String apiToken = "12345";
        String username = "betty@example.com";
        String projectToken = "09876";
        jenkinsServer.when(HttpRequest.request("/crumbIssuer/api/json")
//                .with
        ).respond(HttpResponse.response(crumbDataJson).withStatusCode(SC_OK));
        String jobName = "my-jenkins-project";
        String pushJson = "{}";
        jenkinsServer.when(HttpRequest.request("/job/" + jobName + "/build")
        .withMethod("POST")
                .withHeader(crumbData.crumbRequestField, crumbData.crumb)
                .withQueryStringParameter("token", projectToken))
                .respond(HttpResponse.response().withStatusCode(SC_ACCEPTED));
        MockServletContext servletContext = new MockServletContext();
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        request.addHeader("X-Event-Key", "repo:push");
        
        servletContext.setInitParameter(ContextAppParams.PARAM_JENKINS_BASE_URL, "http://localhost:" + mockServerRule.getHttpPort());
        WebhookHandler instance = new WebhookHandler(new Supplier<CloseableHttpClient>() {
            @Override
            public CloseableHttpClient get() {
                return HttpClients.createDefault();
            }
        }, servletContext);
        
        instance.relayBuildRequest(request, jobName, projectToken, username, apiToken, pushJson);
        
    }
    
}
