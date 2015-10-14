/*
 * (c) 2015 Mike Chaberski, distributed under MIT License
 */
package com.github.mike10004.jenkinsbbhook;

import com.google.common.net.MediaType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 *
 * @author mchaberski
 */
@Provider
public class DefaultExceptionMapper implements ExceptionMapper<Throwable> {

    private transient final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    @Override
    public Response toResponse(Throwable exception) {
        if (exception instanceof WebApplicationException) {
            return ((WebApplicationException)exception).getResponse();
        }
        ExceptionInfo info = new ExceptionInfo(exception);
        if (exception.getCause() != null) {
            info.cause = new ExceptionInfo(exception.getCause());
        }
        String json = gson.toJson(info);
        Response response = Response.serverError()
                .entity(json)
                .type(MediaType.JSON_UTF_8.withoutParameters().toString())
                .build();
        return response;
    }
    
    public static class ExceptionInfo {
        
        public String type;
        public String message;
        public ExceptionInfo cause;
        
        public ExceptionInfo(String type, String message) {
            this.type = type;
            this.message = message;
        }

        public ExceptionInfo() {
        }

        public ExceptionInfo(Throwable t) {
            this(t.getClass().getName(), t.getMessage());
        }
        
    }
}
