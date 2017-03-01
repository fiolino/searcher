package org.fiolino.searcher.statement;

import org.apache.solr.client.solrj.SolrQuery;

/**
 * Created by kuli on 10.03.16.
 */
abstract class ChainedFilter extends Filter {
  final Filter next;

  ChainedFilter(Filter next) {
    this.next = next;
  }

  @Override
  protected void applyTo(StringBuilder sb, boolean negated, boolean allowsNull) {
    next.applyTo(sb, negated, allowsNull);
  }

  @Override
  protected void addToQuery(SolrQuery solrQuery, String query) {
    next.addToQuery(solrQuery, query);
  }
}
