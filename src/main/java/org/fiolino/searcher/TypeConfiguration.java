package org.fiolino.searcher;

import org.apache.solr.common.SolrDocument;
import org.fiolino.common.processing.Processor;
import org.fiolino.data.annotation.Hint;
import org.fiolino.data.annotation.SortDirection;
import org.fiolino.data.annotation.Sorts;
import org.fiolino.data.annotation.Type;
import org.fiolino.searcher.fieldhandling.DynamicFacetType;
import org.fiolino.searcher.fieldhandling.FacetType;
import org.fiolino.searcher.fieldhandling.FilterType;
import org.fiolino.searcher.result.ResultBuilder;
import org.fiolino.searcher.result.ResultItem;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Created by kuli on 20.03.15.
 */
public class TypeConfiguration<T> {

  private static final SortField DEFAULT_RELEVANCE_SCORE = new SortField("score", SortDirection.DESC);

  private final Class<T> modelType;

  private final FilterDomain filterDomain;

  /**
   * Field aliases are used to find the correct fields for complex queries.
   */
  private final Map<String, String> fieldAliases = new HashMap<>();

  /**
   * Contains all indexed fields as keys.
   * The values may contain a parameter map: individual parameters mapped by their Solr key name.
   */
  private final Map<String, Map<String, Object>> fields = new HashMap<>();

  private final Map<Type, Field> registeredFields;

  private final Set<FullTextField> fullTextFields = new HashSet<>();

  private final Map<String, SortField> sortFields = new HashMap<>();

  private String qf;
  private String names;

  public TypeConfiguration(Class<T> modelType, FilterDomain filterDomain) {
    this.modelType = modelType;
    this.filterDomain = filterDomain;
    sortFields.put(Sorts.RELEVANCE, DEFAULT_RELEVANCE_SCORE);

    registeredFields = new EnumMap<>(Type.class);
  }

  ResultBuilder<T> createResultBuilder(Processor<SolrDocument, ResultItem<T>> processor) {
    return new ResultBuilder<>(this, processor);
  }

  public Class<T> type() {
    return modelType;
  }

  public <F> FilterType<F> registerFilter(String solrName, String tagName, Class<F> filterType, String... filterNames) {
    return filterDomain.registerFilter(solrName, tagName, filterType, filterNames);
  }

  public FacetType<String> registerStringFacet(String solrName, String tagName, Hint hint, String... facetNames) {
    FacetType<String> facetType = filterDomain.registerStringFacet(solrName, tagName, hint, facetNames);
    registerField(solrName);
    return facetType;
  }

  <F> FacetType<F> registerFacetWith(String solrName, String tagName, Class<F> targetType,
                                     Hint hint, String... facetNames) {
    return filterDomain.registerFacetWith(solrName, tagName, targetType, hint, facetNames);
  }

  <F> FacetType<F> registerFacetWith(MethodHandle facetHandler, String solrName, String tagName, Class<F> targetType,
                                     Hint hint, String... facetNames) {
    return filterDomain.registerFacetWith(facetHandler, solrName, tagName, targetType, hint, facetNames);
  }

  void registerDynamicFacetWith(String solrName, Hint hint, String... facetNames) {
    MethodHandle identity = MethodHandles.identity(String.class);
    registerDynamicFacetWith(identity, String.class, solrName, solrName, hint, facetNames);
  }

  void registerDynamicFacetWith(MethodHandle converter, Class<?> valueType, String solrName,
                                    String solrFilterName, Hint hint, String... facetNames) {
    filterDomain.registerDynamicFacetWith(converter, valueType, solrName, solrFilterName, hint, facetNames);
  }

  void registerField(String fieldName, String... aliases) {
    register(fieldName);
    for (String a : aliases) {
      fieldAliases.put(a, fieldName);
    }
  }

  private void register(String fieldName) {
    if (!fields.containsKey(fieldName)) {
      fields.put(fieldName, new HashMap<>());
    }
  }

  void registerField(Type t, Class<?> type, String name, String fieldName, int order) {
    Field f = registeredFields.get(t);
    if (f == null) {
      f = new Field(type, name, fieldName, order);
    } else {
      f = f.insert(type, name, fieldName, order);
    }
    registeredFields.put(t, f);
  }

  void addFieldParameter(String field, String parameter, Object value) {
    Map<String, Object> parameterValueMap = fields.get(field);
    if (parameterValueMap == null) {
      throw new IllegalArgumentException("Field " + field + " not registered yet!");
    }
    parameterValueMap.put(parameter, value);
  }

  void registerFullTextFields(String[] fieldNames, float boost) {
    for (String f : fieldNames) {
      FullTextField textField = FullTextField.createStatic(f, boost);
      filterDomain.registerFullTextField(textField);
      fullTextFields.add(textField);
    }
    qf = null;
    names = null;
  }

  void registerFullTextField(Pattern namePattern, float boost) {
    FullTextField textField = FullTextField.createDynamic(namePattern, boost);
    filterDomain.registerFullTextField(textField);
    fullTextFields.add(textField);
    qf = null;
    names = null;
  }

  void registerSortField(String sortBy, String fieldName, SortDirection direction, int order) {
    SortField sortField = sortFields.get(sortBy);
    if (sortField == null) {
      resetSortField(sortBy, fieldName, direction, order);
      return;
    }
    sortFields.put(sortBy, sortField.insert(fieldName, direction, order));
  }

  void resetSortField(String sortBy, String fieldName, SortDirection direction, int order) {
    sortFields.put(sortBy, new SortField(fieldName, direction, order));
  }

  @Nullable
  public SortField getSortField(String sortBy) {
    return sortFields.get(sortBy);
  }

  public Map<String, Map<String, Object>> getFields() {
    return fields;
  }

  @Nullable
  public FilterType<?> getFilter(String filterName) {
    return filterDomain.getFilter(filterName);
  }

  @Nullable
  DynamicFacetType<?> findDynamicFacetByFieldName(String solrName) {
    for (DynamicFacetType<?> dyn : getDynamicFacets()) {
      if (dyn.extractName(solrName) != null) {
        return dyn;
      }
    }
    return null;
  }

  @Nullable
  DynamicFacetType<?> findDynamicFacetByCategory(String category) {
    for (DynamicFacetType<?> dyn : getDynamicFacets()) {
      if (dyn.contains(category)) {
        return dyn;
      }
    }
    return null;
  }

  @Nullable
  public String getFieldForAlias(String symbolicName) {
    return fieldAliases.get(symbolicName);
  }

  public Field getFieldForType(Type t) {
    return registeredFields.get(t);
  }

  @Nullable
  public FacetType<?> getFacetByCategory(String facetName) {
    return filterDomain.getFacetByCategory(facetName);
  }

  @Nullable
  public FacetType<?> getFacetByFieldName(String facetName) {
    return filterDomain.getFacetByFieldName(facetName);
  }

  @Nullable
  public <F> List<FacetType<F>> getFacetsByType(Class<F> type) {
    return filterDomain.getFacetsByType(type);
  }

  public Collection<DynamicFacetType<?>> getDynamicFacets() {
    return filterDomain.getDynamicFacets();
  }

  @Nullable
  public DynamicFacetType getDynamicFacetByGroup(String group) {
    return filterDomain.getDynamicFacetByGroup(group);
  }

  public FacetType<?>[] getAllFacets() {
    return filterDomain.getAllFacets();
  }

  public synchronized String getQF(Realm realm) {
    if (qf != null) {
      return qf;
    }
    qf = FullTextField.createQF(fullTextFields, realm);
    return qf;
  }

  public synchronized String getNames(Realm realm) {
    if (names != null) {
      return names;
    }
    names = FullTextField.createNames(fullTextFields, realm);
    return names;
  }

  public String getDomainQF(Realm realm) {
    return filterDomain.getQF(realm);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(modelType.getSimpleName());
    boolean first = true;
    for (String f : fields.keySet()) {
      if (first) {
        sb.append(" using fields: ");
        first = false;
      } else {
        sb.append(',');
      }
      sb.append(f);
    }

    return sb.toString();
  }
}
