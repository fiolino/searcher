package org.fiolino.searcher.searcher;

import java.util.List;

import org.fiolino.searcher.Realm;
import org.fiolino.searcher.QueryBuilder;
import org.fiolino.searcher.fieldhandling.FacetType;
import org.fiolino.searcher.result.Result;

/**
 * Created by micro on 4/20/2016.
 */
public interface Searcher<T> {
  /**
   * The returned type.
   */
  Class<T> type();

  /**
   * Creates a query builder.
   *
   * @param realm For which mandator
   */
  QueryBuilder createQueryBuilder(Realm realm);

  /**
   * Does the search.
   *
   * @param builder Contains all search specs
   * @return The found result
   */
  Result<T> search(QueryBuilder builder);

  /**
   * Gets the facet information for a specific type.
   *
   * @param tagName Used to identify the facet
   * @return The type
   */
  FacetType<?> getFacetTypeFor(String tagName);

  /**
   * Searches for some given ids.
   *
   * @param realm
   * @param filterField
   * @param ids
   * @return
   */
  List<T> searchByIDs(Realm realm, String filterField, Long... ids);

  /**
   * Gets the suggestion list.
   *
   * @param realm For which mandator
   * @param input the used prefix
   * @return
   */
  List<String> getSuggestions(Realm realm, String input);
}
