package org.fiolino.searcher.searcher;

import org.apache.solr.common.SolrDocument;
import org.fiolino.common.util.Instantiator;
import org.fiolino.searcher.FilterDomain;
import org.fiolino.searcher.TypeConfiguration;

import java.util.function.Supplier;

/**
 * Created by kuli on 07.03.16.
 */
public class GenericSearcher<T> extends AbstractSearcher<T> {
  private final Supplier<T> factory;

  public GenericSearcher(Class<T> modelType) {
    this(modelType, Instantiator.creatorFor(modelType));
  }

  public GenericSearcher(Class<T> modelType, FilterDomain filterDomain) {
    this(modelType, filterDomain, Instantiator.creatorFor(modelType));
  }

  public GenericSearcher(Class<T> modelType, FilterDomain filterDomain, Supplier<T> factory) {
    super(new TypeConfiguration<T>(modelType, filterDomain));
    this.factory = factory;
  }

  public GenericSearcher(Class<T> modelType, Supplier<T> factory) {
    this(modelType, FilterDomain.getDefault());
  }

  @Override
  protected T newInstance(SolrDocument doc) {
    return factory.get();
  }
}