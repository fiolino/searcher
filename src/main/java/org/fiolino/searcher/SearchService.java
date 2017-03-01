package org.fiolino.searcher;

import org.fiolino.common.container.Schema;
import org.fiolino.common.ioc.Beans;
import org.fiolino.common.ioc.Component;
import org.fiolino.data.base.Identified;
import org.fiolino.searcher.searcher.Searcher;
import org.fiolino.searcher.searcher.Searches;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by kuli on 20.03.15.
 */
@Component
public class SearchService {
  static final Schema SCHEMA = new Schema("Search");

  private static final Logger logger = LoggerFactory.getLogger(SearchService.class.getName());

  private final Map<Class<?>, Searcher<?>> searchers;

  {
    searchers = new HashMap<>();
    initializeSearchers();
  }

  private void initializeSearchers() {

    Set<Class<? extends Searcher>> concreteSearchers = Beans.getReflections().getSubTypesOf(Searcher.class);
    for (Class<? extends Searcher> c : concreteSearchers) {
      if (Modifier.isAbstract(c.getModifiers())) {
        continue;
      }
      Searches annotation = c.getAnnotation(Searches.class);
      if (annotation == null) {
        continue;
      }
      Constructor<? extends Searcher> constructor;
      try {
        constructor = c.getConstructor();
      } catch (NoSuchMethodException ex) {
        logger.warn(c.getName() + " has no empty constructor.");
        continue;
      }
      Searcher<?> searcher;
      try {
        searcher = constructor.newInstance();
      } catch (InstantiationException | IllegalAccessException ex) {
        throw new AssertionError("Constructor " + c.getName() + "() threw exception!", ex);
      } catch (InvocationTargetException ex) {
        throw new AssertionError("Constructor " + c.getName() + "() threw exception!", ex.getCause());
      }
      Class<?> type = searcher.type();
      if (searchers.containsKey(type)) {
        throw new IllegalStateException("Multiple searchers declared for " + type.getName());
      }
      searchers.put(type, searcher);
    }
  }

  /**
   * THIS IS THE MAIN API METHOD!
   */
  public <T> Searcher<T> getSearcher(Class<T> modelType) {
    @SuppressWarnings("unchecked")
    Searcher<T> searcher = (Searcher<T>) searchers.get(modelType);
    if (searcher == null) {
      throw new IllegalArgumentException("No Searcher for domain type " + modelType.getName());
    }

    return searcher;
  }

  public QueryBuilder createQueryBuilder(Class<? extends Identified> modelType, Realm realm) {
    Searcher<?> searcher = getSearcher(modelType);
    return searcher.createQueryBuilder(realm);
  }
}
