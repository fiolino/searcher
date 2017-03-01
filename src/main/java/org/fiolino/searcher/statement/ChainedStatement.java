package org.fiolino.searcher.statement;

import org.apache.solr.client.solrj.SolrQuery;

/**
 * Created by kuli on 10.03.16.
 */
abstract class ChainedStatement extends Statement {
  final Statement next;

  ChainedStatement(Statement next) {
    this.next = next;
  }

  @Override
  protected void applyTo(StringBuilder sb) {
    next.applyTo(sb);
  }

  @Override
  protected void addToQuery(SolrQuery solrQuery, String query) {
    next.addToQuery(solrQuery, query);
  }

  @Override
  protected void applyLocalParamsTo(ParamContainer container) {
    super.applyLocalParamsTo(container);
    next.applyLocalParamsTo(container);
  }
}
