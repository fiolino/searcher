package org.fiolino.searcher.statement;

import org.apache.solr.client.solrj.SolrQuery;

/**
 * Created by kuli on 29.02.16.
 */
class BooleanFilter extends Filter {
  private final Filter first, second;
  private final Operator operator;

  BooleanFilter(Filter first, Filter second, Operator operator) {
    this.first = first;
    this.second = second;
    this.operator = operator;
  }

  @Override
  protected void applyTo(StringBuilder sb, boolean negated, boolean allowsNull) {
    first.applyTo(sb, negated, allowsNull);
    sb.append(' ').append(operator.value(negated)).append(' ');
    second.applyTo(sb, negated, allowsNull);
  }

  @Override
  protected void addToQuery(SolrQuery solrQuery, String query) {
    first.addToQuery(solrQuery, query);
  }
}
