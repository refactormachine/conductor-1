/**
 * Copyright 2016 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 *
 */
package com.netflix.conductor.client.http;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.netflix.conductor.client.exceptions.ConductorClientException;
import com.netflix.conductor.client.exceptions.ErrorResponse;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandler;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource.Builder;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.function.Function;

/**
 * Abstract client for the REST template
 */
public abstract class ClientBase {

    private static Logger logger = LoggerFactory.getLogger(ClientBase.class);

    protected final Client client;

    protected String root = "";

    protected ObjectMapper objectMapper;

    protected ClientBase() {
        this(new DefaultClientConfig(), null);
    }

    protected ClientBase(ClientConfig clientConfig) {
        this(clientConfig, null);
    }

    protected ClientBase(ClientConfig clientConfig, ClientHandler handler) {
        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        objectMapper.setSerializationInclusion(Include.NON_NULL);
        objectMapper.setSerializationInclusion(Include.NON_EMPTY);

        JacksonJsonProvider provider = new JacksonJsonProvider(objectMapper);
        clientConfig.getSingletons().add(provider);

        if (handler == null) {
            this.client = Client.create(clientConfig);
        } else {
            this.client = new Client(handler, clientConfig);
        }
    }

    public void setRootURI(String root) {
        this.root = root;
    }

    protected void delete(String url, Object... uriVariables) {
        delete(null, url, uriVariables);
    }

    protected void delete(Object[] queryParams, String url, Object... uriVariables) {
        URI uri = null;
        ClientResponse clientResponse = null;
        try {
            uri = getURIBuilder(root + url, queryParams).build(uriVariables);
            client.resource(uri).delete();
        } catch (RuntimeException e) {
            handleException(uri, e);
        }
    }

    protected void put(String url, Object[] queryParams, Object request, Object... uriVariables) {
        URI uri = null;
        try {
            uri = getURIBuilder(root + url, queryParams).build(uriVariables);
            getWebResourceBuilder(uri, request).put();
        } catch (RuntimeException e) {
            handleException(uri, e);
        }
    }

    /**
     * @deprecated replaced by {@link #postForEntityWithRequestOnly(String, Object)} ()}
     */
    @Deprecated
    protected void postForEntity(String url, Object request) {
        postForEntityWithRequestOnly(url, request);
    }


    protected void postForEntityWithRequestOnly(String url, Object request) {
        Class<?> type = null;
        postForEntity(url, request, null, type);
    }

    /**
     * @deprecated replaced by {@link #postForEntityWithUriVariablesOnly(String, Object...)} ()}
     */
    @Deprecated
    protected void postForEntity1(String url, Object... uriVariables) {
        postForEntityWithUriVariablesOnly(url, uriVariables);
    }

    protected void postForEntityWithUriVariablesOnly(String url, Object... uriVariables) {
        Class<?> type = null;
        postForEntity(url, null, null, type, uriVariables);
    }


    protected <T> T postForEntity(String url, Object request, Object[] queryParams, Class<T> responseType, Object... uriVariables) {
        return postForEntity(url, request, queryParams, responseType, builder -> builder.post(responseType), uriVariables);
    }

    protected <T> T postForEntity(String url, Object request, Object[] queryParams, GenericType<T> responseType, Object... uriVariables) {
        return postForEntity(url, request, queryParams, responseType, builder -> builder.post(responseType), uriVariables);
    }

    private <T> T postForEntity(String url, Object request, Object[] queryParams, Object responseType, Function<Builder, T> postWithEntity, Object... uriVariables) {
        URI uri = null;
        try {
            uri = getURIBuilder(root + url, queryParams).build(uriVariables);
            Builder webResourceBuilder = getWebResourceBuilder(uri, request);
            if (responseType == null) {
                webResourceBuilder.post();
                return null;
            }
            return postWithEntity.apply(webResourceBuilder);
        } catch (UniformInterfaceException e) {
            handleUniformInterfaceException(e, uri);
        } catch (RuntimeException e) {
            handleRuntimeException(e, uri);
        }
        return null;
    }

    protected <T> T getForEntity(String url, Object[] queryParams, Class<T> responseType, Object... uriVariables) {
        return getForEntity(url, queryParams, response -> response.getEntity(responseType), uriVariables);
    }

    protected <T> T getForEntity(String url, Object[] queryParams, GenericType<T> responseType, Object... uriVariables) {
        return getForEntity(url, queryParams, response -> response.getEntity(responseType), uriVariables);
    }

    private <T> T getForEntity(String url, Object[] queryParams, Function<ClientResponse, T> entityPvoider, Object... uriVariables) {
        URI uri = null;
        ClientResponse clientResponse = null;
        try {
            uri = getURIBuilder(root + url, queryParams).build(uriVariables);
            clientResponse = client.resource(uri)
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN)
                    .get(ClientResponse.class);
            if (clientResponse.getStatus() < 300) {
                return entityPvoider.apply(clientResponse);
            } else {
                throw new UniformInterfaceException(clientResponse); // let handleUniformInterfaceException to handle unexpected response consistently
            }
        } catch (UniformInterfaceException e) {
            handleUniformInterfaceException(e, uri);
        } catch (RuntimeException e) {
            handleRuntimeException(e, uri);
        }
        return null;
    }

    private Builder getWebResourceBuilder(URI URI, Object entity) {
        return client.resource(URI).type(MediaType.APPLICATION_JSON).entity(entity).accept(MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON);
    }

    private void handleClientHandlerException(ClientHandlerException exception, URI uri){
        String errorMessage = String.format("Unable to invoke Conductor API with uri: %s, failure to process request or response", uri);
        logger.error(errorMessage, exception);
        throw new ConductorClientException(errorMessage, exception);
    }

    private void handleRuntimeException(RuntimeException exception, URI uri) {
        String errorMessage = String.format("Unable to invoke Conductor API with uri: %s, runtime exception occurred", uri);
        logger.error(errorMessage, exception);
        throw new ConductorClientException(errorMessage, exception);
    }

    private void handleUniformInterfaceException(UniformInterfaceException exception, URI uri) {
        ClientResponse clientResponse = exception.getResponse();
        if (clientResponse == null) {
            throw new ConductorClientException(String.format("Unable to invoke Conductor API with uri: %s", uri));
        }
        try {
            if (clientResponse.getStatus() < 300) {
                return;
            }
            String errorMessage = clientResponse.getEntity(String.class);
            logger.error("Unable to invoke Conductor API with uri: {}, unexpected response from server: {}", uri, clientResponseToString(exception.getResponse()), exception);
            ErrorResponse errorResponse = null;
            try {
                errorResponse = objectMapper.readValue(errorMessage, ErrorResponse.class);
            } catch (IOException e) {
                throw new ConductorClientException(clientResponse.getStatus(), errorMessage);
            }
            throw new ConductorClientException(clientResponse.getStatus(), errorResponse);
        } catch (ConductorClientException e) {
            throw e;
        } catch (ClientHandlerException e) {
            handleClientHandlerException(e, uri);
        } catch (RuntimeException e) {
            handleRuntimeException(e, uri);
        } finally {
            clientResponse.close();
        }
    }

    private void handleException(URI uri, RuntimeException e) {
        if (e instanceof UniformInterfaceException) {
            handleUniformInterfaceException(((UniformInterfaceException) e), uri);
        } else if (e instanceof ClientHandlerException) {
            handleClientHandlerException((ClientHandlerException)e, uri);
        } else {
            handleRuntimeException(e, uri);
        }
    }

    /**
     * Converts ClientResponse object to string with detailed debug information including status code, media type,
     * response headers, and response body if exists.
     */
    private String clientResponseToString(ClientResponse response) {
        if (response == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("[status: ").append(response.getStatus());
        builder.append(", media type: ").append(response.getType());
        if (response.getStatus() != 404) {
            try {
                String responseBody = response.getEntity(String.class);
                if (responseBody != null) {
                    builder.append(", response body: ").append(responseBody);
                }
            } catch (RuntimeException ignore) {
                // Ignore if there is no response body, or IO error - it may have already been read in certain scenario.
            }
        }
        builder.append(", response headers: ").append(response.getHeaders());
        builder.append("]");
        return builder.toString();
    }

    private UriBuilder getURIBuilder(String path, Object[] queryParams) {
        if (path == null) {
            path = "";
        }
        UriBuilder builder = UriBuilder.fromPath(path);
        if (queryParams != null) {
            for (int i = 0; i < queryParams.length; i += 2) {
                String param = queryParams[i].toString();
                Object value = queryParams[i + 1];
                if (value != null) {
                    if (value instanceof Collection) {
                        Object[] values = ((Collection<?>) value).toArray();
                        builder.queryParam(param, values);
                    } else {
                        builder.queryParam(param, value);
                    }
                }
            }
        }
        return builder;
    }

}
