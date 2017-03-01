package org.fiolino.searcher.fieldhandling;

import org.fiolino.searcher.statement.DirectFilter;

/**
 * Created by kuli on 23.03.15.
 */
public class FilterType<T> extends SolrType<T> {

  private final String solrFieldName;

  /**
   * Which name to use for excluding filters.
   */
  private final String tagName;

  public FilterType(String solrFieldName, String tagName, Class<T> type, String... categories) {
    super(type, categories);
    this.solrFieldName = solrFieldName;
    this.tagName = tagName;
  }

  public String getSolrFieldName() {
    return solrFieldName;
  }

  public String getTagName() {
    return tagName;
  }

  public DirectFilter make(Object... values) {
    return new DirectFilter(getSolrFieldName(), getTagName(), values);
  }
}
