package org.fiolino.searcher.fieldhandling;

import java.util.Arrays;

/**
 * Created by kuli on 23.03.15.
 */
public abstract class SolrType<T> {
  private final String[] categories;
  private final Class<T> type;

  SolrType(Class<T> type, String... categories) {
    Arrays.sort(categories);
    this.categories = categories;
    this.type = type;
  }

  public String[] getCategories() {
    return categories;
  }

  public boolean contains(String category) {
    for (String cat : categories) {
      if (cat.equals(category)) {
        return true;
      }
    }
    return false;
  }

  public Class<T> getType() {
    return type;
  }

  public T get(String serialization) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this
            || obj != null && obj.getClass().equals(getClass()) && Arrays.equals(((SolrType<?>) obj).categories, categories);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(categories) + getClass().hashCode();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + " '" + Arrays.toString(categories) + "' <" + type + ">";
  }

}
