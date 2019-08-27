/*
 * Copyright 2019 Björn Kautler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.kautler.test.pitest;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.experimental.categories.Category;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.pitest.junit.DescriptionFilter;
import org.pitest.junit.adapter.AdaptedJUnitTestUnit;
import org.pitest.reflection.IsAnnotatedWith;
import org.pitest.testapi.TestGroupConfig;
import org.pitest.testapi.TestUnit;
import org.pitest.testapi.TestUnitFinder;
import org.spockframework.runtime.model.FeatureMetadata;
import spock.lang.Shared;
import spock.lang.Stepwise;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.Arrays.asList;
import static java.util.Collections.disjoint;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.junit.runner.Description.createTestDescription;
import static org.pitest.functional.FCollection.contains;
import static org.pitest.functional.FCollection.flatMap;
import static org.pitest.functional.FCollection.map;
import static org.pitest.reflection.Reflection.allMethods;
import static org.pitest.reflection.Reflection.publicFields;

/**
 * A Spock test unit finder.
 */
public class SpockTestUnitFinder implements TestUnitFinder {
    /**
     * The test group config.
     */
    private final TestGroupConfig config;

    /**
     * Runners that should be excluded.
     */
    private final Collection<String> excludedRunners;

    /**
     * Test methods that should be included.
     */
    private final Collection<String> includedTestMethods;

    /**
     * Constructs a new spock test unit finder.
     *
     * @param config              the test group config
     * @param excludedRunners     runners that should be excluded
     * @param includedTestMethods test methods that should be included
     */
    public SpockTestUnitFinder(TestGroupConfig config,
                               Collection<String> excludedRunners,
                               Collection<String> includedTestMethods) {
        this.config = config;
        this.excludedRunners = excludedRunners;
        this.includedTestMethods = includedTestMethods;
    }

    @Override
    public List<TestUnit> findTestUnits(Class<?> clazz) {
        Runner runner = AdaptedJUnitTestUnit.createRunner(clazz);

        if ((runner == null) || isExcluded(runner) || !isIncluded(clazz)) {
            return emptyList();
        }

        if (shouldTreatAsOneUnit(clazz)) {
            return singletonList(new AdaptedJUnitTestUnit(clazz, Optional.empty()));
        } else {
            List<TestUnit> filteredUnits = splitIntoFilteredUnits(clazz);
            return filterUnitsByMethod(filteredUnits);
        }
    }

    private List<TestUnit> filterUnitsByMethod(List<TestUnit> filteredUnits) {
        if (this.includedTestMethods.isEmpty()) {
            return filteredUnits;
        }

        List<TestUnit> units = new ArrayList<>();
        for (TestUnit unit : filteredUnits) {
            if (this.includedTestMethods.contains(
                    unit.getDescription().getName().split("\\(")[0])) {
                units.add(unit);
            }
        }
        return units;
    }

    private boolean isExcluded(Runner runner) {
        return excludedRunners.contains(runner.getClass().getName());
    }

    private boolean isIncluded(Class<?> clazz) {
        return isIncludedCategory(clazz) && !isExcludedCategory(clazz);
    }

    private boolean isIncludedCategory(Class<?> clazz) {
        List<String> included = this.config.getIncludedGroups();
        return included.isEmpty() || !disjoint(included, getCategories(clazz));
    }

    private boolean isExcludedCategory(Class<?> clazz) {
        List<String> excluded = this.config.getExcludedGroups();
        return !excluded.isEmpty() && !disjoint(excluded, getCategories(clazz));
    }

    private List<String> getCategories(Class<?> clazz) {
        Category category = clazz.getAnnotation(Category.class);
        return flatMap(singletonList(category), toCategoryNames());
    }

    private Function<Category, Iterable<String>> toCategoryNames() {
        return category -> {
            if (category == null) {
                return emptyList();
            }
            return map(asList(category.value()), Class::getName);
        };
    }

    private boolean shouldTreatAsOneUnit(Class<?> clazz) {
        Set<Method> methods = allMethods(clazz);
        return hasAnnotation(methods, BeforeClass.class)
                || hasAnnotation(methods, AfterClass.class)
                || hasAnnotation(clazz, Stepwise.class)
                || hasMethodNamed(methods, "setupSpec")
                || hasMethodNamed(methods, "cleanupSpec")
                || hasSharedField(clazz)
                || hasClassRuleAnnotations(clazz, methods);
    }

    private boolean hasSharedField(Class<?> clazz) {
        return hasAnnotation(allFields(clazz), Shared.class);
    }

    private static Set<Field> allFields(Class<?> clazz) {
        Set<Field> fields = new LinkedHashSet<>();
        if (clazz != null) {
            fields.addAll(asList(clazz.getDeclaredFields()));
            fields.addAll(allFields(clazz.getSuperclass()));
        }
        return fields;
    }

    private boolean hasMethodNamed(Set<Method> methods, String methodName) {
        return contains(methods, havingName(methodName));
    }

    private Predicate<Method> havingName(String methodName) {
        return method -> method.getName().equals(methodName);
    }

    private boolean hasClassRuleAnnotations(Class<?> clazz, Set<Method> methods) {
        return hasAnnotation(methods, ClassRule.class)
                || hasAnnotation(publicFields(clazz), ClassRule.class);
    }

    private boolean hasAnnotation(final AnnotatedElement annotatedElement,
                                  Class<? extends Annotation> annotation) {
        return annotatedElement.isAnnotationPresent(annotation);
    }

    private boolean hasAnnotation(Set<? extends AccessibleObject> methods,
                                  Class<? extends Annotation> annotation) {
        return contains(methods, IsAnnotatedWith.instance(annotation));
    }

    private List<TestUnit> splitIntoFilteredUnits(Class<?> clazz) {
        return allMethods(clazz)
                .stream()
                .map(method -> method.getAnnotation(FeatureMetadata.class))
                .filter(Objects::nonNull)
                .map(FeatureMetadata::name)
                .map(featureName -> new AdaptedJUnitTestUnit(
                        clazz,
                        Optional.of(createFilterFor(clazz, featureName))))
                .collect(toList());
    }

    private Filter createFilterFor(Class<?> clazz, String featureName) {
        return new DescriptionFilter(createTestDescription(clazz, featureName).toString());
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", SpockTestUnitFinder.class.getSimpleName() + "[", "]")
                .add("config=" + config)
                .add("excludedRunners=" + excludedRunners)
                .add("includedTestMethods=" + includedTestMethods)
                .toString();
    }
}
