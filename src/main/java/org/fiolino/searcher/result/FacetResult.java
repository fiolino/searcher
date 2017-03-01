package org.fiolino.searcher.result;

import javax.annotation.Nullable;

import org.fiolino.searcher.fieldhandling.SolrType;

import java.util.List;

/**
 * Created by Michael Kuhlmann on 05.01.2016.
 */
public class FacetResult<T> {
  private final SolrType<T> type;
  private final List<SingleFacetItem<T>> items;

  FacetResult(SolrType<T> type, List<SingleFacetItem<T>> items) {
    this.type = type;
    this.items = items;
  }

  /**
   * Gets the original type of this facet.
   */
  public SolrType<T> getSolrType() {
    return type;
  }

  /**
   * Gets all result items for this facet.
   */
  public List<SingleFacetItem<T>> getItems() {
    return items;
  }

  /**
   * Gets the item of a specific value, if there is one, or null otherwise.
   */
  @Nullable public SingleFacetItem<T> getItemByValue(Object value) {
    for (SingleFacetItem<T> i : items) {
      if (value.equals(i.getValue())) {
        return i;
      }
    }

    return null;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(getClass().getSimpleName()).append(" of ").append(getSolrType()).append(": ");
    if (items.isEmpty()) {
      sb.append("<EMPTY>");
    } else {
      boolean first = true;
      for (SingleFacetItem<T> each : items) {
        if (first) {
          first = false;
        } else {
          sb.append(',');
        }
        sb.append(each);
      }
    }

    return sb.toString();
  }
}
