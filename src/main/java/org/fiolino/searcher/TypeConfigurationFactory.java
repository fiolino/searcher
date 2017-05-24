package org.fiolino.searcher;

import org.apache.solr.common.SolrDocument;
import org.fiolino.common.analyzing.Analyzeable;
import org.fiolino.common.analyzing.AnnotationInterest;
import org.fiolino.common.analyzing.ModelInconsistencyException;
import org.fiolino.common.container.Container;
import org.fiolino.common.container.Selector;
import org.fiolino.common.ioc.Beans;
import org.fiolino.common.processing.Analyzer;
import org.fiolino.common.processing.FieldDescription;
import org.fiolino.common.processing.ModelDescription;
import org.fiolino.common.processing.Processor;
import org.fiolino.common.reflection.Converters;
import org.fiolino.common.reflection.ExceptionHandler;
import org.fiolino.common.reflection.Methods;
import org.fiolino.common.util.*;
import org.fiolino.data.annotation.*;
import org.fiolino.data.base.Text;
import org.fiolino.searcher.names.*;
import org.fiolino.searcher.result.ResultBuilder;
import org.fiolino.searcher.result.ResultItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.invoke.MethodType.methodType;
import static org.fiolino.common.analyzing.Priority.*;

/**
 * Created by kuli on 04.01.16.
 */
public abstract class TypeConfigurationFactory<T> extends Analyzeable {

    private static final String FACET_ID_SUFFIX = "_facetid";

    private static final String FACET_SUFFIX = "_facet";

    private static final Logger logger = LoggerFactory.getLogger(TypeConfigurationFactory.class);

    private static final Selector<String[]> FIELD_NAMES = SearchService.SCHEMA.createSelector(new String[0]);

    private static final Selector<String[]> FIELD_ALIASES = SearchService.SCHEMA.createSelector(new String[0]);

    private static final Selector<Sorts> SORT_ANNOTATION = SearchService.SCHEMA.createSelector();

    private static final Selector<Boolean> IS_HANDLED_BY_FACET = SearchService.SCHEMA.createSelector(Boolean.FALSE);

    private static final Selector<Filtered> FILTERED = SearchService.SCHEMA.createSelector(Filtered.NO);

    private static final Selector<String[]> FILTER_NAMES = SearchService.SCHEMA.createSelector();

    private static final Selector<String[]> FACET_NAMES = SearchService.SCHEMA.createSelector();

    static final Selector<NamingPolicy> NAMING_POLICY = SearchService.SCHEMA.createSelector(
            Beans.get(NamingPolicy.DEFAULT_NAME, NamingPolicy.class));

    final TypeConfiguration<T> typeConfig;

    private final Prefix prefix;
    private final Instantiator instantiator;
    private final DeserializerBuilder deserializerBuilder;

    private final boolean useTexts;

    private final Cardinality cardinality;

    Processor<SolrDocument, ResultItem<T>> processor = Processor.doNothing();

    private final Set<String> processedCategories;

    private TypeConfigurationFactory(TypeConfiguration<T> typeConfig, Instantiator instantiator) {
        this(typeConfig, instantiator, new DeserializerBuilder(instantiator), Prefix.root(), true, Cardinality.TO_ONE, new HashSet<>());
    }

    private TypeConfigurationFactory(TypeConfiguration<T> typeConfig, Instantiator instantiator, DeserializerBuilder deserializerBuilder,
                                     Prefix prefix, boolean useTexts, Cardinality cardinality, Set<String> processedCategories) {
        this.typeConfig = typeConfig;
        this.instantiator = instantiator;
        this.deserializerBuilder = deserializerBuilder;
        this.prefix = prefix;
        this.useTexts = useTexts;
        this.cardinality = cardinality;
        this.processedCategories = processedCategories;
    }

    public static <T> ResultBuilder<T> createAndAnalyze(TypeConfiguration<T> typeConfiguration, Instantiator instantiator) throws ModelInconsistencyException {
        Container configuration = SearchService.SCHEMA.createContainer();
        ModelDescription modelDescription = new ModelDescription(typeConfiguration.type(), configuration);
        MainTypeConfigurationFactory<T> factory = new MainTypeConfigurationFactory<>(typeConfiguration, instantiator);
        Analyzer.analyzeAll(modelDescription, factory);

        return factory.createResultBuilder();
    }

    public Class<?> type() {
        return typeConfig.type();
    }

    TypeConfiguration<T> getTypeConfig() {
        return typeConfig;
    }

    @Override
    protected MethodHandles.Lookup getLookup() {
        return MethodHandles.lookup();
    }

    private boolean fieldIsMulti(FieldDescription field) {
        return Collection.class.isAssignableFrom(field.getValueType());
    }

    private boolean fieldIsMap(FieldDescription field) {
        return Map.class.isAssignableFrom(field.getValueType());
    }

    void register(Processor<SolrDocument, ResultItem<T>> next) {
        processor = processor.andThen(next);
    }

    void registerField(FieldDescription field,
                       String name,
                       String[] solrNames,
                       Class<?> targetType,
                       float boost) {
        Container configuration = field.getConfiguration();
        String[] aliases = configuration.get(FIELD_ALIASES);
        String s = solrNames[0];
        typeConfig.registerField(s, aliases);
        Register reg = field.getAnnotation(Register.class);
        if (reg != null) {
            if (reg.fields().length > 0) {
                throw new IllegalStateException(field + " has @Register annotation with fields defined but is no relation.");
            }
            registerForTypes(name, reg, targetType, s);
            typeConfig.registerField(s, reg.aliases());
        }

        Sorts sorting = configuration.get(SORT_ANNOTATION);
        if (sorting != null) {
            typeConfig.registerSortField(sorting.value(), s, sorting.direction(), sorting.order());
        }

        boolean fieldIsMap = fieldIsMap(field);
        boolean isFullText = useTexts && Text.class.isAssignableFrom(targetType);
        MethodHandle setter = createSetterOf(field);
        if (setter == null) {
            return;
        }
        MethodHandle converter = findConverter(targetType);
        if (fieldIsMap) {
            MethodHandle convertedSetter = makeTypedSetter(setter, Map.class);
            Pattern wildcardExpander = createWildcardPattern(s);
            if (isFullText) {
                typeConfig.registerFullTextField(wildcardExpander, boost);
            }
            MapFieldProcessor<T> fieldProcessor;
            Class<?> mapValueType = Types.erasedArgument(field.getGenericType(), Map.class, 1, Types.Bounded.UPPER);
            if (Collection.class.isAssignableFrom(mapValueType)) {
                fieldProcessor = new MultiValueMapFieldProcessor<>(convertedSetter, wildcardExpander, converter);
            } else {
                fieldProcessor = new MapFieldProcessor<>(convertedSetter, wildcardExpander, converter);
            }
            register(fieldProcessor);
        } else {
            if (isFullText) {
                typeConfig.registerFullTextFields(solrNames, boost);
            }
            if (fieldIsMulti(field)) {
                MethodHandle listSetter = makeTypedSetter(setter, List.class);
                register(new MultiFieldProcessor<>(listSetter, solrNames, converter));
            } else {
                if (targetType.isEnum()) {
                    setter = MethodHandles.filterArguments(makeObjectSetter(setter), 1, converter);
                } else if (Text.class.isAssignableFrom(targetType)) {
                    register(new TextFieldProcessor<>(setter, solrNames));
                    return;
                }
                register(new SingleFieldProcessor<>(setter, solrNames));
            }
        }
    }

    void registerForTypes(String name, Register reg, Class<?> targetType, String solrName) {
        for (Type t : reg.value()) {
            typeConfig.registerField(t, targetType, name, solrName, reg.order());
        }
    }

    private MethodHandle findConverter(Class<?> targetType) {
        MethodHandle converter;
        if (targetType.isEnum()) {
            converter = Methods.convertStringToEnum(targetType.asSubclass(Enum.class), (f, v) -> {
                IndexedAs anno = f.getAnnotation(IndexedAs.class);
                return anno == null ? null : anno.value();
            });
            converter = Methods.wrapWithExceptionHandler(converter, IllegalArgumentException.class, (ex, v) -> {
                    logger.warn("No enum " + v[0] + " for " + targetType.getName());
                    return null;
                });
        } else if (Text.class.isAssignableFrom(targetType)) {
            converter = Converters.createSimpleConverter(getLookup(), String.class, Text.class);
        } else {
            return MethodHandles.identity(Object.class);
        }
        return converter.asType(methodType(Object.class, Object.class));
    }

    private Pattern createWildcardPattern(String fieldName) {
        return Pattern.compile("^" + fieldName.replace("*", "(.*)") + "$");
    }

    private static MethodHandle makeObjectSetter(MethodHandle setter) {
        return makeTypedSetter(setter, Object.class);
    }

    private static MethodHandle makeTypedSetter(MethodHandle setter, Class<?> valueType) {
        return setter.asType(methodType(void.class, Object.class, valueType));
    }

    /**
     * Registers a facet whioch is a relation to a full serialized object.
     */
    private <V> void registerFacetRelation(FieldDescription field, String[] facetNames,
                                           String solrName, String tagName, Class<V> targetType,
                                           Hint hint) throws ModelInconsistencyException {

        MethodHandle facetHandler = deserializerBuilder.getDeserializer(targetType);
        if (fieldIsMap(field)) {
            if (solrName.indexOf('*') < 0) {
                solrName += "_*";
            }

            String facetName = solrName + FACET_SUFFIX;
            typeConfig.registerDynamicFacetWith(facetHandler, targetType, facetName,
                    facetName, hint, facetNames);
        } else {
            typeConfig.registerFacetWith(facetHandler, solrName + FACET_SUFFIX, tagName, targetType, hint, facetNames);
            typeConfig.registerFilter(solrName + FACET_ID_SUFFIX, tagName, Long.class, facetNames);
        }

        registerRelationWith(field, facetHandler, solrName + FACET_SUFFIX);
    }

    /**
     * Registers a serialized relation so that the returned value is gonna be written into the model via the setter.
     */
    private void registerRelationWith(FieldDescription field, MethodHandle converter,
                                      String... solrNames) {

        MethodHandle setter = createSetterOf(field);
        if (setter == null) {
            return;
        }

        if (fieldIsMulti(field)) {
            MethodHandle converted = makeTypedSetter(setter, List.class);
            register(new MultiFieldProcessor<>(converted, solrNames, converter));
        } else if (fieldIsMap(field)) {
            MethodHandle converted = makeTypedSetter(setter, Map.class);
            Pattern wildcardExpander = createWildcardPattern(solrNames[0]);
            Class<?> mapValueType = Types.erasedArgument(field.getGenericType(), Map.class, 1, Types.Bounded.UPPER);
            if (Collection.class.isAssignableFrom(mapValueType)) {
                register(new MultiValueMapRelationProcessor<>(converted, wildcardExpander, converter));
            } else {
                register(new MapRelationProcessor<>(converted, wildcardExpander, converter));
            }
        } else {
            // (void)T,String
            MethodHandle fullSetter = MethodHandles.filterArguments(setter, 1, converter);
            register(new SingleFieldProcessor<>(fullSetter, solrNames));
        }
    }

    private void registerDeserializedRelation(FieldDescription field, final String[] solrNames) throws ModelInconsistencyException {
        registerRelationWith(field, deserializerBuilder.getDeserializer(field.getTargetType()), solrNames);
    }

    @AnnotationInterest(INITIALIZING)
    @SuppressWarnings("unused")
    protected void setNaming(Container configuration, Naming naming) {
        NamingPolicy policy = Beans.get(naming.value(), NamingPolicy.class);
        NAMING_POLICY.set(configuration, policy);
    }

    @AnnotationInterest(value = INITIALIZING, annotation = Name.class)
    @SuppressWarnings("unused")
    protected void setName(Container configuration) {
        configuration.set(FILTERED, Filtered.YES);
    }

    @AnnotationInterest(INITIALIZING)
    @SuppressWarnings("unused")
    protected void setFiltered(Container configuration, FieldDescription field, Filterable filterable) {
        Filtered previous = configuration.get(FILTERED);
        if (previous != Filtered.NEVER) {
            configuration.set(FILTERED, Filtered.YES);
            String filterName = filterable.value();
            if (filterName.equals("")) {
                filterName = field.getName();
            }
            configuration.set(FILTER_NAMES, new String[] {filterName});
        }
    }

    @AnnotationInterest(INITIALIZING)
    @SuppressWarnings("unused")
    protected void setSortFlag(Container configuration, Sorts annotation) {
        configuration.set(SORT_ANNOTATION, annotation);
        configuration.set(FILTERED, Filtered.YES);
    }

    private String[] getSolrNames(FieldDescription field, FieldType fieldType, String primary, String[] compatibilities) {
        Container configuration = field.getConfiguration();
        NamingPolicy policy = NAMING_POLICY.get(configuration);
        Filtered filtered = FILTERED.get(configuration);

        if (fieldIsMap(field) && primary.indexOf('*') < 0) {
            primary += "_*";
        }
        if (fieldType == FieldType.TEXT && !useTexts) {
            fieldType = FieldType.STRING;
        }

        String[] names = allNames(primary, compatibilities);
        Cardinality fieldCardinality = cardinality.join(field.getGenericType());
        return policy.names(names, prefix, field, fieldType, filtered, fieldCardinality, false);
    }

    private String[] allNames(String primary, String[] compatibilities) {
        String[] names = new String[compatibilities.length + 1];
        names[0] = primary;
        System.arraycopy(compatibilities, 0, names, 1, compatibilities.length);
        return names;
    }

    @AnnotationInterest(PROCESSING)
    @SuppressWarnings("unused")
    protected void setIndexed(FieldDescription field, Container configuration, Indexed indexed)
            throws ModelInconsistencyException {

        if (Boolean.TRUE.equals(configuration.get(IS_HANDLED_BY_FACET))) {
            // It's a facet relation, it's already handled
            return;
        }
        String primary = indexed.value();
        if (primary.isEmpty()) {
            primary = field.getName();
        }
        Class<?> targetType = field.getTargetType();
        FieldType fieldType = FieldType.getDefaultFor(targetType);
        String[] solrNames = configuration.get(FIELD_NAMES);
        if (solrNames.length == 0) {
            if (fieldType == null) {
                // Then it's probably a relation
                float boost = indexed.boost();
                String[] names = allNames(primary, indexed.compatibleTo());
                handleRelation(field, targetType, names, useTexts && !Float.isNaN(boost) && boost > 0.0);
                return;
            }
            solrNames = getSolrNames(field, fieldType, primary, indexed.compatibleTo());
            if (solrNames == null) {
                return;
            }
            FIELD_NAMES.set(configuration, solrNames);
        }
        registerField(field, primary, solrNames, targetType, getFieldBoost(indexed));

        String[] filterNames = configuration.get(FILTER_NAMES);
        if (filterNames != null && filterNames.length > 0) {
            // That means, it's a simple facet field, or an explicit filter
            if (filterNames[0].length() == 0) {
                filterNames[0] = field.getName();
            }
            typeConfig.registerFilter(solrNames[0], null, targetType, filterNames);
        }
    }

    @AnnotationInterest(POSTPROCESSING)
    @SuppressWarnings("unused")
    protected void setHighlightInfo(Container configuration, Highlighted highlighted) {
        String[] fieldNames = configuration.get(FIELD_NAMES);
        if (fieldNames.length == 0) {
            return;
        }
        int snippets = highlighted.snippets();
        int fragmentSize = highlighted.fragmentSize();

        for (String s : fieldNames) {
            if (snippets > 0) {
                typeConfig.addFieldParameter(s, "hl.snippets", snippets);
            }
            if (fragmentSize > 0) {
                typeConfig.addFieldParameter(s, "hl.fragsize", fragmentSize);
            }
        }
    }

    /**
     * Returns only those categories which are not processed yet.
     * The goal is to have each facet category only once.
     */
    private String[] unusedCategories(String[] categories) {
        if (categories.length == 0) {
            return null;
        }
        List<String> duplicates = null;
        for (String c : categories) {
            if (processedCategories.add(c)) {
                continue;
            }
            if (duplicates == null) {
                duplicates = new ArrayList<>(categories.length);
            }
            duplicates.add(c);
        }
        if (duplicates == null) {
            return categories;
        }
        int n = categories.length - duplicates.size();
        if (n == 0) {
            return null;
        }
        String[] remaining = new String[n];
        int x = 0;
        for (String c : categories) {
            if (!duplicates.contains(c)) {
                remaining[x++] = c;
            }
        }
        return remaining;
    }

    @AnnotationInterest(PREPROCESSING)
    @SuppressWarnings("unused")
    protected void setFacet(FieldDescription field, Container configuration, Facet facet) throws ModelInconsistencyException {
        String[] categories = unusedCategories(facet.value());
        if (categories == null) {
            return;
        }
        configuration.set(FIELD_ALIASES, categories);
        configuration.set(FACET_NAMES, categories);
        String solrName = facet.fieldName();
        if ("".equals(solrName)) {
            solrName = Strings.normalizeName(Strings.combinationOf(categories));
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Registering {} as keys to {}", Arrays.toString(categories), solrName);
        }

        Class<?> targetType = field.getTargetType();
        String tagName = facet.tagName();
        if (tagName.equals("")) {
            tagName = solrName;
        } else {
            tagName = Strings.normalizeName(tagName);
        }

        // Change this
        if (isRelationType(targetType)) {
            registerFacetRelation(field, categories, solrName, tagName, targetType, facet.hint());
        } else {
            registerSimpleFacetField(field, categories, solrName, tagName, targetType, facet.hint());
        }
    }

    @AnnotationInterest(PREPROCESSING)
    @SuppressWarnings("unused")
    protected void setDateFacet(FieldDescription field, Container configuration, DateFacet facet) {
        String yearFieldName = Strings.normalizeName(facet.year()) + FACET_ID_SUFFIX;
        String monthFieldName = Strings.normalizeName(facet.month()) + FACET_ID_SUFFIX;

        String yearTag = Strings.normalizeName(facet.year());
        String monthTag = Strings.normalizeName(facet.month());

        MethodHandle converter = Converters.findConverter(Converters.defaultConverters, String.class, Integer.class);
        typeConfig.registerFacetWith(converter, yearFieldName, yearTag, Integer.class, Hint.LIMITED_SIZE, facet.year());
        typeConfig.registerFacetWith(converter, monthFieldName, monthTag, Integer.class, Hint.LIMITED_SIZE, facet.month());
        typeConfig.registerFilter(yearFieldName, yearTag, Integer.class, facet.year());
        typeConfig.registerField(yearFieldName, facet.year());
        typeConfig.registerFilter(monthFieldName, monthTag, Integer.class, facet.month());
        typeConfig.registerField(monthFieldName, facet.month());
    }

    private void registerSimpleFacetField(FieldDescription field, String[] filterNames, String solrName, String tagName,
                                          Class<?> targetType, Hint hint) {
        FieldType fieldType = FieldType.getDefaultFor(targetType);
        Cardinality fieldCardinality = cardinality.join(field.getGenericType());

        if (fieldIsMap(field)) {
            if (solrName.indexOf('*') < 0) {
                solrName += "_*";
            }
            solrName += "_" + fieldType.getSuffix(fieldCardinality, true, false);
            // TODO Make it work for other types than String
            if (!String.class.equals(targetType)) {
                throw new IllegalStateException("Dynamic facets currently only support Strings!");
            }
            typeConfig.registerDynamicFacetWith(solrName, hint, filterNames);
        } else {
            solrName += "_" + fieldType.getSuffix(fieldCardinality, true, false);
            typeConfig.registerFacetWith(solrName, tagName, targetType, hint, filterNames);
            typeConfig.registerFilter(solrName, tagName, targetType, filterNames);
            field.getConfiguration().set(FIELD_ALIASES, filterNames);
        }
        field.getConfiguration().set(FIELD_NAMES, new String[] {
                solrName
        });
    }

    private boolean isRelationType(Class<?> valueType) {
        return !valueType.isPrimitive() && FieldType.getDefaultFor(valueType) == null;
    }

    private <V> void handleRelation(FieldDescription field, Class<V> targetType, String[] names, boolean useTexts)
            throws ModelInconsistencyException {
        Prefix subPrefix = prefix.newSubPrefix(names);
        String[] aliases = field.getConfiguration().get(FIELD_ALIASES);
        ModelDescription relationTarget = field.getRelationTarget();
        @Nullable Register reg = field.getAnnotation(Register.class);
        if (reg != null) {
            if (reg.fields().length == 0) {
                throw new IllegalStateException(field + " has @Register annotation but has no fields defined; it's a relation.");
            }
        }
        MethodHandle setter = createSetterOf(field);
        if (setter == null) {
            logger.warn("No setter for " + field);
            return;
        }

        if (fieldIsMulti(field) || fieldIsMap(field)) {
            // Will read _rel field if it's not a facet
            if (aliases.length > 0) {
                NameFinder nameFinder = new NameFinder(subPrefix, Cardinality.TO_MANY, false, useTexts);
                Analyzer.analyzeAll(relationTarget, nameFinder);
                String nameField = nameFinder.getNameField();
                if (nameField == null) {
                    logger.warn("No @Name field defined in " + targetType.getName());
                } else {
                    typeConfig.registerField(nameField, aliases);
                }
            }
            String[] solrNames = field.getConfiguration().get(FACET_NAMES);
            if (solrNames == null) {
                solrNames = prefix.createNames(names);
                for (int i = 0; i < solrNames.length; i++) {
                    solrNames[i] += "_rel";
                }
            }
            registerDeserializedRelation(field, solrNames);
            return;
        }
        SubTypeConfigurationFactory<V> subFactory = new SubTypeConfigurationFactory<>(typeConfig,
                instantiator, deserializerBuilder, subPrefix, useTexts, aliases, cardinality, reg, processedCategories);
        Analyzer.analyzeAll(relationTarget, subFactory);
        Supplier<V> factory = instantiator.createSupplierFor(targetType);
        register(subFactory.createRelationSettingProcessor(setter, factory));
    }

    private MethodHandle createSetterOf(FieldDescription field) {
        MethodHandle setter = field.createSetter();
        if (setter == null) return null;
        return Methods.wrapWithExceptionHandler(setter, Throwable.class,
                ExceptionHandler.rethrowException(DataTransferException::new, "Failed to write {2} into {1} for '{0}'"), field.getName());
    }

    private float getFieldBoost(Indexed annotation) {
        if (isUnsearched(annotation)) {
            return 1.0f;
        }
        float fieldBoost = annotation.boost();
        String prop = annotation.boostProperty();
        if (!"".equals(prop)) {
            fieldBoost *= getFloatFromProperty(prop);
        }
        return fieldBoost;
    }

    private float getFloatFromProperty(String value) {
        try {
            String prop = Beans.getProperty(value);
            return Float.parseFloat(prop);
        } catch (IllegalArgumentException e) {
            logger.info(e.getMessage());
            return 1.0f;
        }
    }

    private boolean isUnsearched(Indexed annotation) {
        return Float.isNaN(annotation.boost()) || annotation.boost() <= 0.0;
    }

    private abstract static class AbstractProcessor<T> implements Processor<SolrDocument, ResultItem<T>> {
        final MethodHandle setter;
        private final String[] solrNames;

        AbstractProcessor(MethodHandle setter, String[] solrNames) {
            this.setter = setter;
            this.solrNames = solrNames;
        }

        @Override
        public final void process(SolrDocument doc, ResultItem<T> i) throws Exception {
            T bean = i.getBean();
            for (String s : solrNames) {
                Object value = doc.getFieldValue(s);
                if (value == null) {
                    continue;
                }
                value = convert(value);
                try {
                    if (process(doc, bean, value, s)) {
                        postProcessValue(i, solrNames, value);
                        return;
                    }
                } catch (Exception e) {
                    throw e;
                } catch (Throwable t) {
                    throw new AssertionError(t);
                }
            }

            // If reached here, the attribute is kept unassigned
            if (logger.isDebugEnabled()) {
                logger.debug("No data for " + Arrays.toString(solrNames));
            }
        }

        protected abstract boolean process(SolrDocument doc, T bean, Object value, String solrName) throws Throwable;

        protected void postProcessValue(ResultItem<T> item, String[] fieldNames, Object value) {
            // Nothing to do by default
        }

        protected Object convert(Object value) {
            return value;
        }

        @Override
        public String toString() {
            return this.getClass().getSimpleName() + "[solrNames: " + Arrays.toString(solrNames) + "]";
        }
    }

    private static class SingleFieldProcessor<T> extends AbstractProcessor<T> {

        private SingleFieldProcessor(MethodHandle setter, String[] solrNames) {
            super(makeObjectSetter(setter), solrNames);
        }

        @Override
        protected boolean process(SolrDocument doc, T bean, Object value, String solrName) throws Throwable {
            if (value instanceof Collection) {
                Iterator<?> it = ((Collection<?>) value).iterator();
                if (!it.hasNext()) {
                    return false;
                }
                value = it.next();
            }
            setter.invokeExact(bean, value);
            return true;
        }

        protected void postProcessValue(ResultItem<T> item, String[] fieldNames, Object value) {
            if (value instanceof Text) {
                item.addText(fieldNames, (Text) value);
            }
        }
    }

    private static class TextFieldProcessor<T> extends SingleFieldProcessor<T> {

        private TextFieldProcessor(MethodHandle setter, String[] solrNames) {
            super(setter, solrNames);
        }

        @Override
        protected Object convert(Object value) {
            return Text.valueOf((String) value);
        }

        protected void postProcessValue(ResultItem<T> item, String[] fieldNames, Object value) {
            item.addText(fieldNames, (Text) value);
        }
    }

    /**
     * Relation handler for to-many instances.
     */
    private final static class MultiFieldProcessor<T> extends AbstractProcessor<T> {

        private final MethodHandle converter;

        private MultiFieldProcessor(MethodHandle setter, String[] solrNames, MethodHandle converter) {
            super(setter, solrNames);
            this.converter = converter.asType(methodType(Object.class, Object.class));
        }

        @Override
        protected boolean process(SolrDocument doc, T bean, Object value, String solrName) throws Throwable {
            List<Object> deserializedList;
            if (value instanceof Collection) {
                Collection<?> c = (Collection<?>) value;
                deserializedList = new ArrayList<>(c.size());
                for (Object v : c) {
                    Object each = converter.invokeExact(v);
                    deserializedList.add(each);
                }
            } else {
                deserializedList = Collections.singletonList(converter.invokeExact(value));
            }
            setter.invokeExact(bean, deserializedList);

            return true;
        }
    }

    private static class MapRelationProcessor<T> implements Processor<SolrDocument, ResultItem<T>> {

        private final MethodHandle setter;

        private final Pattern wildcardExpander;

        private final MethodHandle converter;

        private MapRelationProcessor(MethodHandle setter, Pattern wildcardExpander, MethodHandle converter) {
            this.setter = makeTypedSetter(setter, Map.class);
            this.wildcardExpander = wildcardExpander;
            this.converter = converter.asType(methodType(Object.class, String.class));
        }

        @Override
        public void process(SolrDocument doc, ResultItem<T> item) throws Exception {
            Map<String, Object> map = new HashMap<>();
            for (String f : doc.getFieldNames()) {
                Matcher m = wildcardExpander.matcher(f);
                if (m.find()) {
                    Collection<Object> fieldValues = doc.getFieldValues(f);
                    for (Object v : fieldValues) {
                        try {
                            Object value = converter.invokeExact((String) v);
                            String key = Encoder.ALL_LETTERS.decode(m.group(1));
                            storeValue(map, key, value);
                            if (value instanceof Text) {
                                item.addText(new String[] {f}, (Text) value);
                            }
                        } catch (Error | Exception e) {
                            throw e;
                        } catch (Throwable t) {
                            throw new AssertionError(t);
                        }
                    }
                }
            }
            try {
                setter.invokeExact(item.getBean(), map);
            } catch (Error | Exception e) {
                throw e;
            } catch (Throwable t) {
                throw new AssertionError(t);
            }
        }

        protected void storeValue(Map<String, Object> target, String key, Object value) {
            target.put(key, value);
        }

        @Override
        public String toString() {
            return this.getClass().getName() + "[wildcardExpander: " + wildcardExpander + "]";
        }
    }

    private final static class MultiValueMapRelationProcessor<T> extends MapRelationProcessor<T> {
        private MultiValueMapRelationProcessor(MethodHandle setter, Pattern wildcardExpander, MethodHandle converter) {
            super(setter, wildcardExpander, converter);
        }

        @Override
        protected void storeValue(Map<String, Object> target, String key, Object value) {
            @SuppressWarnings("unchecked")
            List<Object> values = (List<Object>) target.get(key);
            if (values == null) {
                values = new ArrayList<>();
                target.put(key, values);
            }
            values.add(value);
        }
    }

    private final static class IDFieldProcessor<T> implements Processor<SolrDocument, ResultItem<T>> {
        private final String idField;

        IDFieldProcessor(String idField) {
            this.idField = idField;
        }

        @Override
        public void process(SolrDocument doc, ResultItem<T> item) {
            Object value = doc.getFieldValue(idField);
            item.setId(value);
        }
    }

    private static class MapFieldProcessor<T> implements Processor<SolrDocument, ResultItem<T>> {

        private final MethodHandle setter;
        private final MethodHandle converter;

        private final Pattern wildcardExpander;

        private MapFieldProcessor(MethodHandle setter, Pattern wildcardExpander, MethodHandle converter) {
            this.setter = setter;
            this.wildcardExpander = wildcardExpander;
            this.converter = converter.asType(methodType(Object.class, Object.class));
        }

        @Override
        public void process(SolrDocument doc, ResultItem<T> item) throws Exception {
            Map<String, Object> map = new HashMap<>();
            for (String f : doc.getFieldNames()) {
                Matcher m = wildcardExpander.matcher(f);
                if (m.find()) {
                    Collection<Object> fieldValues = doc.getFieldValues(f);
                    for (Object o : fieldValues) {
                        String key = Encoder.ALL_LETTERS.decode(m.group(1));
                        Object v;
                        try {
                            v = converter.invokeExact(o);
                        } catch (Error | Exception e) {
                            throw e;
                        } catch (Throwable t) {
                            throw new AssertionError(t);
                        }
                        storeValue(map, key, v);
                        if (v instanceof Text) {
                            item.addText(new String[] {f}, (Text) v);
                        }
                    }
                }
            }
            try {
                setter.invokeExact(item.getBean(), map);
            } catch (Error | Exception e) {
                throw e;
            } catch (Throwable t) {
                throw new AssertionError(t);
            }
        }

        protected void storeValue(Map<String, Object> target, String key, Object value) {
            target.put(key, value);
        }

        @Override
        public String toString() {
            return this.getClass().getSimpleName() + " [wildcardExpander: " + wildcardExpander + "]";
        }
    }

    private final static class MultiValueMapFieldProcessor<T> extends MapFieldProcessor<T> {
        private MultiValueMapFieldProcessor(MethodHandle setter, Pattern wildcardExpander, MethodHandle converter) {
            super(setter, wildcardExpander, converter);
        }

        @Override
        protected void storeValue(Map<String, Object> target, String key, Object value) {
            @SuppressWarnings("unchecked")
            List<Object> values = (List<Object>) target.get(key);
            if (values == null) {
                values = new ArrayList<>();
                target.put(key, values);
            }
            values.add(value);
        }
    }

    private static class SubTypeConfigurationFactory<U> extends TypeConfigurationFactory<U> {

        private final
        @Nullable
        Register registerAnnotation;
        private final String[] aliases;

        SubTypeConfigurationFactory(TypeConfiguration typeConfig, Instantiator instantiator, DeserializerBuilder deserializerBuilder, Prefix prefix, boolean useTexts, String[] aliases,
                                    Cardinality cardinality, @Nullable Register registerAnnotation, Set<String> processedCategories) {
            super(typeConfig, instantiator, deserializerBuilder, prefix, useTexts, cardinality, processedCategories);
            this.aliases = aliases;
            this.registerAnnotation = registerAnnotation;
        }

        @Override
        protected MethodHandles.Lookup getLookup() {
            return MethodHandles.lookup();
        }

        @Override
        void registerField(FieldDescription field, String name, String[] solrNames, Class<?> targetType, float boost) {
            if (registerAnnotation != null) {
                for (String f : registerAnnotation.fields()) {
                    if (f.equals(name)) {
                        registerForTypes(name, registerAnnotation, targetType, solrNames[0]);
                    }
                }
            }
            super.registerField(field, name, solrNames, targetType, boost);
        }

        /**
         * This is necessary to find the name for complex queries,
         */
        @AnnotationInterest(value = POSTPROCESSING, annotation = Name.class)
        @SuppressWarnings("unused")
        private void setNameField(Container configuration) {
            if (aliases.length == 0) {
                return;
            }
            String[] fieldNames = configuration.get(FIELD_NAMES);
            if (fieldNames.length == 0) {
                logger.warn("No field name in @Name annotation? Aliases are " + Arrays.toString(aliases));
            } else {
                getTypeConfig().registerField(fieldNames[0], aliases);
            }
        }

        <V> Processor<SolrDocument, ResultItem<V>> createRelationSettingProcessor(MethodHandle setter, Supplier<U> factory) {
            MethodHandle casted = makeObjectSetter(setter);
            return new RelationSettingProcessor<>(factory, casted);
        }

        private final class RelationSettingProcessor<V> implements Processor<SolrDocument, ResultItem<V>> {

            private final Supplier<U> factory;

            private final MethodHandle setter;

            private RelationSettingProcessor(Supplier<U> factory, MethodHandle setter) {
                this.factory = factory;
                this.setter = setter;
            }

            @Override
            public void process(SolrDocument doc, ResultItem<V> relationSource) throws Exception {
                // Check whether there is at least some referenced value
                Field idField = typeConfig.getFieldForType(Type.ID);
                while (idField != null) {
                    if (doc.containsKey(idField.getSolrName())) {
                        // Create the relation target
                        ResultItem<U> relationDest = relationSource.createChild(factory.get());
                        processor.process(doc, relationDest);
                        // Now set the relation value to its container
                        try {
                            setter.invokeExact(relationSource.getBean(), relationDest.getBean());
                        } catch (ClassCastException ex) {
                            logger.warn("Cannot cast " + this, ex);
                        } catch (Error | Exception e) {
                            throw e;
                        } catch (Throwable t) {
                            throw new AssertionError(t);
                        }

                        return;
                    }

                    idField = idField.getNext();
                }
            }

            @Override
            public String toString() {
                return this.getClass().getName() + "[factory: " + factory + "]";
            }
        }
    }

    private static class MainTypeConfigurationFactory<T> extends TypeConfigurationFactory<T> {

        MainTypeConfigurationFactory(TypeConfiguration<T> typeConfig, Instantiator instantiator) {
            super(typeConfig, instantiator);
        }

        ResultBuilder<T> createResultBuilder() {
            return getTypeConfig().createResultBuilder(processor);
        }

        @Override
        protected void postAnalyze(ModelDescription modelDescription) {
            super.postAnalyze(modelDescription);
            Field idField = typeConfig.getFieldForType(Type.REFERENCE_ID);
            if (idField == null) {
                throw new IllegalStateException("No ID field defined in " + typeConfig);
            }

            register(new IDFieldProcessor<>(idField.getSolrName()));
        }
    }
}
