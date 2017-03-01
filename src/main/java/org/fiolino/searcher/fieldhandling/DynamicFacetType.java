package org.fiolino.searcher.fieldhandling;

import org.fiolino.common.util.Strings;
import org.fiolino.data.annotation.Hint;
import org.fiolino.searcher.statement.DirectFilter;

import java.lang.invoke.MethodHandle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by kuli on 08.02.16.
 */
public class DynamicFacetType<T> extends DeserializingFacetType<T> {
  private final Pattern wildcard;
  private final String solrFilterName;

  public DynamicFacetType(MethodHandle converter, Class<T> valueType, String solrFieldName,
                          Hint hint, String... categories) {
    this(converter, valueType, solrFieldName, solrFieldName, hint, categories);
  }

  public DynamicFacetType(MethodHandle converter, Class<T> valueType, String solrFieldName,
                          String solrFilterName, Hint hint, String... categories) {
    super(converter, valueType, solrFieldName, createTagName(categories), hint, enhanceCategories(categories));
    this.solrFilterName = solrFilterName;
    int wildcardPos = solrFieldName.indexOf('*');
    if (wildcardPos < 0 || solrFieldName.indexOf('*', wildcardPos + 1) > 0) {
      throw new IllegalArgumentException("SolrFieldName " + solrFieldName + " must contain exactly one wildcard!");
    }
    wildcard = Pattern.compile("^" + solrFieldName.replace("*", "(.*)") + "$");
  }

  private static String[] enhanceCategories(String[] cats) {
    for (int i=0; i<cats.length; i++) {
      cats[i] = removeWildcard(cats[i]);
    }
    return cats;
  }

  private static String createTagName(String[] categories) {
    if (categories.length == 0) {
      return null;
    }
    String tagName = Strings.normalizeName(Strings.combinationOf(categories));
    return removeWildcard(tagName);
  }

  private static String removeWildcard(String tagName) {
    tagName = tagName.replaceAll("_\\*", "");
    tagName = tagName.replaceAll("\\*_", "");
    return tagName.replaceAll("\\*", "");
  }

  public String getSolrFieldNameFor(String name) {
    String fieldName = getSolrFieldName();
    return getSolrFieldNameFor(fieldName, name);
  }

  private String getSolrFieldNameFor(String fieldName, String valueName) {
    int asterisk = fieldName.indexOf('*');
    return fieldName.substring(0, asterisk) + valueName + fieldName.substring(asterisk + 1);
  }

  /**
   * Extracts the name part of the field.
   * Returns null if this facet is not valid for the solr field.
   */
  public String extractName(String fieldName) {
    Matcher m = wildcard.matcher(fieldName);
    if (m.find()) {
      return m.group(1);
    }
    return null;
  }

  public DirectFilter makeForName(String name, Object... values) {
    String solrFieldName = getSolrFieldNameFor(solrFilterName, name);
    return new DirectFilter(solrFieldName, getTagName() + "_" + name, values);
  }
}
