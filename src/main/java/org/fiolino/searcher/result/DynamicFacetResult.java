package org.fiolino.searcher.result;

import org.fiolino.searcher.fieldhandling.DynamicFacetType;

import java.util.List;

/**
 * Created by kuli on 08.02.16.
 */
public class DynamicFacetResult<T> extends FacetResult<T> {
  private final String name;
  private final String solrFieldName;
  private final String group;

  DynamicFacetResult(DynamicFacetType<T> type, String solrFieldName, String name, List<SingleFacetItem<T>> singleFacetItems) {
    super(type, singleFacetItems);
    this.solrFieldName = solrFieldName;
    this.name = name;
    group = type.getTagName();
  }

  public String getName() {
    return name;
  }

  public String getGroup() {
    return group;
  }

  public String getSolrFieldName() {
    return solrFieldName;
  }
}
