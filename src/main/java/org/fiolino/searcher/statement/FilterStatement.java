package org.fiolino.searcher.statement;

import org.apache.solr.client.solrj.SolrQuery;

/**
 * A {@link FilterStatement} is a Statement which will be added to a fq field.
 *
 * Created by kuli on 11.01.17.
 */
public class FilterStatement extends Statement {
  private final String value;

  public FilterStatement(String value) {
    this.value = value;
  }

  @Override
  protected void addToQuery(SolrQuery solrQuery, String query) {
    solrQuery.addFilterQuery(query);
  }

  @Override
  protected void applyTo(StringBuilder sb) {
    sb.append(value);
  }
}
