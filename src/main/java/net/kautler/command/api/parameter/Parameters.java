/*
 * Copyright 2020 Björn Kautler
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

package net.kautler.command.api.parameter;

import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * A map-like container of parameter names and their associated values with optional implicit downcasts
 * for the values within all methods that handle the values. {@code null} values for parameters are not permitted,
 * if a value for a parameter is not present, this means the parameter was not given.
 *
 * <p>Parameters instances are immutable except if {@link #fixup(String, String)} is used. All other methods can not
 * change the contents of this container, that is the mappings themselves. The values can be mutable of course though.
 *
 * <p>There are no thread-safety guarantees if this container is modified using {@code fixup(...)}, so if you want to
 * use parameters instances on multiple threads, you either have to make sure you call {@code fixup(...)} in a
 * thread-safe manner by using external synchronization or you should not use {@code fixup(...)}. As long as that
 * method is not called, this container is immutable and thus can be used from multiple threads without synchronization.
 *
 * @param <V> the class of the values in this parameters instance
 */
public interface Parameters<V> {
    /**
     * Returns the value to which the specified parameter name is mapped with an optional implicit downcast,
     * or an empty {@link Optional} if this parameters instance contains no mapping for the name.
     *
     * <p>If the value of the parameter is of a subtype of {@link V}, the returned {@code Optional} can implicitly
     * be downcasted by using {@link R} to define the subtype using an explicit type parameter like with
     * <pre>{@code
     * Parameters<Object> parameters = parameterParser.parse(...);
     * parameters.<User>get("user");
     * }</pre>
     * or using implicit type inference like with
     * <pre>{@code
     * Parameters<Object> parameters = parameterParser.parse(...);
     * Optional<User> user = parameters.get("user");
     * }</pre>
     *
     * <p><b>Warning:</b> Be aware that choosing {@code R} must be done wisely as it is an unsafe operation. If you for
     * example select {@code String} for {@code R} and then try to get a {@code User} object from the returned
     * optional, you will get a {@link ClassCastException} at runtime.
     *
     * @param <R>           the class to which the value is implicitly downcasted
     * @param parameterName the parameter name whose associated value is to be returned
     * @return the value to which the specified parameter name is mapped
     */
    <R extends V> Optional<R> get(String parameterName);

    /**
     * Returns the value to which the specified parameter name is mapped with an optional implicit downcast,
     * or the given default value if this parameters instance contains no mapping for the name.
     *
     * <p>If the value of the parameter is of a subtype of {@link V}, the returned value can implicitly
     * be downcasted by using {@link R} to define the subtype using an explicit type parameter like with
     * <pre>{@code
     * Parameters<Object> parameters = parameterParser.parse(...);
     * parameters.<User>get("user", new UserSubClass());
     * }</pre>
     * or using implicit type inference like with
     * <pre>{@code
     * Parameters<Object> parameters = parameterParser.parse(...);
     * User user = parameters.get("user", (User) null);
     * }</pre>
     * or
     * <pre>{@code
     * Parameters<Object> parameters = parameterParser.parse(...);
     * User defaultUser = ...;
     * parameters.get("user", defaultUser);
     * }</pre>
     *
     * <p><b>Warning:</b> Be aware that choosing {@code R} must be done wisely as it is an unsafe operation.
     * If you for example select {@code String} for {@code R} and then try to get a {@code User} object,
     * you will get a {@link ClassCastException} at runtime.
     *
     * @param <R>           the class to which the value is implicitly downcasted
     * @param parameterName the parameter name whose associated value is to be returned
     * @param defaultValue  the default value to return if there is no mapping
     * @return the value to which the specified parameter name is mapped or the default value
     */
    <R extends V> R get(String parameterName, R defaultValue);

    /**
     * Returns the value to which the specified parameter name is mapped with an optional implicit downcast,
     * or a default value returned by the given supplier if this parameters instance contains no mapping for the name.
     *
     * <p>If the value of the parameter is of a subtype of {@link V}, the returned value can implicitly
     * be downcasted by using {@link R} to define the subtype using an explicit type parameter like with
     * <pre>{@code
     * Parameters<Object> parameters = parameterParser.parse(...);
     * parameters.<User>get("user", () -> null);
     * }</pre>
     * or using implicit type inference like with
     * <pre>{@code
     * Parameters<Object> parameters = parameterParser.parse(...);
     * User user = parameters.get("user", () -> null);
     * }</pre>
     * or
     * <pre>{@code
     * Parameters<Object> parameters = parameterParser.parse(...);
     * User defaultUser = ...;
     * parameters.get("user", () -> defaultUser);
     * }</pre>
     *
     * <p><b>Warning:</b> Be aware that choosing {@code R} must be done wisely as it is an unsafe operation.
     * If you for example select {@code String} for {@code R} and then try to get a {@code User} object,
     * you will get a {@link ClassCastException} at runtime.
     *
     * @param <R>                  the class to which the value is implicitly downcasted
     * @param parameterName        the parameter name whose associated value is to be returned
     * @param defaultValueSupplier the supplier for the default value to return if there is no mapping
     * @return the value to which the specified parameter name is mapped or the computed default value
     */
    <R extends V> R get(String parameterName, Supplier<R> defaultValueSupplier);

    /**
     * Returns the number of parameter-value mappings in this parameters instance.
     * If the parameters instance contains more than {@code Integer.MAX_VALUE} elements,
     * {@code Integer.MAX_VALUE} is returned.
     *
     * @return the number of parameter-value mappings in this parameters instance
     */
    int size();

    /**
     * Returns whether this parameters instance contains no parameter-value mappings.
     *
     * @return whether this parameters instance contains no parameter-value mappings
     */
    boolean isEmpty();

    /**
     * Returns whether this parameters instance contains a value for the given parameter name.
     *
     * @param parameterName the name of the parameter whose presence in this parameters instance is to be tested
     * @return whether this parameters instance contains a value for the given parameter name
     */
    boolean containsParameter(String parameterName);

    /**
     * Returns whether this parameters instance maps one or more parameter names to the given value.
     *
     * @param value the value whose presence in this parameters instance is to be tested
     * @return whether this parameters instance maps one or more parameter names to the given value
     */
    boolean containsValue(V value);

    /**
     * Returns an unmodifiable {@link Set} view of the parameter names contained in this parameters instance.
     *
     * <p>The set is backed by this parameters instance, so changes through {@link #fixup(String, String)}
     * are reflected in the set. If the parameters instance is modified while an iteration over the set is
     * in progress, the results of the iteration are undefined.
     *
     * @return an unmodifiable {@link Set} view of the parameter names
     */
    Set<String> getParameterNames();

    /**
     * Returns an unmodifiable {@link Collection} view of the values contained in this parameters instance
     * with an optional implicit downcast.
     *
     * <p>The collection is backed by this parameters instance, so changes through {@link #fixup(String, String)}
     * are reflected in the collection. If the parameters instance is modified while an iteration over the
     * collection is in progress, the results of the iteration are undefined.
     *
     * <p>If only a certain subtype of {@link V} is queried from the returned collection, or there is anyway only a
     * certain subtype of {@code V} present in this parameters instance, the returned collection can implicitly
     * downcast the values by using {@link R} to define the subtype using an explicit type parameter like with
     * <pre>{@code
     * Parameters<Object> parameters = parameterParser.parse(...);
     * String value = parameters.<String>getValues().iterator().next();
     * }</pre>
     * or using implicit type inference like with
     * <pre>{@code
     * Parameters<Object> parameters = parameterParser.parse(...);
     * Collection<String> values = parameters.getValues();
     * String value = values.iterator().next();
     * }</pre>
     *
     * <p><b>Warning:</b> Be aware that choosing {@code R} must be done wisely as it is an unsafe operation. If you for
     * example select {@code String} for {@code R} and then try to get a {@code User} object from the returned
     * collection, you will get a {@link ClassCastException} at runtime.
     *
     * @param <R> the class to which the values are implicitly downcasted
     * @return an unmodifiable {@link Collection} view of the values
     */
    <R extends V> Collection<R> getValues();

    /**
     * Returns an unmodifiable {@link Set} view of the mapping entries contained in this parameters instance
     * with an optional implicit downcast for the value.
     *
     * <p>The set is backed by this parameters instance, so changes through {@link #fixup(String, String)}
     * are reflected in the set. If the parameters instance is modified while an iteration over the set
     * is in progress the results of the iteration are undefined.
     *
     * <p>If only a certain subtype of {@link V} is queried from the entries in the returned set, or there is anyway
     * only a certain subtype of {@code V} present in this parameters instance, the returned set can implicitly
     * downcast the values by using {@link R} to define the subtype using an explicit type parameter like with
     * <pre>{@code
     * Parameters<Object> parameters = parameterParser.parse(...);
     * Entry<String, String> entry = parameters.<String>getEntries().iterator().next();
     * }</pre>
     * or using implicit type inference like with
     * <pre>{@code
     * Parameters<Object> parameters = parameterParser.parse(...);
     * Set<Entry<String, String>> entries = parameters.getEntries();
     * Entry<String, String> entry = entries.iterator().next();
     * }</pre>
     *
     * <p><b>Warning:</b> Be aware that choosing {@code R} must be done wisely as it is an unsafe operation. If you for
     * example select {@code String} for {@code R} and then try to get a {@code User} object from one of the returned
     * entries, you will get a {@link ClassCastException} at runtime.
     *
     * @param <R> the class to which the values are implicitly downcasted
     * @return an unmodifiable {@link Set} view of the mapping entries
     */
    <R extends V> Set<Entry<String, R>> getEntries();

    /**
     * Performs the given action for each entry in this parameters instance with an optional implicit downcast
     * for the value until all entries have been processed or the action throws an exception.
     * Exceptions thrown by the action are relayed to the caller.
     *
     * <p>This method is atomic in so far, that modifications via {@link #fixup(String, String)} are prevented while
     * a call to this method is processed and besides that method this class is immutable.
     *
     * <p>If there is anyway only a certain subtype of {@link V} present in this parameters instance, this method
     * can implicitly downcast the values by using {@link R} to define the subtype using an explicit type parameter
     * like with
     * <pre>{@code
     * Parameters<Object> parameters = parameterParser.parse(...);
     * parameters.<String>forEach((name, value) -> value.intern());
     * }</pre>
     * or using implicit type inference like with
     * <pre>{@code
     * Parameters<Object> parameters = parameterParser.parse(...);
     * parameters.forEach((String name, String value) -> value.intern());
     * }</pre>
     *
     * <p><b>Warning:</b> Be aware that choosing {@code R} must be done wisely as it is an unsafe operation. If you for
     * example select {@code String} for {@code R} and then a {@code User} object is processed, you will get a
     * {@link ClassCastException} at runtime.
     *
     * @param action The action to be performed for each entry
     * @param <R>    the class to which the values are implicitly downcasted
     */
    <R extends V> void forEach(BiConsumer<? super String, ? super R> action);

    /**
     * If you for example have
     * <ul>
     *    <li>an optional placeholder followed by an optional literal like in {@code [<placeholder>] ['literal']} or
     *    <li>alternatively a placeholder or literal like in {@code (<placeholder> | 'literal')}
     * </ul>
     * and a user invokes the command with only the parameter {@code literal}, it could fit in both parameter slots.
     * You have to decide yourself in which slot it belongs. For cases where the literal parameter can never be meant
     * for the placeholder, you can use this method to correct this parameters instance for the two given parameters.
     *
     * <p>This method checks whether the literal parameter is absent and the placeholder parameter has the literal
     * parameter as value. If this is the case, the placeholder parameter is removed and the literal parameter is
     * added instead.
     *
     * <p>Calling this method while a {@link #forEach(BiConsumer)} call is being processed will result in a
     * {@link ConcurrentModificationException} being thrown by this method.
     *
     * @param placeholderName the name of the placeholder parameter
     * @param literalName     the name of the literal parameter
     * @throws ConcurrentModificationException if this method is called while a {@link #forEach(BiConsumer)}
     *                                         call is being processed
     */
    void fixup(String placeholderName, String literalName);

    /**
     * Returns this parameters instance with an optional implicit downcast for the values.
     *
     * <p>If only a certain subtype of {@link V} is queried from the parameters instance, or there is anyway only a
     * certain subtype of {@code V} present, the returned reference can implicitly downcast the values by using
     * {@link R} to define the subtype using an explicit type parameter like with
     * <pre>{@code
     * Parameters<Object> parameters = parameterParser.parse(...);
     * Optional<String> value = parameters.<String>getParameters().get("placeholder");
     * }</pre>
     * or using implicit type inference like with
     * <pre>{@code
     * Parameters<Object> parameters = parameterParser.parse(...);
     * Parameters<String> stringParameters = parameters.getParameters();
     * }</pre>
     *
     * <p><b>Warning:</b> Be aware that choosing {@code R} must be done wisely as it is an unsafe operation. If you for
     * example select {@code String} for {@code R} and then try to get a {@code User} object from the returned
     * parameters instance, you will get a {@link ClassCastException} at runtime.
     *
     * @param <R> the class to which the values are implicitly downcasted
     * @return this parameters instance
     */
    <R extends V> Parameters<R> getParameters();

    /**
     * Returns an unmodifiable {@link Map} view of this parameters instance with an optional implicit downcast for the
     * values.
     *
     * <p>The map is backed by this parameters instance, so changes through {@link #fixup(String, String)}
     * are reflected in the map. If the parameters instance is modified while an iteration over the map
     * is in progress the results of the iteration are undefined.
     *
     * <p>If only a certain subtype of {@link V} is queried from the map, or there is anyway only a certain subtype of
     * {@code V} present in this parameters instance, the returned map can implicitly downcast the values by using
     * {@link R} to define the subtype using an explicit type parameter like with
     * <pre>{@code
     * Parameters<Object> parameters = parameterParser.parse(...);
     * String value = parameters.<String>getAsMap().values().iterator().next();
     * }</pre>
     * or using implicit type inference like with
     * <pre>{@code
     * Parameters<Object> parameters = parameterParser.parse(...);
     * Map<String, String> parameterMap = parameters.getAsMap();
     * }</pre>
     *
     * <p><b>Warning:</b> Be aware that choosing {@code R} must be done wisely as it is an unsafe operation. If you for
     * example select {@code String} for {@code R} and then try to get a {@code User} object from the returned map,
     * you will get a {@link ClassCastException} at runtime.
     *
     * @param <R> the class to which the values are implicitly downcasted
     * @return an unmodifiable {@link Map} view of this parameters instance
     */
    <R extends V> Map<String, R> getAsMap();
}
