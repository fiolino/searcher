package org.fiolino.searcher;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

abstract class FullTextField implements Comparable<FullTextField> {

  private final float relevancy;

  FullTextField(float relevancy) {
    this.relevancy = relevancy;
  }

  @Override
  public int compareTo(@Nonnull FullTextField other) {
    return Float.compare(other.relevancy, relevancy);
  }

  abstract void appendNamesTo(StringBuilder sb, Realm realm);
  abstract void appendWeightedNamesTo(StringBuilder sb, float relevancy, Realm realm);

  private void appendWeightedNamesTo(StringBuilder sb, Realm realm) {
    if (relevancy == 1.0f) {
      appendNamesTo(sb, realm);
    } else {
      appendWeightedNamesTo(sb, relevancy, realm);
    }
  }

  private static class StaticFullTextField extends FullTextField {
    private final String name;

    StaticFullTextField(float relevancy, String name) {
      super(relevancy);
      this.name = name;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof StaticFullTextField && ((StaticFullTextField) obj).name.equals(name);
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }

    @Override
    void appendNamesTo(StringBuilder sb, Realm realm) {
      sb.append(name).append(' ');
    }

    @Override
    void appendWeightedNamesTo(StringBuilder sb, float relevancy, Realm realm) {
      sb.append(name).append('^').append(relevancy).append(' ');
    }
  }

  private static class DynamicFullTextField extends FullTextField {
    private final Pattern namePattern;

    DynamicFullTextField(float relevancy, Pattern namePattern) {
      super(relevancy);
      this.namePattern = namePattern;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof DynamicFullTextField && ((DynamicFullTextField) obj).namePattern.equals(namePattern);
    }

    @Override
    public int hashCode() {
      return namePattern.hashCode();
    }

    @Override
    void appendNamesTo(StringBuilder sb, Realm realm) {
      List<String> fieldNames = realm.getFieldNames();
      for (String f : fieldNames) {
        if (namePattern.matcher(f).matches()) {
          sb.append(f).append(' ');
        }
      }
    }

    @Override
    void appendWeightedNamesTo(StringBuilder sb, float relevancy, Realm realm) {
      List<String> fieldNames = realm.getFieldNames();
      for (String f : fieldNames) {
        if (namePattern.matcher(f).matches()) {
          sb.append(f).append('^').append(relevancy).append(' ');
        }
      }
    }
  }

  static String createQF(Collection<FullTextField> qfFields, Realm realm) {
    StringBuilder sb = new StringBuilder();
    List<FullTextField> fields = new ArrayList<>(qfFields);
    Collections.sort(fields);
    for (FullTextField f : fields) {
      f.appendWeightedNamesTo(sb, realm);
    }
    return sb.toString();
  }

  static String createNames(Collection<FullTextField> fields, Realm realm) {
    StringBuilder sb = new StringBuilder();
    for (FullTextField f : fields) {
      f.appendNamesTo(sb, realm);
    }
    return sb.toString();
  }

  static FullTextField createStatic(String name, float relevancy) {
    return new StaticFullTextField(relevancy, name);
  }

  static FullTextField createDynamic(Pattern namePattern, float relevancy) {
    return new DynamicFullTextField(relevancy, namePattern);
  }
}