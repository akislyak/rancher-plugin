package jenkins.plugins.rancher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.charset.Charset;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public abstract class HttpClient {

    private final String accesskey;
    private final String secretKey;
    private final String endpoint;

    public HttpClient(String endpoint, String accesskey, String secretKey) {
        this.accesskey = accesskey;
        this.secretKey = secretKey;
        this.endpoint = endpoint;
    }

    protected <T> T get(String url, Class<T> responseClass) {
        HttpGet getMethod = new HttpGet(endpoint + url);
        return execute(getMethod, responseClass);
    }

    protected <T> T delete(String url, Class<T> responseClass) {
        HttpDelete httpDelete = new HttpDelete(endpoint + url);
        return execute(httpDelete, responseClass);
    }

    protected <T> T post(String url, Object data, Class<T> responseClass) throws IOException {
        HttpPost method = new HttpPost(endpoint + url);
        method.setEntity(getRequestBody(data));
        return this.execute(method, responseClass);
    }

    protected <T> T post(String url, Class<T> responseClass) {
        HttpPost method = new HttpPost(endpoint + url);
        return this.execute(method, responseClass);
    }

    protected <T> T put(String url, Object data, Class<T> responseClass) throws IOException {
        HttpPut method = new HttpPut(endpoint + url);
        method.setEntity(getRequestBody(data));
        return this.execute(method, responseClass);
    }

    private <T> T execute(HttpRequestBase method, Class<T> responseClass) {
        try (CloseableHttpClient httpclient = HttpClients.custom()
                .setRetryHandler(new DefaultHttpRequestRetryHandler(3, false)).build())  {

            method.addHeader("Authorization", getAuthorization());
            ResponseHandler<String> responseHandler = response -> {
            int status = response.getStatusLine().getStatusCode();

                if (status != HttpStatus.SC_OK && status != HttpStatus.SC_ACCEPTED && status != HttpStatus.SC_CREATED) {
                    throw new RuntimeException(String.format("Some Error Happen statusCode %d", status));
                }
                HttpEntity entity = response.getEntity();
                return entity != null ? EntityUtils.toString(entity) : null;
            };

            String responseBody = httpclient.execute(method, responseHandler);
            return getObjectMapper().readValue(responseBody, responseClass);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Connection to Rancher Failed Please check deploy configuration");
        } finally {
            method.releaseConnection();
        }
    }

    private HttpEntity getRequestBody(Object stack) throws JsonProcessingException {
        String requestBody = getObjectMapper().writeValueAsString(stack);
        return new StringEntity(requestBody, ContentType.APPLICATION_JSON);
    }

    private String getAuthorization() {
        byte[] encodedAuth = Base64.encodeBase64((accesskey + ":" + secretKey).getBytes(Charset.forName("US-ASCII")));
        return "Basic " + new String(encodedAuth);
    }

    private ObjectMapper getObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        objectMapper.configure(SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS, false);
        return objectMapper;
    }
}
