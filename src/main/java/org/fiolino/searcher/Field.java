package org.fiolino.searcher;

import java.io.Serializable;

/**
 * Created by kuli on 22.12.16.
 */
public final class Field implements Serializable {

  private static final long serialVersionUID = 2743909893961273314L;

  private final Class<?> type;
  private final String name;
  private final String solrName;
  private final int order;
  private Field next;

  Field(Class<?> type, String name, String solrName, int order) {
    this.name = name;
    this.solrName = solrName;
    this.type = type;
    this.order = order;
  }

  private Field(Field next, Class<?> type, String name, String solrName, int order) {
    this(type, name, solrName, order);
    this.next = next;
  }

  public String getName() {
    return name;
  }

  public String getSolrName() {
    return solrName;
  }

  public Class<?> getType() {
    return type;
  }

  public int getOrder() {
    return order;
  }

  public Field getNext() {
    return next;
  }

  /**
   * Inserts a field into this chain.
   *
   * @param type The type of the field
   * @param name The name under which it is registered (usually the field name itself)
   * @param solrName The solr name
   * @param order The ordering - higher values come first
   * @return The new field chain (different to this field if new field has higher order)
   */
  Field insert(Class<?> type, String name, String solrName, int order) {
    if (this.order < order) {
      // The new one comes first
      return new Field(this, type, name, solrName, order);
    }
    // The new one comes later
    if (next == null) {
      next = new Field(type, name, solrName, order);
    } else {
      next = next.insert(type, name, solrName, order);
    }
    return this;
  }

  @Override
  public String toString() {
    String s = getType().getName() + " " + getName() + " (" + getSolrName() + ")";
    if (getNext() != null) {
      s += "; " + getNext().toString();
    }
    return s;
  }

  @Override
  public boolean equals(Object obj) {
    return obj != null && obj.getClass().equals(getClass())
            && getSolrName().equals(((Field) obj).getSolrName())
            && (getNext() == null && ((Field) obj).getNext() == null) || (getNext() != null && getNext().equals(((Field) obj).getNext()));
  }

  @Override
  public int hashCode() {
    return getSolrName().hashCode() * 31 + (getNext() == null ? 0 : getNext().hashCode());
  }
}
