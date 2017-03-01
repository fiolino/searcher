package org.fiolino.searcher.fieldhandling;

import org.fiolino.data.annotation.Hint;

/**
 * Created by Michael Kuhlmann on 25.01.2016.
 */
public abstract class FacetType<T> extends FilterType<T> {
  /**
   * Specifies whether many or few values are being expected.
   */
  private final Hint hint;

  public FacetType(Class<T> type, String solrFieldName, String tagName, Hint hint, String... categories) {
    super(solrFieldName, tagName, type, categories);
    this.hint = hint;
  }

  @Override
  public abstract T get(String serialization);

  public Hint getHint() {
    return hint;
  }
}
