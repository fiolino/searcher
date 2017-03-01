package org.fiolino.searcher;

import org.fiolino.common.reflection.Converters;
import org.fiolino.data.annotation.Hint;
import org.fiolino.searcher.fieldhandling.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.util.*;

import static java.lang.invoke.MethodType.methodType;

/**
 * Created by kuli on 24.03.16.
 */
public final class FilterDomain {

  private static final Logger logger = LoggerFactory.getLogger(FilterDomain.class);

  private final String name;

  private final Map<String, FacetType<?>> facetsByCategory = new HashMap<>();

  private final Map<String, FacetType<?>> facetsByFieldName = new HashMap<>();

  private final Map<Class<?>, List<FacetType<?>>> facetsByType = new HashMap<>();

  private final Map<String, DynamicFacetType<?>> dynamicFacetTypes = new HashMap<>();

  private final Map<String, FilterType<?>> filterMap = new HashMap<>();

  private final Set<FullTextField> fullTextFields = new HashSet<>();

  private static final FilterDomain DEFAULT = new FilterDomain("default");

  private String qf;

  public static FilterDomain getDefault() {
    return DEFAULT;
  }

  public FilterDomain(String name) {
    this.name = name;
  }

  public <F> FilterType<F> registerFilter(String solrName, String tagName, Class<F> filterType, String... filterNames) {
    if (filterNames.length == 0) {
      throw new IllegalArgumentException("No filter names specified.");
    }
    if (filterMapContainsAll(filterNames)) {
      @SuppressWarnings("unchecked")
      FilterType<F> ft = (FilterType<F>) filterMap.get(filterNames[0]);
      return ft;
    }
    FilterType<F> ft = new FilterType<>(solrName, tagName, filterType, filterNames);
    for (String f : filterNames) {
      if (!filterMap.containsKey(f)) {
        filterMap.put(f, ft);
      }
    }
    return ft;
  }

  private boolean filterMapContainsAll(String[] filterNames) {
    for (String f : filterNames) {
      if (!filterMap.containsKey(f)) {
        return false;
      }
    }
    return true;
  }

  private void registerFacet(FacetType<?> facetType) {
    String[] facetNames = facetType.getCategories();
    for (String f : facetNames) {
      if (!facetsByCategory.containsKey(f)) {
        facetsByCategory.put(f, facetType);
      }
    }

    String solrFieldName = facetType.getSolrFieldName();
    if (!facetsByFieldName.containsKey(solrFieldName)) {
      facetsByFieldName.put(solrFieldName, facetType);
    }

    Class<?> type = facetType.getType();
    List<FacetType<?>> facets = facetsByType.computeIfAbsent(type, t -> new ArrayList<>());
    facets.add(facetType);
  }

  FacetType<String> registerStringFacet(String solrName, String tagName, Hint hint, String... facetNames) {
    FacetType<String> facetType = new StringFacetType(solrName, tagName, hint, facetNames);
    registerFacet(facetType);
    registerFilter(solrName, tagName, String.class, facetNames);
    return facetType;
  }

  <F> FacetType<F> registerFacetWith(String solrName, String tagName, Class<F> targetType,
                                            Hint hint, String... facetNames) {
    if (String.class.equals(targetType)) {
      @SuppressWarnings("unchecked")
      FacetType<F> stringFacetType = (FacetType<F>) registerStringFacet(solrName, tagName, hint, facetNames);
      return stringFacetType;
    } else {
      MethodHandle converter = Converters.findConverter(Converters.defaultConverters,
              String.class, targetType);
      return registerFacetWith(converter, solrName, tagName, targetType, hint, facetNames);
    }
  }

  <F> FacetType<F> registerFacetWith(MethodHandle facetHandler, String solrName, String tagName,
                                            Class<F> targetType, Hint hint, String... facetNames) {
    MethodHandle objectFactoryHandle = facetHandler.asType(methodType(Object.class, String.class));
    FacetType<F> facetType = new DeserializingFacetType<>(objectFactoryHandle,
            targetType, solrName, tagName, hint, facetNames);
    registerFacet(facetType);
    return facetType;
  }

  <F> void registerDynamicFacetWith(MethodHandle facetHandler, Class<F> valueType, String solrName,
                                           String solrFilterName, Hint hint, String... facetNames) {
    MethodHandle objectFactoryHandle = facetHandler.asType(methodType(Object.class, String.class));
    DynamicFacetType<F> facetType = new DynamicFacetType<>(objectFactoryHandle, valueType, solrName,
            solrFilterName, hint, facetNames);
    String tagName = facetType.getTagName();
    if (!dynamicFacetTypes.containsKey(tagName)) {
      dynamicFacetTypes.put(tagName, facetType);
    }
  }

  @Nullable public FilterType<?> getFilter(String filterName) {
    return filterMap.get(filterName);
  }

  @Nullable public FacetType<?> getFacetByCategory(String facetName) {
    return facetsByCategory.get(facetName);
  }

  @Nullable public FacetType<?> getFacetByFieldName(String facetName) {
    return facetsByFieldName.get(facetName);
  }

  @SuppressWarnings("unchecked")
  @Nullable public <T> List<FacetType<T>> getFacetsByType(Class<T> type) {
    return (List<FacetType<T>>) (List) facetsByType.get(type);
  }

  public Collection<DynamicFacetType<?>> getDynamicFacets() {
    return dynamicFacetTypes.values();
  }

  @Nullable public DynamicFacetType<?> getDynamicFacetByGroup(String group) {
    return dynamicFacetTypes.get(group);
  }

  public FacetType<?>[] getAllFacets() {
    return facetsByCategory.values().toArray(new FacetType<?>[0]);
  }

  void registerFullTextField(FullTextField textField) {
    fullTextFields.add(textField);
    qf = null;
  }

  public synchronized String getQF(Realm realm) {
    if (qf != null) {
      return qf;
    }
    qf = FullTextField.createQF(fullTextFields, realm);
    return qf;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + " \"" + name + "\"";
  }
}
