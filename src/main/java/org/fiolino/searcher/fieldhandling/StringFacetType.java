package org.fiolino.searcher.fieldhandling;

import org.fiolino.data.annotation.Hint;

/**
 * Created by Michael Kuhlmann on 25.01.2016.
 */
public class StringFacetType extends FacetType<String> {
  public StringFacetType(String solrFieldName, String tagName, Hint hint, String... categories) {
    super(String.class, solrFieldName, tagName, hint, categories);
  }

  @Override
  public String get(String serialization) {
    return serialization;
  }
}
