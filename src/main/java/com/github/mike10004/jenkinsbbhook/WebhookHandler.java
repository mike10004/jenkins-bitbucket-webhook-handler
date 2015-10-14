/*
 * (c) 2015 Mike Chaberski, distributed under MIT License
 */
package com.github.mike10004.jenkinsbbhook;

import com.google.common.annotations.VisibleForTesting;
import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.collect.Iterators;
import com.google.common.io.CharStreams;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.Context;
import javax.ws.rs.Consumes;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.UriBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

/**
 * REST Web Service
 *
 * @author mchaberski
 */
@Path("build")
public class WebhookHandler {

    private static final Logger log = Logger.getLogger(WebhookHandler.class.getName());
    
    @Context
    private ServletContext context;

    private transient final Supplier<? extends CloseableHttpClient> httpClientFactory;
    
    private transient final Gson gson;
    
    /**
     * Creates a new instance of WebhookResource
     */
    public WebhookHandler() {
        this(new SystemHttpClientSupplier(), (ServletContext) null);
    }
    
    @VisibleForTesting
    WebhookHandler(Supplier<? extends CloseableHttpClient> httpClientFactory, @Nullable ServletContext context) {
        this.httpClientFactory = checkNotNull(httpClientFactory);
        this.context = context;
        gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * POST method for handling webhook.
     * @param content representation for the resource
     * @return an HTTP response with content of the updated or created resource.
     */
    @POST
    @Consumes("application/json")
    @Produces("application/json")
    public String relayBuildRequest(@Context HttpServletRequest request, @QueryParam("job") String jobName, 
            @QueryParam("project_token") String projectToken, 
            @QueryParam("username") String username, 
            @QueryParam("api_token") String apiToken, String content) throws IOException {
        if (!"repo:push".equals(request.getHeader("X-Event-Key"))) {
            throw new BadRequestException("unhandled event key header value");
        }
        Push push;
        try {
            push = gson.fromJson(content, Push.class);
        } catch (JsonSyntaxException e) {
            throw new BadRequestException("malformed json in request body", e);
        }
        jobName = Strings.nullToEmpty(jobName);
        projectToken = Strings.nullToEmpty(projectToken);
        username = Strings.nullToEmpty(username);
        apiToken = Strings.nullToEmpty(apiToken);
        log.log(Level.INFO, "jobName={0}; projectToken.length={1}; username={2}, apiToken.length={3}; {4}", new Object[]{jobName, projectToken.length(), username, apiToken.length(), push});
        if (projectToken.isEmpty()) {
            throw new BadRequestException("project_token query parameter absent or empty");
        }
        if (jobName.isEmpty()) {
            throw new BadRequestException("could not derive job name from path segment");
        }
        if (apiToken.isEmpty()) {
            throw new BadRequestException("api_token query parameter absent or empty");
        }
        if (username.isEmpty()) {
            throw new BadRequestException("username query parameter absent or empty");
        }
        AppParams appParams = loadAppParams();
        if (appParams.getJenkinsBaseUrl() == null) {
            throw new InternalServerErrorException("server not configured: init param " + ContextAppParams.PARAM_JENKINS_BASE_URL + " is required");
        }
        PostRequestRelayer relayer = constructRelayer(appParams);
        CrumbData crumbData = relayer.fetchCrumbData(appParams, username, apiToken);
        log.log(Level.FINER, "crumb data: {0}", crumbData);
        URI postedUri = relayer.sendBuildPostRequest(appParams, crumbData, jobName, projectToken);
        ResponseData responseData = new ResponseData(appParams.getJenkinsBaseUrl(), crumbData, jobName, postedUri.toString(), appParams.isSimulation());
        return gson.toJson(responseData);
    }

    public static interface PostRequestRelayer {
        CrumbData fetchCrumbData(AppParams appParams, String username, String apiToken) throws IOException;
        URI sendBuildPostRequest(AppParams appParams, CrumbData crumbData, String jobName, String token) throws IOException;
    }
    
    protected PostRequestRelayer constructRelayer(AppParams appParams) {
        if (appParams.isSimulation()) {
            return new PostRequestRelayer() {

                @Override
                public CrumbData fetchCrumbData(AppParams appParams, String username, String apiToken) throws IOException {
                    CrumbData crumbData = new CrumbData(UUID.randomUUID().toString(), ".crumb");
                    return crumbData;
                }

                @Override
                public URI sendBuildPostRequest(AppParams appParams, CrumbData crumbData, String jobName, String token) throws IOException {
                    return buildJenkinsBuildUri(appParams, jobName, token);
                }
            };
        } else {
            return new DefaultPostRequestRelayer();
        }
    }
    
    public static class ResponseData {
        public String jenkinsBaseUrl;
        public CrumbData crumbData;
        public String jobName;
        public String jobUrl;
        public Boolean simulation;
        
        public ResponseData() {
        }

        public ResponseData(String jenkinsBaseUrl, CrumbData crumbData, String jobName, String jobUrl, boolean simulation) {
            this.jenkinsBaseUrl = jenkinsBaseUrl;
            this.crumbData = crumbData;
            this.jobName = jobName;
            this.jobUrl = jobUrl;
            this.simulation = simulation;
        }
        
    }
    
    protected AppParams loadAppParams() {
        log.log(Level.FINER, "loading app params; defined: {0}", Iterators.toString(Iterators.forEnumeration(context.getInitParameterNames())));
        return new ContextAppParams(new ServletInitParamValueProvider(context));
    }

    protected URI buildJenkinsBuildUri(AppParams appParams, String jobName, String projectToken) {
        return UriBuilder.fromUri(appParams.getJenkinsBaseUrl())
                    .path("job/{jobName}/build")
                    .queryParam("token", projectToken).build(jobName);
    }
    
    protected static int getPortOrDefault(URL url) {
        int port = url.getPort();
        if (port == -1) {
            port = url.getDefaultPort();
        }
        return port;
    }
    
    public class DefaultPostRequestRelayer implements PostRequestRelayer {
    
        @Override
        public CrumbData fetchCrumbData(AppParams appParams, String username, String apiToken) throws IOException {
            String jenkinsBaseUrl = appParams.getJenkinsBaseUrl();
            URI crumbIssuerUri = UriBuilder.fromUri(jenkinsBaseUrl)
                    .path("crumbIssuer/api/json")
                    .build();
            URL crumbIssuerUrl = crumbIssuerUri.toURL();
            HttpHost crumbHost = new HttpHost(crumbIssuerUrl.getHost(), getPortOrDefault(crumbIssuerUrl), crumbIssuerUrl.getProtocol());
            CredentialsProvider credentialsProvider = asCredentialsProvider(crumbIssuerUri.toURL(), appParams, username, apiToken);
            CloseableHttpClient client = httpClientFactory.get();
            RequestConfig requestConfig = RequestConfig.custom()
                    .setAuthenticationEnabled(true)
                    .build();
            AuthCache authCache = new BasicAuthCache();
            BasicScheme basicAuth = new BasicScheme();
            authCache.put(crumbHost, basicAuth);
            HttpClientContext clientContext = HttpClientContext.create();
            clientContext.setRequestConfig(requestConfig);
            clientContext.setCredentialsProvider(credentialsProvider);
            clientContext.setAuthCache(authCache);
            HttpGet request = new HttpGet(crumbIssuerUri);
            String crumbJson;
            CrumbData crumbData;
            try (CloseableHttpResponse response = client.execute(request, clientContext);
                  Reader reader = newReader(response.getEntity())) {
                crumbJson = CharStreams.toString(reader);
            }
            try {
                crumbData = gson.fromJson(crumbJson, CrumbData.class);
            } catch (JsonSyntaxException e) {
                log.log(Level.INFO, "response from crumb issuer is malformed json: {0}", StringUtils.abbreviate(crumbJson, 512));
                throw e;
            }
            if (crumbData == null) {
                throw new InternalServerErrorException("crumb not present in entity returned from " + crumbIssuerUri);
            }
            return crumbData;
        }

        @Override
        public URI sendBuildPostRequest(AppParams appParams, CrumbData crumbData, String jobName, String token) throws IOException {
            URI jenkinsBuildUri = buildJenkinsBuildUri(appParams, jobName, token);
            log.log(Level.FINER, "jenkins build uri = {0}", jenkinsBuildUri);
            CloseableHttpClient client = httpClientFactory.get();
            HttpPost request = new HttpPost(jenkinsBuildUri);
            request.setHeader(crumbData.crumbRequestField, crumbData.crumb);
            try (CloseableHttpResponse response = client.execute(request)) {
                int status = response.getStatusLine().getStatusCode();
                log.log(Level.INFO, "response from jenkins (base={0}) is {1}", new Object[]{appParams.getJenkinsBaseUrl(), response.getStatusLine()});
                if (!isSuccessStatus(status)) {
                    throw new InternalServerErrorException("response to POST request to jenkins project build URL has status code " + status);
                }
            }
            return jenkinsBuildUri;
        }
    }
    
    protected boolean isSuccessStatus(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }
    
    protected Reader newReader(HttpEntity entity) throws IOException {
        return new InputStreamReader(entity.getContent());
    }
    
    protected CredentialsProvider asCredentialsProvider(URL jenkinsCrumbUrl, AppParams appParams, String username, String apiToken) {
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        String jenkinsHost = jenkinsCrumbUrl.getHost();
        int jenkinsPort = jenkinsCrumbUrl.getPort();
        if (jenkinsPort == -1) {
            jenkinsPort = jenkinsCrumbUrl.getDefaultPort();
        }
        AuthScope scope = new AuthScope(jenkinsHost, jenkinsPort);
        credentialsProvider.setCredentials(scope, new UsernamePasswordCredentials(username, apiToken));
        return credentialsProvider;
    }
    
    public static class CrumbData {
        
        public String crumb;
        public String crumbRequestField;

        public CrumbData(String crumb, String crumbRequestField) {
            this.crumb = crumb;
            this.crumbRequestField = crumbRequestField;
        }

        public CrumbData() {
        }

        @Override
        public String toString() {
            return "CrumbData{" + "crumb=" + crumb + ", crumbRequestField=" + crumbRequestField + '}';
        }
        
    }

    private static class SystemHttpClientSupplier implements Supplier<CloseableHttpClient> {

        public SystemHttpClientSupplier() {
        }

        @Override
        public CloseableHttpClient get() {
            return HttpClients.createSystem();
        }
    }
    
}
