package org.fiolino.searcher.result;

import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.SpellCheckResponse;
import org.apache.solr.client.solrj.response.SpellCheckResponse.Collation;
import org.apache.solr.common.SolrDocument;
import org.fiolino.common.analyzing.ModelInconsistencyException;
import org.fiolino.common.processing.Processor;
import org.fiolino.common.util.Encoder;
import org.fiolino.common.util.Instantiator;
import org.fiolino.data.base.Text;
import org.fiolino.searcher.QueryBuilder;
import org.fiolino.searcher.TypeConfiguration;
import org.fiolino.searcher.TypeConfigurationFactory;
import org.fiolino.searcher.fieldhandling.DynamicFacetType;
import org.fiolino.searcher.fieldhandling.FacetType;
import org.fiolino.searcher.fieldhandling.SolrType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Build the result set from a Solr response.
 *
 * Created by kuli on 08.01.16.
 */
public final class ResultBuilder<T> {

  private static final Logger logger = LoggerFactory.getLogger(ResultBuilder.class);

  private final Processor<SolrDocument, ResultItem<T>> processor;

  private final TypeConfiguration<T> typeConfig;

  public ResultBuilder(TypeConfiguration<T> typeConfig, Processor<SolrDocument, ResultItem<T>> processor) {
    this.typeConfig = typeConfig;
    this.processor = processor;
  }

  public static <T> ResultBuilder<T> createAndAnalyze(TypeConfiguration<T> typeConfiguration, Instantiator instantiator) throws ModelInconsistencyException {
    return TypeConfigurationFactory.createAndAnalyze(typeConfiguration, instantiator);
  }

  public ResultItem<T> createResultFrom(T bean, SolrDocument doc) {
    ResultItem<T> item = ResultItem.create(bean);
    try {
      processor.process(doc, item);
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable t) {
      logger.error("Error creating bean for " + doc, t);
      throw new RuntimeException(t);
    }

    return item;
  }

  public void addMetaInformation(QueryBuilder builder, Result<?> result, QueryResponse response) {
    addFacetFields(response.getFacetFields(), result);
    addHighlightInfo(response.getHighlighting(), result);
    if (builder.getLimit() > 0) {
      addDidYouMean(response.getSpellCheckResponse(), result);
    }
  }

  private void addDidYouMean(SpellCheckResponse spellCheckResponse, Result<?> result) {
    if (spellCheckResponse == null) {
      return;
    }

    List<Collation> collations = spellCheckResponse.getCollatedResults();
    if (collations != null) {
      collations.stream().filter(c -> c.getNumberOfHits() > 20).map(c ->
              new DidYouMeanResult(c.getCollationQueryString(), c.getNumberOfHits())).forEach(result::addDidYouMean);
      }
    }

  private void addFacetFields(Iterable<FacetField> facetFields, Result<?> result) {
    if (facetFields != null) {
      for (FacetField ff : facetFields) {
        FacetResult<?> facetResult;
        String name = ff.getName();
        FacetType<?> facetType = typeConfig.getFacetByFieldName(name);
        if (facetType == null) {
          DynamicFacetResult<?> dynamicFacetResult = getDynamicFacetResult(ff);
          if (dynamicFacetResult == null) {
            continue;
          }
          result.addDynamicFacet(dynamicFacetResult);
        } else {
          facetResult = getFacetResult(facetType, ff);
          result.addFacet(facetResult);
        }
      }
    }
  }

  private DynamicFacetResult<?> getDynamicFacetResult(FacetField ff) {

    String name = ff.getName();
    for (DynamicFacetType<?> ft : typeConfig.getDynamicFacets()) {
      String extractedName = ft.extractName(name);
      if (extractedName != null) {
        return getSingleDynamicFacetResult(ff, name, ft, extractedName);
      }
    }
    logger.info("No such facet field: " + name);
    return null;
  }

  private <V> DynamicFacetResult<V> getSingleDynamicFacetResult(FacetField ff, String name,
                                                                DynamicFacetType<V> ft, String extractedName) {
    extractedName = Encoder.ALL_LETTERS.decode(extractedName);
    List<SingleFacetItem<V>> facetItems = collectFacetCounts(ft, ff);
    return new DynamicFacetResult<>(ft, name, extractedName, facetItems);
  }

  private <V> FacetResult<V> getFacetResult(FacetType<V> ft, FacetField ff) {
    List<SingleFacetItem<V>> facetItems = collectFacetCounts(ft, ff);
    return new FacetResult<>(ft, facetItems);
  }

  public void addHighlightInfo(Map<String, Map<String, List<String>>> highlighting, Result<?> result) {
    if (highlighting == null) {
      return;
    }
    for (Map.Entry<String, Map<String, List<String>>> e : highlighting.entrySet()) {
      String id = e.getKey();
      ResultItem<?> item = result.getItemByID(id);
      if (item == null) {
        logger.warn("Highlighting id " + id + " not evaluated.");
      } else {
        e.getValue().forEach((fieldName, snippets) -> {
          int snippetSize = getSnippetSize(fieldName);
          Text[] texts = item.getText(fieldName);
          if (texts == null) {
            logger.debug("No texts defined for field {}", fieldName);
          } else {
            int textPosition = 0;
            Text currentText = null;
            int snippetPosition = 0;
            String[] currentTextSnippets = null;
            for (String snippet : snippets) {
              if (!snippet.contains("<em>")) {
                if (texts.length == snippets.size()) {
                  textPosition++;
                }
                continue;
              }

              if (currentText == null) {
                if (textPosition >= texts.length) {
                  logger.warn("Not enough Text entries (" + texts.length + ") in " + id + " for " + fieldName + ": " + snippets);
                  break;
                }

                currentText = texts[textPosition++];
                snippetPosition = 0;
                currentTextSnippets = new String[snippetSize];
              }
              // String unemphasized;
              if (snippet.contains("<em>")) {
                currentTextSnippets[snippetPosition++] = snippet;
                // unemphasized = snippet.replaceAll("\\</?em\\>", "");
              } else {
                // unemphasized = snippet;
              }
              if (snippetPosition >= snippetSize) { // || t.getText().endsWith(unemphasized)) {
                // Last snippet of this text
                currentTextSnippets = setSnippet(snippetSize, currentText, snippetPosition, currentTextSnippets);
                currentText = null;
              }
            }
            if (currentText != null) {
              setSnippet(snippetSize, currentText, snippetPosition, currentTextSnippets);
            }
          }
        });
      }
    }
  }

  private String[] setSnippet(int snippetSize, Text t, int snippetPosition, String[] currentTextSnippets) {
    if (snippetPosition < snippetSize) {
      currentTextSnippets = Arrays.copyOf(currentTextSnippets, snippetPosition);
    }
    t.setSnippets(currentTextSnippets);
    return currentTextSnippets;
  }

  private int getSnippetSize(String fieldName) {
    Map<String, Object> fieldConfig = typeConfig.getFields().get(fieldName);
    if (fieldConfig == null) {
      return QueryBuilder.HIGHLIGHT_SNIPPETS;
    }
    Object snippets = fieldConfig.get("hl.snippets");
    return snippets == null ? QueryBuilder.HIGHLIGHT_SNIPPETS : (int) snippets;
  }

  private <V> List<SingleFacetItem<V>> collectFacetCounts(SolrType<V> facetType, FacetField ff) {
    List<SingleFacetItem<V>> items = new ArrayList<>(ff.getValueCount());

    for (FacetField.Count count : ff.getValues()) {
      String resultValue = count.getName();
      V instance = facetType.get(resultValue);
      if (instance == null) {
        logger.warn("Result for " + Arrays.toString(facetType.getCategories()) + " is null for " + resultValue);
        continue;
      }

      SingleFacetItem<V> item = new SingleFacetItem<>(instance, (int) count.getCount());
      items.add(item);
    }

    return items;
  }
}
