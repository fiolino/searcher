package org.fiolino.searcher.searcher;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.TermsResponse.Term;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.fiolino.common.analyzing.ModelInconsistencyException;
import org.fiolino.common.util.Instantiator;
import org.fiolino.searcher.*;
import org.fiolino.searcher.NoSuchFieldException;
import org.fiolino.searcher.fieldhandling.FacetType;
import org.fiolino.searcher.result.Result;
import org.fiolino.searcher.result.ResultBuilder;
import org.fiolino.searcher.result.ResultItem;
import org.fiolino.searcher.statement.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by kuli on 08.01.16.
 */
abstract class AbstractSearcher<T> implements Searcher<T> {

  private static final Logger logger = LoggerFactory.getLogger(AbstractSearcher.class);

  protected static final int MAX_ID_QUERY = 100;

  private final TypeConfiguration<T> typeConfiguration;

  private final ResultBuilder<T> resultBuilder;

  public AbstractSearcher(TypeConfiguration<T> typeConfiguration, Instantiator instantiator) {
    this.typeConfiguration = typeConfiguration;
    try {
      resultBuilder = ResultBuilder.createAndAnalyze(typeConfiguration, instantiator);
    } catch (ModelInconsistencyException ex) {
      throw new AssertionError(ex);
    }

    registerFields();
  }

  @Override
  public Class<T> type() {
    return typeConfiguration.type();
  }

  protected TypeConfiguration<T> getTypeConfiguration() {
    return typeConfiguration;
  }

  @Override
  public FacetType<?> getFacetTypeFor(String tagName) {
    return typeConfiguration.getFacetByCategory(tagName);
  }

  protected void registerFields() {
    // Nothing special
  }

  @Override
  public QueryBuilder createQueryBuilder(Realm realm) {
    QueryBuilder queryBuilder = createNewQueryBuilder(realm);
    enhanceQuery(queryBuilder);
    return queryBuilder;
  }

  protected QueryBuilder createNewQueryBuilder(Realm realm) {
    QueryBuilder queryBuilder = new QueryBuilder(typeConfiguration, realm);
    postCreate(queryBuilder);
    return queryBuilder;
  }

  protected void postCreate(QueryBuilder queryBuilder) {
    // Do nothing
  }

  protected void enhanceQuery(QueryBuilder queryBuilder) {
    // Nothing to do
  }

  protected void preExecute(QueryBuilder builder) {
    // Do nothing by default
  }

  public QueryResponse execute(QueryBuilder builder) {
    preExecute(builder);
    SolrQuery q = builder.build();
    logger.info(q.toString());

    Realm realm = builder.getRealm();
    SolrClient solrClient = realm.getSolrClient();
    QueryResponse response;
    try {
      response = sendQueryToSolr(solrClient, q);
    } catch (IOException | SolrServerException ex) {
      throw new RuntimeException("Solr server failed", ex);
    }
    return response;
  }

  protected ResultBuilder<T> getResultBuilder() {
    return resultBuilder;
  }

  @Override
  public Result<T> search(QueryBuilder builder) {
    QueryResponse response = execute(builder);
    Measurement m = builder.getMeasurement();
    m.queryReturned();
    Result<T> result = evaluateResultFrom(builder, response);
    logger.info("Querying " + builder + " with " + result.getHitCount() + " hits; " + m.measureAll(response.getQTime()));
    return result;
  }

  private Result<T> evaluateResultFrom(QueryBuilder builder, QueryResponse response) {
    SolrDocumentList results = response.getResults();
    if (results == null) {
      throw new AssertionError("Response is null for " + type().getName() + " in " + this.getClass().getName());
    }
    int hitCount = (int) results.getNumFound();
    Result<T> result = new Result<>(hitCount, results.size());

    for (SolrDocument doc : results) {
      T bean = newInstance(doc);
      ResultItem<T> item = getResultBuilder().createResultFrom(bean, doc);
      result.addItem(item);
    }

    getResultBuilder().addMetaInformation(builder, result, response);

    return result;
  }

  protected abstract T newInstance(SolrDocument doc);

  protected QueryResponse sendQueryToSolr(SolrClient solrClient, SolrQuery q) throws SolrServerException, IOException {
    return solrClient.query(q);
  }

  @Override
  public List<T> searchByIDs(Realm realm, String filterField, Long... ids) {
    int n = ids.length;
    if (n == 0) {
      return Collections.emptyList();
    }
    if (n <= MAX_ID_QUERY) {
      return searchByIDsLimited(realm, filterField, ids);
    }
    int offset = 0;
    List<T> result = new ArrayList<>(n);
    do {
      int subLength = Math.min(MAX_ID_QUERY, n - offset);
      Long[] subArray = new Long[subLength];
      System.arraycopy(ids, offset, subArray, 0, subLength);
      List<T> subResult = searchByIDsLimited(realm, filterField, subArray);
      result.addAll(subResult);
    } while ((offset += MAX_ID_QUERY) < n);

    return result;
  }

  private List<T> searchByIDsLimited(Realm realm, String filterField, Long... ids) {
    QueryBuilder builder = createNewQueryBuilder(realm);
    try {
      Filter f = builder.getFilter(filterField, (Object[]) ids).uncached();
      builder.apply(f);
    } catch (NoSuchFieldException ex) {
      throw new AssertionError("No filter for id defined!");
    }
    builder.setLimit(Integer.MAX_VALUE);
    Result<T> result = search(builder);
    return result.getItems();
  }

  @Override
  public List<String> getSuggestions(Realm realm, String input) {
    List<String> ret = new ArrayList<>();
    if (input == null) {
      input = "";
    }
    
    input = input.trim().replaceAll("[()\\[\\]]", "");
    String rest = "";

    int index = input.lastIndexOf(" ");
    if (index > 0) {
      rest = input.substring(0, index + 1);
      input = input.substring(index + 1);
    }

    SolrClient solrClient = realm.getSolrClient();
    SolrQuery query = new SolrQuery();
    query.setRequestHandler("/terms");
    String prefix = "";
    if (input.startsWith("#")) {
      input = input.substring(1);
      query.set("terms.fl", "tags");
      prefix = "#";
    }
    if (input.trim().length() > 0) {
      query.set("terms.regex", ".*" + input + ".*");
    }

    try {
      QueryResponse response = solrClient.query(query);
      Map<String, List<Term>> terms = response.getTermsResponse().getTermMap();
      for (String key : terms.keySet()) {
        for (Term term : terms.get(key)) {
          ret.add(rest + prefix + term.getTerm());
        }
      }
    } catch (IOException | SolrServerException ex) {
      throw new RuntimeException("Solr server failed", ex);
    }
    return ret;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + " for " + typeConfiguration.type().getName();
  }
}
