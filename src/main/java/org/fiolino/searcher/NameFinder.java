package org.fiolino.searcher;

import org.fiolino.common.FieldType;
import org.fiolino.common.analyzing.Analyzeable;
import org.fiolino.common.analyzing.AnnotationInterest;
import org.fiolino.common.analyzing.ModelInconsistencyException;
import org.fiolino.common.container.Container;
import org.fiolino.common.container.Selector;
import org.fiolino.common.ioc.Beans;
import org.fiolino.common.processing.*;
import org.fiolino.data.annotation.Indexed;
import org.fiolino.data.annotation.Name;
import org.fiolino.data.annotation.Naming;

import java.lang.invoke.MethodHandles;

import static org.fiolino.common.analyzing.Priority.INITIALIZING;
import static org.fiolino.common.analyzing.Priority.PREPROCESSING;

/**
 * The NameFinder is used to fiind the 'name' attribute of a particular entity.
 * This is needed for the complex search query, where you can look for e.g. country:Germany
 * (when filtering, the id is used instead).
 * <p>
 * Created by kuli on 07.01.16.
 */
class NameFinder extends Analyzeable {

  private static final Selector<String> FIELD_NAME = SearchService.SCHEMA.createSelector();

  private static final Selector<FieldType> FIELD_TYPE = SearchService.SCHEMA.createSelector();

  private final Prefix prefix;

  private String nameField;

  private final boolean hidden;

  private final boolean useTexts;

  private final Cardinality cardinality;

  NameFinder(Prefix prefix, Cardinality cardinality, boolean hidden, boolean useTexts) {
    this.prefix = prefix;
    this.cardinality = cardinality;
    this.hidden = hidden;
    this.useTexts = useTexts;
  }

  @Override
  protected MethodHandles.Lookup getLookup() {
    return MethodHandles.lookup();
  }

  @AnnotationInterest(INITIALIZING)
  @SuppressWarnings("unused")
  private void setNaming(Container configuration, Naming naming) {
    NamingPolicy policy = Beans.get(naming.value(), NamingPolicy.class);
    TypeConfigurationFactory.NAMING_POLICY.set(configuration, policy);
  }

  @AnnotationInterest(INITIALIZING)
  @SuppressWarnings("unused")
  private void setIndexed(ValueDescription field, Container configuration, Indexed indexed) throws ModelInconsistencyException {
    String indexName = indexed.value();
    if (indexName.isEmpty()) {
      indexName = field.getName();
    }
    FieldType type = FieldType.getDefaultFor(field.getTargetType());
    FIELD_NAME.set(configuration, indexName);
    FIELD_TYPE.set(configuration, type);
  }

  @AnnotationInterest(value = PREPROCESSING, annotation = Name.class)
  @SuppressWarnings("unused")
  private void setName(ValueDescription field, Container configuration) {
    String n = configuration.get(FIELD_NAME);
    if (n != null) {
      FieldType ft = configuration.get(FIELD_TYPE);

      if (ft != null) {
        NamingPolicy policy = configuration.get(TypeConfigurationFactory.NAMING_POLICY);

        // if this is not really a text field (e.g. scopes)
        if (ft.equals(FieldType.TEXT) && !useTexts) {
          ft = FieldType.STRING;
        }

        Cardinality fieldCardinality = cardinality.join(field.getGenericType());
        nameField = policy.names(new String[] {n}, prefix, field, ft, Filtered.YES, fieldCardinality, hidden)[0];
      }
    }
  }

  String getNameField() {
    return nameField;
  }
}
