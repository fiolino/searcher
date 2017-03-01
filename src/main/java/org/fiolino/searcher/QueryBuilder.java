package org.fiolino.searcher;

import org.apache.solr.client.solrj.SolrQuery;
import org.fiolino.common.util.Encoder;
import org.fiolino.data.annotation.Hint;
import org.fiolino.data.annotation.Sorts;
import org.fiolino.data.annotation.Type;
import org.fiolino.data.annotation.ValueRange;
import org.fiolino.searcher.fieldhandling.DynamicFacetType;
import org.fiolino.searcher.fieldhandling.FacetType;
import org.fiolino.searcher.fieldhandling.FilterType;
import org.fiolino.searcher.result.Result;
import org.fiolino.searcher.statement.DirectFilter;
import org.fiolino.searcher.statement.Filter;
import org.fiolino.searcher.statement.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by kuli on 20.03.15.
 */
public class QueryBuilder {

  private static final Logger logger = LoggerFactory.getLogger(QueryBuilder.class.getName());

  private static final int DEFAULT_SLOP = 10;

  private static final int HIGHLIGHT_FRAGSIZE = 120;

  public static final int HIGHLIGHT_SNIPPETS = 3;

  private static final int MAX_COLLATION_TRIES = 10;

  private static final String DEFAULT_MM_VALUE = "1";

  private static final String DEFAULT_FULLTEXT_DEFTYPE = "edismax";
  private static final String MULTI_WORD_FULLTEXT_DEFTYPE = "synonym_edismax";

  private static final Pattern FIND_KEYWORD = Pattern.compile("(\\$\\w+)");

  private static final Pattern WEIGHT_MATCH = Pattern.compile("(\\^\\d+)?\\s+");

  private static final Map<ValueRange, String> rangeToFacetTypeMapping;
  private static final Pattern TAG_PATTERN = Pattern.compile("(#([^#() ]*))");

  static {
    rangeToFacetTypeMapping = new EnumMap<>(ValueRange.class);
    rangeToFacetTypeMapping.put(ValueRange.LIMITED, "enum");
    rangeToFacetTypeMapping.put(ValueRange.LARGE, "fc");
  }

  private final SolrQuery solrQuery;

  private final Set<String> filteredTags = new HashSet<>();

  private final Set<Facet> assignedFacets = new HashSet<>();

  private final Realm realm;

  private final TypeConfiguration<?> typeConfiguration;

  private boolean doHighlighting = true;

  private String sorting;

  private final Measurement measurement = new Measurement();

  public QueryBuilder(TypeConfiguration<?> typeConfiguration, Realm realm) {
    this.typeConfiguration = typeConfiguration;
    this.realm = realm;

    solrQuery = new SolrQuery();
    assignDefaults();
  }

  public final SolrQuery getSolrQuery() {
    return solrQuery;
  }

  public Realm getRealm() {
    return realm;
  }

  private void assignDefaults() {
    for (Map.Entry<String, Map<String, Object>> e : typeConfiguration.getFields().entrySet()) {
      String f = e.getKey();
      for (Map.Entry<String, Object> paramEntry : e.getValue().entrySet()) {
        String parameter = paramEntry.getKey();
        solrQuery.add("f." + f + "." + parameter, String.valueOf(paramEntry.getValue()));
      }
    }
    solrQuery.addField("*");
    solrQuery.setFacetMinCount(1);
    solrQuery.setQuery("*:*");
    setSlop(DEFAULT_SLOP);

    setSorting(Sorts.RELEVANCE);
  }

  public Integer getLimit() {
    return solrQuery.getRows();
  }

  public void setLimit(int limit) {
    solrQuery.setRows(limit);
  }

  public void setOffset(int offset) {
    solrQuery.setStart(offset);
  }

  public void setDoHighlight(boolean highlight) {
    this.doHighlighting = highlight;
  }

  public boolean isDoHighlighting() {
    return doHighlighting;
  }

  /**
   * Gets a filter where the categoryName is the symbolic name of the registered filter,
   * and the values are attached in an OR condition.
   * <p/>
   * The filter must be applied manually.
   */
  public Filter getFilter(String categoryName, Object... values) throws NoSuchFieldException {
    FilterType<?> filterType = typeConfiguration.getFilter(categoryName);
    if (filterType == null) {
      throw new NoSuchFieldException("No such filter: " + categoryName);
    }
    return makeFilterWithValues(filterType, values);
  }

  /**
   * Gets a filter where the categoryName is the symbolic name of the registered filter,
   * and the values are attached in an OR condition.
   * <p/>
   * The filter must be applied manually.
   */
  public Filter getDynamicFilter(String groupName, String name, Object... values) throws NoSuchFieldException {
    DynamicFacetType<?> facetType = typeConfiguration.getDynamicFacetByGroup(groupName);
    if (facetType == null) {
      throw new NoSuchFieldException("No such group: " + groupName);
    }
    if (logger.isDebugEnabled()) {
      logger.debug("Filtering " + facetType.getTagName() + "_" + name + ": " + Arrays.toString(values));
    }
    return makeDynamicFilterWithValues(facetType, name, values);
  }

  private String makeTagName(String solrFieldName) {
    int lastUnderscore = solrFieldName.lastIndexOf('_');
    return lastUnderscore < 0 ? solrFieldName : solrFieldName.substring(0, lastUnderscore);
  }

  /**
   * Gets a filter where the tagName is the symbolic name of the registered filter,
   * and the values are attached in an OR condition.
   * <p/>
   * The filter must be applied manually.
   */
  public Filter getDirectFilter(String solrFieldName, String tagName, Object... values) throws NoSuchFieldException {
    filteredTags.add(tagName);

    return new DirectFilter(solrFieldName, tagName, values);
  }

  /**
   * Adds and applies a filter.
   */
  public void applyFilter(String categoryName, Object... values) throws NoSuchFieldException {
    Filter f = getFilter(categoryName, values);
    apply(f);
  }

  /**
   * Applies some filter.
   */
  public final void apply(Statement f) {
    f.apply(solrQuery);
  }

  @SafeVarargs
  public final <F> Filter makeFilter(FilterType<F> filterType, F... values) {
    return makeFilterWithValues(filterType, (Object[]) values);
  }

  private Filter makeFilterWithValues(FilterType<?> filterType, Object... values) {
    DirectFilter filter = filterType.make(values);
    markAsFiltered(filter);
    return filter;
  }

  private Filter makeDynamicFilterWithValues(DynamicFacetType<?> facetType, String name, Object... values) {
    String encodedName = Encoder.ALL_LETTERS.encode(name);
    DirectFilter filter = facetType.makeForName(encodedName, values);
    markAsFiltered(filter);
    return filter;
  }

  private void markAsFiltered(DirectFilter filter) {
    filteredTags.add(filter.getTagName());
  }

  /**
   * Adds an arbitrary filter, where the query may contain symbolic names of filters by using them
   * in a batch-like type: $KEYWORD=value
   */
  public void addUserDefinedFilter(String filterQuery) throws NoSuchFieldException {
    Matcher m = FIND_KEYWORD.matcher(filterQuery);
    StringBuffer sb = new StringBuffer();
    while (m.find()) {
      String keyword = m.group(1).substring(1); // Ignore leading $ sign
      String field = typeConfiguration.getFieldForAlias(keyword);
      if (field == null) {
        throw new NoSuchFieldException("No field for alias " + keyword);
      }
      m.appendReplacement(sb, field);
    }
    m.appendTail(sb);

    solrQuery.addFilterQuery(sb.toString());
  }

  /**
   * Adds a category for retrieving facet counts, with unlimited number of possible result entries.
   */
  public FacetType<?> addFacet(String categoryName) throws NoSuchFieldException {
    return addFacet(categoryName, -1);
  }

  /**
   * Adds a category for retrieving facet counts.
   *
   * @param categoryName The name of the facet, as defined in the @Facet annotation
   * @param limit How many facet results are returned at max
   * @return The facet type, can also used to filter by this.
   * @throws NoSuchFieldException If there was no such facet defined
   */
  public FacetType<?> addFacet(String categoryName, int limit) throws NoSuchFieldException {
    FacetType<?> facet = typeConfiguration.getFacetByCategory(categoryName);
    if (facet == null) {
      DynamicFacetType<?> dynamicFacetType = typeConfiguration.findDynamicFacetByCategory(categoryName);
      if (dynamicFacetType == null) {
        throw new NoSuchFieldException("No such facet: " + categoryName);
      }
      facet = dynamicFacetType;
      assignDynamicFacet(dynamicFacetType, limit);
    } else {
      assignFacet(facet, limit);
    }
    return facet;
  }

  /**
   * Adds all facets of a given type.
   * Only facets of that exact type, no subclass checking.
   */
  public <T> List<FacetType<T>> addFacetsByType(Class<T> type) throws NoSuchFieldException {
    return addFacetsByType(type, -1);
  }

  /**
   * Adds all facets of a given type.
   * Only facets of that exact type, no subclass checking.
   */
  public <T> List<FacetType<T>> addFacetsByType(Class<T> type, int limit) throws NoSuchFieldException {
    List<FacetType<T>> facetTypeList = typeConfiguration.getFacetsByType(type);
    if (facetTypeList == null) {
      throw new NoSuchFieldException("No facet with type " + type.getName());
    }
    for (FacetType<T> t : facetTypeList) {
      assignFacet(t, limit);
    }
    return facetTypeList;
  }

  /**
   * Adds a dynamic facet, like for tags or analytics data.
   *
   * Dynamic facets result from @Facet annotations with a star (*) in their category name.
   *
   * @param groupName The group name - that is either the tag or the facet name without the star
   * @param name The part replaced by the star
   * @return The facet type, can be used for filtering as well
   * @throws NoSuchFieldException Checks only on existing groups, not on names
   */
  public FacetType<?> addDynamicFacet(String groupName, String name) throws NoSuchFieldException {
    return addDynamicFacet(groupName, name, -1);
  }

  /**
   * Adds a dynamic facet, like for tags or analytics data.
   *
   * Dynamic facets result from @Facet annotations with a star (*) in their category name.
   *
   * @param groupName The group name - that is either the tag or the facet name without the star
   * @param name The part replaced by the star
   * @param limit Limits the maximum number of facet results
   * @return The facet type, can be used for filtering as well
   * @throws NoSuchFieldException Checks only on existing groups, not on names
   */
  public FacetType<?> addDynamicFacet(String groupName, String name, int limit) throws NoSuchFieldException {
    DynamicFacetType<?> dynamicFacetType = typeConfiguration.findDynamicFacetByCategory(groupName);
    if (dynamicFacetType == null) {
      throw new NoSuchFieldException("No such facet: " + groupName);
    }
    String encodedName = Encoder.ALL_LETTERS.encode(name);
    addDynamicFacet(dynamicFacetType.getSolrFieldNameFor(encodedName), groupName, encodedName,
            dynamicFacetType.getHint(), limit);
    return dynamicFacetType;
  }

  private void configureForFacets(ValueRange defaultRange) {
    solrQuery.setFacet(true);
    solrQuery.setFacetMinCount(1);
    solrQuery.set("facet.method", rangeToFacetTypeMapping.get(defaultRange));
  }

  /**
   * Adds a facet by the Solr field name.
   *
   * @param solrFieldName As the name says
   * @param tagName Used to find the facet values in the {@link Result}.
   * @param hint If this is a large or small expected value range
   */
  public void addDirectFacet(String solrFieldName, String tagName, Hint hint) {
    addDirectFacet(solrFieldName, tagName, hint, -1);
  }

  /**
   * Adds a facet by the Solr field name.
   *
   * @param solrFieldName As the name says
   * @param tagName Used to find the facet values in the {@link Result}.
   * @param hint If this is a large or small expected value range
   * @param limit Limits the maximum number of facet results
   */
  public void addDirectFacet(String solrFieldName, String tagName, Hint hint, int limit) {
    Facet f = new Facet(solrFieldName, tagName, hint, limit);
    assignedFacets.add(f);
  }

  private void assignFacet(FacetType<?> facetType, int limit) {
    String tagName = facetType.getTagName();
    String solrFieldName = facetType.getSolrFieldName();
    Facet f = new Facet(solrFieldName, tagName, facetType.getHint(), limit);
    if (logger.isDebugEnabled()) {
      logger.debug("Adding facet " + tagName + ": " + solrFieldName);
    }
    assignedFacets.add(f);
  }

  private void assignDynamicFacet(DynamicFacetType<?> type, int limit) {
    Hint hint = type.getHint();
    String tagName = type.getTagName();
    List<String> fieldNames = realm.getFieldNames();
    if (logger.isDebugEnabled()) {
      logger.debug("I got " + fieldNames.size() + " field names.");
    }
    List<String> matchingFacets = new ArrayList<>(fieldNames.size());
    for (String f : fieldNames) {
      String part = type.extractName(f);
      if (part != null) {
        matchingFacets.add(part);
        addDynamicFacet(f, tagName, part, hint, limit);
      }
    }
    if (logger.isDebugEnabled()) {
      logger.debug("Added " + matchingFacets.size() + " facets for dynamic tag " + tagName + ": " + matchingFacets);
    }
  }

  private void addDynamicFacet(String solrFieldName, String group, String name, Hint hint, int limit) {
    addDirectFacet(solrFieldName, group + "_" + name, hint, limit);
  }

  /**
   * Adds all facets defined with the @Facet annotation.
   * Does not add dynamic facets, because their names are unknown.
   */
  public void addAllFacets() {
    solrQuery.setFacet(true);
    FacetType<?>[] allFacets = typeConfiguration.getAllFacets();
    for (FacetType<?> ft : allFacets) {
      assignFacet(ft, -1);
    }
  }

  /**
   * Sets the sort field.
   *
   * @param sorting Some value that must be part of the @Sort annotation in the model
   */
  public void setSorting(String sorting) {
    this.sorting = sorting;
  }

  private void applySorting() {
    if (getLimit() > 0 && Sorts.RELEVANCE.equals(sorting)) {
      String q = solrQuery.getQuery();
      if (q != null && !q.isEmpty() && !q.equals("*:*")) {
        Field sortFunctions = typeConfiguration.getFieldForType(Type.RELEVANCE_RANK);
        if (sortFunctions != null) {
          String plainQuery = q.replaceAll("[^\\w\\s\\.\\-\\+üäöÖÄÜ\\?]", "");
          logger.info("Default sorting is by fields " + sortFunctions);
          StringBuilder sb = new StringBuilder("sum(");
          boolean first = true;
          do {
            if (first) {
              first = false;
            } else {
              sb.append(',');
            }
            int factor = sortFunctions.getOrder();
            if (factor > 1) {
              sb.append("product(");
            }
            sb.append("query({!edismax v='").append(plainQuery).append("' qf=").append(sortFunctions.getSolrName()).append("},0.0001)");
            if (factor > 1) {
              sb.append(',').append(factor).append(')');
            }
          } while ((sortFunctions = sortFunctions.getNext()) != null);

          String sort = sb.append(')').toString();
          logger.info("Sort function: " + sort);
          solrQuery.setSort(sort, SolrQuery.ORDER.desc);
          return;
        }
      }
    }
    SortField sortField = typeConfiguration.getSortField(sorting);
    if (sortField == null) {
      logger.warn("No such sort field: " + sorting);
    } else {
      sortField.apply(solrQuery);
    }
  }

  /**
   * Applies a search phrase as the query string.
   *
   * @param fullText Plain text
   */
  public void setQuery(String fullText) {
    setQuery(fullText, DEFAULT_MM_VALUE);
  }

  private static final Pattern QUOTED = Pattern.compile("\"[^\"]+\"");

  private boolean isQuoted(String text) {
    return QUOTED.matcher(text).find();
  }

  /**
   * Applies a search phrase as the query string.
   *
   * @param fullText Plain text
   * @param mm See https://cwiki.apache.org/confluence/display/solr/The+DisMax+Query+Parser
   */
  public void setQuery(String fullText, String mm) {
    if (fullText == null || fullText.trim().isEmpty()) {
      return;
    }
    
    // tags
    if (fullText.contains("#")) {
      Matcher m = TAG_PATTERN.matcher(fullText);
      if (m.find()) {
        fullText = fullText.replace(m.group(1), "");
        solrQuery.addFilterQuery("tags:" + m.group(2));

        if (fullText.trim().isEmpty()) {
          return;
        }
      }
    }

    if (mm != null && !mm.equalsIgnoreCase("off")) {
      boolean containsBooleanOperator = fullText.contains(" AND ") || fullText.contains(" OR ");
      if (containsBooleanOperator) {
        if (logger.isDebugEnabled()) {
          logger.debug("mm parameter is set to " + mm + ", but phrase '" + fullText + "' contains boolean condition.");
        }
      } else {
        solrQuery.set("mm", mm);
      }
    }

    String deftype = isQuoted(fullText) ? DEFAULT_FULLTEXT_DEFTYPE : MULTI_WORD_FULLTEXT_DEFTYPE;
    solrQuery.set("defType", deftype);
    solrQuery.setQuery(fullText);
    // solrQuery.setFields("*", "score");
    // solrQuery.set("synonyms", true);
    // solrQuery.set("synonyms.constructPhrases", true);
    // solrQuery.set("qf", typeConfig.getQF());
    String queriedFields = "text_unstemmed^100 text_en tags^20";

    solrQuery.set("qf", queriedFields);
    solrQuery.set("pf", fieldsWithWeight(queriedFields, 50));
    // solrQuery.set("pf2", fieldsWithWeight(queriedFields, 10));

    // fix solr bug if spellcheck.maxCollationTries is set in config
    solrQuery.set("spellcheck.maxCollationTries", MAX_COLLATION_TRIES);

    if (isDoHighlighting()) {
      solrQuery.setHighlight(true);
      solrQuery.set("hl.fl", typeConfiguration.getNames(getRealm()));
      solrQuery.set("hl.requireFieldMatch", false);
      solrQuery.set("hl.preserveMulti", true);
      solrQuery.set("hl.usePhraseHighlighter", true);
      solrQuery.set("hl.highlightMultiTerm", true);

      solrQuery.setHighlightSnippets(HIGHLIGHT_SNIPPETS);
      solrQuery.setHighlightFragsize(HIGHLIGHT_FRAGSIZE);
    }
  }

  private String fieldsWithWeight(String input, int weight) {
    Matcher m = WEIGHT_MATCH.matcher(input);
    return m.replaceAll("^" + weight + " ");
  }

  /**
   * Sets the slop for boosting. That means, words within this range are being boosted higher.
   */
  public void setSlop(int value) {
    solrQuery.set("ps", value);
  }

  public SolrQuery build() {
    addFinalSettings();
    measurement.queryBuilt();
    return solrQuery;
  }

  /**
   * This is used to measure times and delays.
   */
  public Measurement getMeasurement() {
    return measurement;
  }

  private void addFinalSettings() {
    applyAssignedFacets();
    applySorting();
  }

  private void applyAssignedFacets() {
    if (assignedFacets.isEmpty()) {
      return;
    }
    ValueRange bestDefault = findBestDefaultValueRange();
    configureForFacets(bestDefault);
    for (Facet f : assignedFacets) {
      f.applyTo(solrQuery, bestDefault, filteredTags);
    }
  }

  private ValueRange findBestDefaultValueRange() {
    Map<ValueRange, Integer> occurrences = new EnumMap<>(ValueRange.class);
    ValueRange mostOftenRange = null;
    int maxCount = 1;
    for (Facet f : assignedFacets) {
      ValueRange range = f.hint.getValueRange();
      Integer i = occurrences.get(range);
      if (i == null) {
        occurrences.put(range, 1);
        if (mostOftenRange == null) {
          mostOftenRange = range;
        }
      } else {
        int c = i+1;
        if (c > maxCount) {
          maxCount = c;
          mostOftenRange = range;
        }
        occurrences.put(range, c);
      }
    }
    return mostOftenRange;
  }

  @Override
  public String toString() {
    return "QueryBuilder(" + realm.toString() + ") on " + typeConfiguration.type().getName();
  }

  private static final class Facet {

    private final String fieldName;

    private final String tagName;

    private final Hint hint;

    private final int limit;

    Facet(String fieldName, String tagName, Hint hint, int limit) {
      this.fieldName = fieldName;
      this.tagName = tagName;
      this.hint = hint;
      this.limit = limit;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Facet && ((Facet) obj).fieldName.equals(fieldName);
    }

    @Override
    public int hashCode() {
      return fieldName.hashCode();
    }

    void applyTo(SolrQuery query, ValueRange defaultRange, Collection<String> filteredTags) {
      String facet;
      if (filteredTags.contains(tagName)) {
        facet = "{!ex=" + tagName + "}" + fieldName;
      } else {
        facet = fieldName;
      }
      query.addFacetField(facet);
      ValueRange range = hint.getValueRange();
      if (range != defaultRange) {
        query.set("f." + fieldName + ".facet.method", rangeToFacetTypeMapping.get(range));
      }
      if (hint == Hint.ONLY_COUNT) {
        query.set("f." + fieldName + ".facet.exists", true);
      }
      if (limit > 0) {
        query.set("f." + fieldName + ".facet.limit", limit);
      }
    }
  }

}
