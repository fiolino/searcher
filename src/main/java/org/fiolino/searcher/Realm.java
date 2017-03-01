package org.fiolino.searcher;

import com.google.common.reflect.TypeToken;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.fiolino.common.util.Cached;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Created by kuli on 10.03.16.
 */
public class Realm {

    private static final Logger logger = LoggerFactory.getLogger(Realm.class);

    private static final Type FIELDS_RESULT_TYPE = new TypeToken<Map<String, Map<String, String>>>() {}.getType();

    private final String url;
    private final String core;
    private final SolrClient solrClient;

    private final Cached<List<String>> fieldNames;

    private CloseableHttpClient httpClient;

    Realm(String url, String core) {
        this(url, core, 10);
    }

    Realm(String url, String core, int updateIntervalInMinutes) {
        this.url = url.endsWith("/") ? url : url + "/";
        this.core = core;
        String full = this.url + core;
        this.solrClient = new HttpSolrClient.Builder().withBaseSolrUrl(full).build();
        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(5000).setSocketTimeout(10000).build();
        HttpClientBuilder clientBuilder = HttpClientBuilder.create().setConnectionTimeToLive(10, TimeUnit.SECONDS)
                .setMaxConnTotal(10).setConnectionManagerShared(true).setDefaultRequestConfig(requestConfig);
        httpClient = clientBuilder.build();
        fieldNames = Cached.updateEvery(updateIntervalInMinutes).minutes().with(this::fetchFieldNames);
    }

    public SolrClient getSolrClient() {
        return solrClient;
    }

    public List<String> getFieldNames() {
        return fieldNames.get();
    }

    private List<String> fetchFieldNames() {
        try (CloseableHttpClient httpClient = getHttpClient()) {
            URIBuilder builder = new URIBuilder(url + "/admin/luke");
            builder.addParameter("numTerms", "0");
            builder.addParameter("wt", "json");
            HttpGet httpGet = new HttpGet(builder.build());
            addAdditionalInfo(httpGet);

            int count = 0;
            while (count++ >= 0) {
                try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                    if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                        if (count < 3) {
                            continue;
                        }
                        logger.warn("Cannot retrieve fields for " + url + ": " + response.getStatusLine());
                        return Collections.emptyList();
                    }

                    String content = EntityUtils.toString(response.getEntity(), "UTF-8");

                    Map<String, Map<String, String>> fields = Json.extractFrom(content, "fields", FIELDS_RESULT_TYPE);
                    if (fields == null) {
                        logger.warn("Luke request returned no fields (" + url + "): " + content);
                        if (count < 3) {
                            continue;
                        }
                        return null;
                    }
                    return Collections.unmodifiableList(new ArrayList<>(fields.keySet()));
                } catch (IOException e) {
                    if (count < 3) {
                        continue;
                    }
                    throw e;
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException("Cannot retrieve fields for " + url, ex);
        } catch (URISyntaxException ex) {
            throw new IllegalStateException("Cannot create URI " + url + core, ex);
        }
        return null;
    }

    private boolean checkCoreExists() {
        try (CloseableHttpClient httpClient = getHttpClient()) {
            HttpGet httpGet = createGetWithContentTypeAsJson("/admin/ping");

            CloseableHttpResponse response = httpClient.execute(httpGet);
            return response.getStatusLine().getStatusCode() == HttpStatus.SC_OK;
        } catch (IOException ex) {
            logger.error("Cannot get Core status", ex);
            return false;
        }
    }

    private HttpGet createGetWithContentTypeAsJson(String path) {
        HttpGet httpGet = new HttpGet(url + path);
        addAdditionalInfo(httpGet);
        return httpGet;
    }

    private void addAdditionalInfo(HttpGet httpGet) {
        httpGet.addHeader("content-type", "application/json; charset=UTF-8");
    }

    public void initialize() {
        if (checkCoreExists()) {
            return;
        }
        logger.info("Core " + core + " does not exist yet, creating...");
        try (CloseableHttpClient httpClient = getHttpClient()) {
            URIBuilder builder = new URIBuilder(url + "admin/cores");
            builder.addParameter("action", "CREATE");
            builder.addParameter("name", core);
            builder.addParameter("configSet", "pmm");
            HttpGet httpGet = new HttpGet(builder.build());
            addAdditionalInfo(httpGet);

            int count = 0;
            while (count++ >= 0) {
                try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                    if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                        if (count < 3) {
                            continue;
                        }
                        throw new RuntimeException("Cannot create core " + core);
                    }
                    break;
                } catch (IOException e) {
                    if (count < 3) {
                        continue;
                    }
                    throw e;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot create core " + core, e);
        } catch (URISyntaxException ex) {
            throw new IllegalStateException("Cannot create URI " + url + core, ex);
        }
    }

    private CloseableHttpClient getHttpClient() {
        return httpClient;
    }

    @Override
    public String toString() {
        return url + core;
    }
}
