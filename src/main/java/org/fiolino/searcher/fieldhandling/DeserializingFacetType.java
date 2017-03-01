package org.fiolino.searcher.fieldhandling;

import org.fiolino.data.annotation.Hint;

import java.lang.invoke.MethodHandle;
import java.util.Arrays;

/**
 * Created by Michael Kuhlmann on 25.01.2016.
 */
public class DeserializingFacetType<T> extends FacetType<T> {
  /**
   * This converts a String to the resulting type.
   */
  private final MethodHandle converter;

  public DeserializingFacetType(MethodHandle converter, Class<T> type, String solrFieldName, String tagName,
                                Hint hint, String... categories) {
    super(type, solrFieldName, tagName, hint, categories);
    this.converter = converter;
  }

  @Override
  public T get(String serialization) {
    try {
      return getType().cast(converter.invokeExact(serialization));
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable t) {
      throw new IllegalStateException("Error building facet for " + Arrays.toString(getCategories()) + " using " + serialization, t);
    }
  }
}
