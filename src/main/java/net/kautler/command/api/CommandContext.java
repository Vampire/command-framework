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

package net.kautler.command.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * The context of a command triggered by messages of the given type. At various places in the processing some fields
 * might be set and others not.
 *
 * <p>This class is thread-safe as it is immutable and the additional data is stored in a thread-safe container.
 * Any method that modifies a value - except for additional data - produces a new instance.
 *
 * @param <M> the class of the messages for which this command context is used
 */
public class CommandContext<M> {
    /**
     * The message that triggered the command processing.
     */
    private final M message;

    /**
     * The content of the message that triggered the command processing.
     */
    private final String messageContent;

    /**
     * The prefix that the message has to start with to trigger a command.
     */
    private final String prefix;

    /**
     * The alias of the triggered command.
     */
    private final String alias;

    /**
     * The parameter string to be processed by the triggered command.
     */
    private final String parameterString;

    /**
     * The command that is triggered.
     */
    private final Command<? super M> command;

    /**
     * The map that backs the additional data.
     */
    private final Map<String, Object> additionalData = new ConcurrentHashMap<>();

    /**
     * Constructs a new command context from the given builder.
     *
     * @param builder the message that triggered the command processing
     */
    private CommandContext(Builder<M> builder) {
        message = requireNonNull(builder.message);
        messageContent = requireNonNull(builder.messageContent);
        prefix = builder.prefix;
        alias = builder.alias;
        parameterString = builder.parameterString;
        command = builder.command;
        additionalData.putAll(builder.additionalData);
    }

    /**
     * Returns the message that triggered the command processing.
     *
     * @return the message that triggered the command processing
     */
    public M getMessage() {
        return message;
    }

    /**
     * Returns a builder that will create a new command context based on this instance with the given message.
     *
     * @param message the message that triggered the command processing
     * @return a builder that will create a new command context based on this instance with the given message
     */
    public Builder<M> withMessage(M message) {
        return new Builder<>(this).withMessage(message);
    }

    /**
     * Returns the content of the message that triggered the command processing.
     *
     * @return the content of the message that triggered the command processing
     */
    public String getMessageContent() {
        return messageContent;
    }

    /**
     * Returns a builder that will create a new command context based on this instance with the given message content.
     *
     * @param messageContent the content of the message that triggered the command processing
     * @return a builder that will create a new command context based on this instance with the given message content
     */
    public Builder<M> withMessageContent(String messageContent) {
        return new Builder<>(this).withMessageContent(messageContent);
    }

    /**
     * Returns the prefix that the message has to start with to trigger a command.
     *
     * @return the prefix that the message has to start with to trigger a command
     */
    public Optional<String> getPrefix() {
        return Optional.ofNullable(prefix);
    }

    /**
     * Returns a builder that will create a new command context based on this instance with the given prefix.
     *
     * @param prefix the prefix that the message has to start with to trigger a command
     * @return a builder that will create a new command context based on this instance with the given prefix
     */
    public Builder<M> withPrefix(String prefix) {
        return new Builder<>(this).withPrefix(prefix);
    }

    /**
     * Returns the alias of the triggered command.
     *
     * @return the alias of the triggered command
     */
    public Optional<String> getAlias() {
        return Optional.ofNullable(alias);
    }

    /**
     * Returns a builder that will create a new command context based on this instance with the given alias.
     *
     * @param alias the alias of the triggered command
     * @return a builder that will create a new command context based on this instance with the given alias
     */
    public Builder<M> withAlias(String alias) {
        return new Builder<>(this).withAlias(alias);

    }

    /**
     * Returns the parameter string to be processed by the triggered command.
     *
     * @return the parameter string to be processed by the triggered command
     */
    public Optional<String> getParameterString() {
        return Optional.ofNullable(parameterString);
    }

    /**
     * Returns a builder that will create a new command context based on this instance with the given parameter string.
     *
     * @param parameterString the parameter string to be processed by the triggered command
     * @return a builder that will create a new command context based on this instance with the given parameter string
     */
    public Builder<M> withParameterString(String parameterString) {
        return new Builder<>(this).withParameterString(parameterString);
    }

    /**
     * Returns the command that is triggered.
     *
     * @return the command that is triggered
     */
    public Optional<Command<? super M>> getCommand() {
        return Optional.ofNullable(command);
    }

    /**
     * Returns a builder that will create a new command context based on this instance with the given command.
     *
     * @param command the command that is triggered
     * @return a builder that will create a new command context based on this instance with the given command
     */
    public Builder<M> withCommand(Command<? super M> command) {
        return new Builder<>(this).withCommand(command);
    }

    /**
     * Returns the additional data value to which the specified key is mapped with an optional implicit downcast,
     * or an empty {@link Optional} if this command context contains no additional data for the key.
     *
     * <p>The returned {@code Optional} can implicitly be downcasted by using {@link R} to define the type
     * using an explicit type parameter like with
     * <pre>{@code
     * commandContext.<User>getAdditionalData("user");
     * }</pre>
     * or using implicit type inference like with
     * <pre>{@code
     * Optional<User> user = commandContext.getAdditionalData("user");
     * }</pre>
     *
     * <p><b>Warning:</b> Be aware that choosing {@code R} must be done wisely as it is an unsafe operation. If you for
     * example select {@code String} for {@code R} and then try to get a {@code User} object from the returned
     * optional, you will get a {@link ClassCastException} at runtime.
     *
     * @param key the additional data key whose associated value is to be returned
     * @param <R> the class to which the value is implicitly downcasted
     * @return the value to which the specified additional data key is mapped
     */
    public <R> Optional<R> getAdditionalData(String key) {
        return Optional.ofNullable(getAdditionalData(key, (R) null));
    }

    /**
     * Returns the additional data value to which the specified key is mapped with an optional implicit downcast,
     * or the given default value if this command context contains no additional data for the key.
     *
     * <p>The returned value can implicitly be downcasted by using {@link R} to define the type
     * using an explicit type parameter like with
     * <pre>{@code
     * commandContext.<User>getAdditionalData("user", new UserSubClass());
     * }</pre>
     * or using implicit type inference like with
     * <pre>{@code
     * User user = commandContext.getAdditionalData("user", (User) null);
     * }</pre>
     * or
     * <pre>{@code
     * User defaultUser = ...;
     * commandContext.getAdditionalData("user", defaultUser);
     * }</pre>
     *
     * <p><b>Warning:</b> Be aware that choosing {@code R} must be done wisely as it is an unsafe operation.
     * If you for example select {@code String} for {@code R} and then try to get a {@code User} object,
     * you will get a {@link ClassCastException} at runtime.
     *
     * @param key          the additional data key whose associated value is to be returned
     * @param defaultValue the default value to return if there is no mapping
     * @param <R>          the class to which the value is implicitly downcasted
     * @return the value to which the specified additional data key is mapped or the default value
     */
    @SuppressWarnings("unchecked")
    public <R> R getAdditionalData(String key, R defaultValue) {
        return ((Map<String, R>) additionalData)
                .getOrDefault(key, defaultValue);
    }

    /**
     * Returns the additional data value to which the specified key is mapped with an optional implicit downcast,
     * or a default value returned by the given supplier if this command context contains no additional data
     * for the key.
     *
     * <p>The returned value can implicitly be downcasted by using {@link R} to define the type
     * using an explicit type parameter like with
     * <pre>{@code
     * commandContext.<User>getAdditionalData("user", () -> null);
     * }</pre>
     * or using implicit type inference like with
     * <pre>{@code
     * User user = commandContext.getAdditionalData("user", () -> null);
     * }</pre>
     * or
     * <pre>{@code
     * User defaultUser = ...;
     * commandContext.getAdditionalData("user", () -> defaultUser);
     * }</pre>
     *
     * <p><b>Warning:</b> Be aware that choosing {@code R} must be done wisely as it is an unsafe operation.
     * If you for example select {@code String} for {@code R} and then try to get a {@code User} object,
     * you will get a {@link ClassCastException} at runtime.
     *
     * @param key                  the additional data key whose associated value is to be returned
     * @param defaultValueSupplier the supplier for the default value to return if there is no mapping
     * @param <R>                  the class to which the value is implicitly downcasted
     * @return the value to which the specified additional data key is mapped or the computed default value
     */
    public <R> R getAdditionalData(String key, Supplier<R> defaultValueSupplier) {
        return this
                .<R>getAdditionalData(key)
                .orElseGet(defaultValueSupplier);
    }

    /**
     * Sets the additional data for the given key to the given value. If the additional data key was mapped to a value
     * previously, the old value is returned, otherwise an empty {@link Optional} is returned.
     *
     * <p>The returned value can implicitly be downcasted by using {@link R} to define the type
     * using an explicit type parameter like with
     * <pre>{@code
     * commandContext.<User>setAdditionalData("user", user);
     * }</pre>
     * or using implicit type inference like with
     * <pre>{@code
     * Optional<User> user = commandContext.setAdditionalData("user", user);
     * }</pre>
     *
     * <p><b>Warning:</b> Be aware that choosing {@code R} must be done wisely as it is an unsafe operation.
     * If you for example select {@code String} for {@code R} and then try to get a {@code User} object,
     * you will get a {@link ClassCastException} at runtime.
     *
     * @param key   the additional data key whose value is to be set
     * @param value the value to set for the given key
     * @param <R>   the class to which the returned old value is implicitly downcasted
     * @return the value to which the specified additional data key was mapped previously or an empty {@code Optional}
     */
    @SuppressWarnings("unchecked")
    public <R> Optional<R> setAdditionalData(String key, Object value) {
        return Optional.ofNullable((R) additionalData.put(key, value));
    }

    /**
     * Removes the additional data for the given key. If the additional data key was mapped to a value previously,
     * the removed value is returned, otherwise an empty {@link Optional} is returned.
     *
     * <p>The returned value can implicitly be downcasted by using {@link R} to define the type
     * using an explicit type parameter like with
     * <pre>{@code
     * commandContext.<User>removeAdditionalData("user");
     * }</pre>
     * or using implicit type inference like with
     * <pre>{@code
     * Optional<User> user = commandContext.removeAdditionalData("user");
     * }</pre>
     *
     * <p><b>Warning:</b> Be aware that choosing {@code R} must be done wisely as it is an unsafe operation.
     * If you for example select {@code String} for {@code R} and then try to get a {@code User} object,
     * you will get a {@link ClassCastException} at runtime.
     *
     * @param key the additional data key whose value is to be removed
     * @param <R> the class to which the returned value is implicitly downcasted
     * @return the value to which the specified additional data key was mapped previously or an empty {@code Optional}
     */
    @SuppressWarnings("unchecked")
    public <R> Optional<R> removeAdditionalData(String key) {
        return Optional.ofNullable((R) additionalData.remove(key));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if ((obj == null) || (getClass() != obj.getClass())) {
            return false;
        }
        CommandContext<?> that = (CommandContext<?>) obj;
        return message.equals(that.message) &&
                messageContent.equals(that.messageContent) &&
                Objects.equals(prefix, that.prefix) &&
                Objects.equals(alias, that.alias) &&
                Objects.equals(parameterString, that.parameterString) &&
                Objects.equals(command, that.command) &&
                additionalData.equals(that.additionalData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(message, messageContent, prefix, alias, parameterString, command, additionalData);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", CommandContext.class.getSimpleName() + "[", "]")
                .add("message=" + message)
                .add("messageContent='" + messageContent + "'")
                .add("prefix='" + prefix + "'")
                .add("alias='" + alias + "'")
                .add("parameterString='" + parameterString + "'")
                .add("command=" + command)
                .add("additionalData=" + additionalData)
                .toString();
    }

    /**
     * A builder to build command context instances.
     * This builder is not thread-safe.
     *
     * @param <M> the class of the messages for which the built command contexts are used
     */
    public static class Builder<M> {
        /**
         * The message that triggered the command processing.
         */
        private M message;

        /**
         * The content of the message that triggered the command processing.
         */
        private String messageContent;

        /**
         * The prefix that the message has to start with to trigger a command.
         */
        private String prefix;

        /**
         * The alias of the triggered command.
         */
        private String alias;

        /**
         * The parameter string to be processed by the triggered command.
         */
        private String parameterString;

        /**
         * The command that is triggered.
         */
        private Command<? super M> command;

        /**
         * The map that backs the additional data.
         */
        private final Map<String, Object> additionalData = new HashMap<>();

        /**
         * Constructs a new command context builder with the given message and message content.
         *
         * @param message        the message that triggered the command processing
         * @param messageContent the content of the message that triggered the command processing
         */
        public Builder(M message, String messageContent) {
            this.message = requireNonNull(message);
            this.messageContent = requireNonNull(messageContent);
        }

        /**
         * Constructs a new command context builder with the same values as the given command context.
         *
         * @param commandContext the command context used to initialize the builder
         */
        private Builder(CommandContext<M> commandContext) {
            message = requireNonNull(commandContext.message);
            messageContent = requireNonNull(commandContext.messageContent);
            prefix = commandContext.prefix;
            alias = commandContext.alias;
            parameterString = commandContext.parameterString;
            command = commandContext.command;
            commandContext.additionalData.keySet().forEach(Objects::requireNonNull);
            commandContext.additionalData.values().forEach(Objects::requireNonNull);
            additionalData.putAll(commandContext.additionalData);
        }

        /**
         * Sets the message that triggered the command processing and returns this builder for method call chaining.
         *
         * @param message the message that triggered the command processing
         * @return this builder
         */
        public Builder<M> withMessage(M message) {
            this.message = requireNonNull(message);
            return this;
        }

        /**
         * Sets the content of the message that triggered the command processing and returns this builder
         * for method call chaining.
         *
         * @param messageContent the content of the message that triggered the command processing
         * @return this builder
         */
        public Builder<M> withMessageContent(String messageContent) {
            this.messageContent = requireNonNull(messageContent);
            return this;
        }

        /**
         * Sets the prefix that the message has to start with to trigger a command and returns this builder
         * for method call chaining.
         *
         * @param prefix the prefix that the message has to start with to trigger a command
         * @return this builder
         */
        public Builder<M> withPrefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        /**
         * Sets the alias of the triggered command and returns this builder for method call chaining.
         *
         * @param alias the alias of the triggered command
         * @return this builder
         */
        public Builder<M> withAlias(String alias) {
            this.alias = alias;
            return this;
        }

        /**
         * Sets the parameter string to be processed by the triggered command and returns this builder
         * for method call chaining.
         *
         * @param parameterString the parameter string to be processed by the triggered command
         * @return this builder
         */
        public Builder<M> withParameterString(String parameterString) {
            this.parameterString = parameterString;
            return this;
        }

        /**
         * Sets the command that is triggered and returns this builder for method call chaining.
         *
         * @param command the command that is triggered
         * @return this builder
         */
        public Builder<M> withCommand(Command<? super M> command) {
            this.command = command;
            return this;
        }

        /**
         * Sets the additional data for the given key to the given value and returns this builder
         * for method call chaining.
         *
         * @param key   the additional data key whose value is to be set
         * @param value the value to set for the given key
         * @return this builder
         */
        public Builder<M> withAdditionalData(String key, Object value) {
            additionalData.put(requireNonNull(key), requireNonNull(value));
            return this;
        }

        /**
         * Removes the additional data for the given key and returns this builder for method call chaining.
         *
         * @param key the additional data key whose value is to be removed
         * @return this builder
         */
        public Builder<M> withoutAdditionalData(String key) {
            additionalData.remove(key);
            return this;
        }

        /**
         * Removes all additional data and returns this builder for method call chaining.
         *
         * @return this builder
         */
        public Builder<M> withoutAdditionalData() {
            additionalData.clear();
            return this;
        }

        /**
         * Returns a new command context instance from the current values. Calling this method multiple times creates a
         * new command context instance each time with the current values. Modifying this builder after calling this
         * method does not influence previously built command context instances.
         *
         * @return a new command context instance from the current values
         */
        public CommandContext<M> build() {
            return new CommandContext<>(this);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if ((obj == null) || (getClass() != obj.getClass())) {
                return false;
            }
            Builder<?> builder = (Builder<?>) obj;
            return message.equals(builder.message) &&
                    messageContent.equals(builder.messageContent) &&
                    Objects.equals(prefix, builder.prefix) &&
                    Objects.equals(alias, builder.alias) &&
                    Objects.equals(parameterString, builder.parameterString) &&
                    Objects.equals(command, builder.command) &&
                    additionalData.equals(builder.additionalData);
        }

        @Override
        public int hashCode() {
            return Objects.hash(message, messageContent, prefix, alias, parameterString, command, additionalData);
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", Builder.class.getSimpleName() + "[", "]")
                    .add("message=" + message)
                    .add("messageContent='" + messageContent + "'")
                    .add("prefix='" + prefix + "'")
                    .add("alias='" + alias + "'")
                    .add("parameterString='" + parameterString + "'")
                    .add("command=" + command)
                    .add("additionalData=" + additionalData)
                    .toString();
        }
    }
}
