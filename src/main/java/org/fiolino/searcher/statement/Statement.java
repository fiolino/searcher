package org.fiolino.searcher.statement;

import org.apache.solr.client.solrj.SolrQuery;

/**
 * Created by kuli on 29.12.16.
 */
public abstract class Statement {
  public final void apply(SolrQuery solrQuery) {
    StringBuilder sb = createStringBuilder();
    applyTo(sb);
    addToQuery(solrQuery, sb.toString());
  }

  protected abstract void addToQuery(SolrQuery solrQuery, String query);

  protected abstract void applyTo(StringBuilder sb);

  private StringBuilder createStringBuilder() {
    ParamContainer container = new ParamContainer();
    applyLocalParamsTo(container);
    return container.get();
  }

  protected void applyLocalParamsTo(ParamContainer container) {
    // Default: No params
  }
}
