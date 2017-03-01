package org.fiolino.searcher.statement;

import org.apache.solr.client.solrj.SolrQuery;
import org.fiolino.common.util.Strings;

/**
 * A filter for comparing to some values.
 *
 * Created by kuli on 29.02.16.
 */
public class DirectFilter extends Filter {
  private final String solrFieldName;
  private final String tagName;
  private final Object[] values;

  public DirectFilter(String solrFieldName, String tagName, Object... values) {
    this.solrFieldName = solrFieldName;
    this.tagName = tagName;
    this.values = values;
  }

  public String getTagName() {
    return tagName;
  }

  @Override
  protected void addToQuery(SolrQuery solrQuery, String filter) {
    solrQuery.addFilterQuery(filter);
  }

  @Override
  protected void applyTo(StringBuilder sb, boolean negated, boolean allowsNull) {
    if (allowsNull) {
      sb.append('(');
    }
    int n = values.length;
    switch (n) {
      case 0:
        throw new IllegalArgumentException("No values given for filter " + this);
      case 1:
        addQueryTo(sb, negated, allowsNull, values[0]);
        break;
      default:
        Object[] more = new Object[n - 1];
        System.arraycopy(values, 1, more, 0, n - 1);
        addQueryTo(sb, negated, allowsNull, values[0], more);
    }
    if (allowsNull) {
      sb.append(')');
    }
  }

  @Override
  protected void applyLocalParamsTo(ParamContainer container) {
    super.applyLocalParamsTo(container);
    if (tagName != null) {
      container.add("tag=").append(tagName);
    }
  }

  private void addQueryTo(StringBuilder sb, boolean negated, boolean allowsNull, Object value) {
    if (negated ^ (value == null)) {
      sb.append('-');
    }
    sb.append(solrFieldName).append(':');
    allowsNull = appendValueTo(sb, allowsNull, value);
    checkNull(sb, allowsNull);
  }

  private void addQueryTo(StringBuilder sb, boolean negated, boolean allowsNull, Object value, Object... moreValues) {
    if (moreValues.length == 0) {
      addQueryTo(sb, negated, allowsNull, value);
      return;
    }
    if (negated) {
      sb.append('-');
    }
    sb.append(solrFieldName).append(":(");
    allowsNull = appendValueTo(sb, allowsNull, value);
    for (Object o : moreValues) {
      appendSingleValueTo(sb.append(' '), o);
    }
    sb.append(')');

    checkNull(sb, allowsNull);
  }

  private void checkNull(StringBuilder sb, boolean allowsNull) {
    if (allowsNull) {
      sb.append(" OR (*:* NOT ").append(solrFieldName).append(":[* TO *])");
    }
  }

  private boolean appendValueTo(StringBuilder sb, boolean allowsNull, Object value) {
    if (value == null) {
      sb.append("[* TO *]");
      return false;
    }
    if (value instanceof Iterable) {
      return appendCollection(sb, allowsNull, (Iterable<?>) value);
    } else {
      appendSingleValueTo(sb, value);
      return allowsNull;
    }
  }

  private boolean appendCollection(StringBuilder sb, boolean allowsNull, Iterable<?> values) {
    boolean first = true;
    Object firstValue = null;
    for (Object each : values) {
      if (each == null) {
        allowsNull = true;
      } else {
        if (first) {
          first = false;
          firstValue = each;
        } else {
          if (firstValue != null) {
            appendSingleValueTo(sb.append('('), firstValue);
            firstValue = null;
          }
          appendSingleValueTo(sb.append(' '), each);
        }
      }
    }
    if (first) {
      // Then collection was empty
      throw new IllegalArgumentException("Empty collection: " + this);
    } else if (firstValue != null) {
      // Only one element
      appendSingleValueTo(sb, firstValue);
    } else {
      // Multiple values
      sb.append(')');
      sb.append(')');
    }
    return allowsNull;
  }

  private void appendSingleValueTo(StringBuilder sb, Object value) {
    if (value instanceof String) {
      Strings.appendQuotedString(sb, (String) value);
    } else {
      if (value instanceof Number && ((Number) value).longValue() < 0) {
        sb.append('\\');
      }
      sb.append(value);
    }
  }

}
