package org.fiolino.searcher;

import org.apache.solr.client.solrj.SolrQuery;
import org.fiolino.data.annotation.SortDirection;

import java.util.EnumMap;
import java.util.Map;

/**
 * Created by kuli on 16.02.16.
 */
public final class SortField {
  private static final Map<SortDirection, SolrQuery.ORDER> orderMapping;

  static {
    orderMapping = new EnumMap<>(SortDirection.class);
    orderMapping.put(SortDirection.ASC, SolrQuery.ORDER.asc);
    orderMapping.put(SortDirection.DESC, SolrQuery.ORDER.desc);
  }

  private final String solrFieldName;
  private final SortDirection direction;
  private final int order;

  private SortField next;

  SortField(String solrFieldName, SortDirection direction) {
    this(solrFieldName, direction, 1);
  }

  SortField(String solrFieldName, SortDirection direction, int order) {
    this.solrFieldName = solrFieldName;
    this.direction = direction;
    this.order = order;
  }

  String getSolrFieldName() {
    return solrFieldName;
  }

  SortDirection getDirection() {
    return direction;
  }

  SortField insert(String solrFieldName, SortDirection direction, int order) {
    return insert(new SortField(solrFieldName, direction, order));
  }

  private SortField insert(SortField other) {
    if (other.order == this.order) {
      throw new IllegalStateException("Solr field " + other.solrFieldName + " clashes with " + this.solrFieldName + " at the same position #" + order);
    }

    if (other.order < this.order) {
      other.next = this;
      return other;
    }
    if (next == null) {
      next = other;
    } else {
      next = next.insert(other);
    }
    return this;
  }

  public void apply(SolrQuery query) {
    query.setSort(solrFieldName, orderMapping.get(direction));
    addNext(query);
  }

  protected void addNext(SolrQuery query) {
    if (next != null) {
      next.addTo(query);
    }
  }

  public void addTo(SolrQuery query) {
    query.addSort(solrFieldName, orderMapping.get(direction));
    addNext(query);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    print(sb);
    return sb.toString();
  }

  private void print(StringBuilder sb) {
    sb.append(solrFieldName).append(' ').append(direction.toString());
    if (next != null) {
      next.print(sb.append(','));
    }
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this || obj != null
            && obj.getClass().equals(getClass()) && solrFieldName.equals(((SortField) obj).solrFieldName)
            && direction == ((SortField) obj).direction
            && (next == null && ((SortField) obj).next == null || next != null && next.equals(((SortField) obj).next));
  }

  @Override
  public int hashCode() {
    return solrFieldName.hashCode() + direction.hashCode() * 31 +
            (next == null ? 0 : next.hashCode() * 31 * 31);
  }
}
