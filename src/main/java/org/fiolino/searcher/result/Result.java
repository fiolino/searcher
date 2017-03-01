package org.fiolino.searcher.result;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by kuli on 23.03.15.
 */
public class Result<T> {

  private static final Logger logger = LoggerFactory.getLogger(Result.class);

  private final int hitCount;

  private final Map<String, FacetResult<?>> facets = new HashMap<>();

  private final List<DynamicFacetResult<?>> dynamicFacets = new ArrayList<>();

  private final List<T> items;

  private final Map<Object, ResultItem<?>> itemsByID = new HashMap<>();

  private final Set<DidYouMeanResult> didYouMean = new TreeSet<>();

  public Result(int hitCount, int pageSize) {
    this.hitCount = hitCount;
    items = new ArrayList<>(pageSize);
  }

  public int getHitCount() {
    return hitCount;
  }

  void addFacet(FacetResult<?> facet) {
    String[] categories = facet.getSolrType().getCategories();
    for (String c : categories) {
      if (facets.containsKey(c)) {
        throw new IllegalStateException("Facet " + facet + " is already registered.");
      }
      facets.put(c, facet);
    }
  }

  void addDynamicFacet(DynamicFacetResult<?> facet) {
    dynamicFacets.add(facet);
  }

  /**
   * Gets the facet result of a specific category and type.
   * Returns null if there is no such facet, or if the type does not match.
   */
  @Nullable
  public <F> FacetResult<? extends F> getFacet(String category, Class<F> type) {
    FacetResult<?> facet = facets.get(category);
    if (facet == null) {
      return null;
    }

    if (type.isAssignableFrom(facet.getSolrType().getType())) {
      @SuppressWarnings("unchecked")
      FacetResult<? extends F> casted = (FacetResult<? extends F>) facet;
      return casted;
    }
    return null;
  }

  /**
   * Gets the facet result of a type.
   */
  public <F> List<FacetResult<? extends F>> getFacetByType(Class<F> type) {
    List<FacetResult<? extends F>> list = new ArrayList<>();
    for (FacetResult<?> f : facets.values()) {
      if (type.isAssignableFrom(f.getSolrType().getType())) {
        @SuppressWarnings("unchecked")
        FacetResult<? extends F> casted = (FacetResult<? extends F>) f;
        list.add(casted);
      }
    }
    return list;
  }

  public List<DynamicFacetResult<?>> getDynamicFacets() {
    return dynamicFacets;
  }

  public void addItem(ResultItem<T> item) {
    addItem(item.getId(), item);
  }

  public void addItem(Object id, ResultItem<T> item) {
    items.add(item.getBean());
    addSubItem(id, item);
  }

  public void addSubItem(Object id, ResultItem<?> item) {
    if (id == null) {
      logger.error("The id of item <{}> is null!", item);
    }
    itemsByID.put(id, item);
  }

  ResultItem<?> getItemByID(Object id) {
    return itemsByID.get(id);
  }

  public List<T> getItems() {
    return items;
  }

  void addDidYouMean(DidYouMeanResult didYouMeanItem) {
    didYouMean.add(didYouMeanItem);
  }

  public Set<DidYouMeanResult> getDidYouMean() {
    return didYouMean;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + " with " + hitCount + " hits.";
  }
}
