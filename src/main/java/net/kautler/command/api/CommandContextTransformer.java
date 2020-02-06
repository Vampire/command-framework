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

import net.kautler.command.api.restriction.Restriction;

import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Qualifier;
import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * A transformer that can transform a command context in a given {@link Phase phase} or multiple ones.
 * Implementations need to be annotated with one or more {@link InPhase @InPhase} annotations to specify
 * in which phases the transformer should be called. Only one transformer per phase that can be applied
 * to a given message framework is supported currently.
 *
 * <p>Possible use-cases for example include:
 * <ul>
 *     <li>
 *         custom prefix computation for example based on the server of the message
 *     </li>
 *     <li>
 *         fuzzy-searching for mistyped aliases and their automatic correction
 *         (this could also be used for just a "did you mean X" response,
 *         but for that the command not found events are probably better suited)
 *     </li>
 *     <li>
 *         having a command that forwards to one command in one channel
 *         but to another command in another channel,
 *         like {@code !player} that forwards to {@code !mc:player} in an MC channel
 *         but to {@code !s4:player} in an S4 channel
 *     </li>
 *     <li>
 *         supporting something like {@code !runas @other-user foo bar baz},
 *         where this transformer will transform that to alias {@code foo}
 *         and parameter string {@code bar baz} and then a custom {@link Restriction}
 *         can check whether the message author has the permissions to use {@code !runas}
 *         and then for example whether the {@code other-user} would have permissions
 *         for the {@code foo} command and only then allow it to proceed
 *     </li>
 *     <li>
 *         forwarding to a {@code !help} command if an unknown command was issued
 *     </li>
 *     <li>
 *         having a custom command registry for example with dynamically created
 *         command instances
 *     </li>
 * </ul>
 *
 * @param <M> the class of the messages for which this transformer can be triggered
 * @see Phase
 */
public interface CommandContextTransformer<M> {
    /**
     * The phases during which a command context transformer is called if one is provided for the given phase.
     * The phases can be skipped if a former phase already provides the result of a later phase,
     * or a phase causes a command not found event being fired which will stop processing completely.
     *
     * <p>The five main phases that are handled are in order initialization, prefix computation,
     * alias and parameter string computation, command computation, and command execution.
     * For all but the first and last, there is a before and an after sub phase each
     * during which the command context transformer is called.
     *
     * <p>If at the end of the initialization phase, or any before / after sub phases the command is set
     * in the context, processing is fast forwarded immediately to the command execution phase and all other
     * inbetween phases and sub phases are skipped.
     *
     * <p>If at the end of the initialization phase, or any before / after sub phases before the
     * {@code BEFORE_COMMAND_COMPUTATION} sub phase, the alias is set in the context, processing is
     * fast forwarded immediately to the before command computation sub phase and all other inbetween
     * phases and sub phases are skipped.
     *
     * <p>If at the end of the initialization phase, or any before / after sub phases before the
     * {@code BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION} sub phase, the prefix is set in the context,
     * processing is fast forwarded immediately to the before alias and parameter string computation
     * sub phase and all other inbetween phases and sub phases are skipped.
     */
    enum Phase {
        /**
         * At the start of this phase, usually only the message, and message content are set.
         */
        BEFORE_PREFIX_COMPUTATION,

        /**
         * At the start of this phase, usually only the message, message content, and prefix are set.
         *
         * <p>If at the end of this phase no fast forward was done and no prefix is set,
         * a command not found event is being fired and processing stops completely.
         */
        AFTER_PREFIX_COMPUTATION,

        /**
         * At the start of this phase, usually only the message, message content, and prefix are set.
         *
         * <p>If at the end of this phase no fast forward was done and no prefix is set,
         * a command not found event is being fired and processing stops completely.
         *
         * <p>If at the end of this phase a prefix is set and it does not match the start of the message content,
         * the message is ignored and processing stops completely. This is the only way to stop processing cleanly
         * without getting a command not found event fired. This can also be achieved by fast forwarding to this
         * phase by setting the prefix in an earlier phase and not doing anything in this phase actually,
         * or not even registering for it.
         */
        BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION,

        /**
         * At the start of this phase, usually only the message, message content, prefix, alias, and parameter string
         * are set.
         *
         * <p>If at the end of this phase no fast forward was done and no alias is set,
         * a command not found event is being fired and processing stops completely.
         */
        AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION,

        /**
         * At the start of this phase, usually only the message, message content, prefix, alias, and parameter string
         * are set.
         *
         * <p>If at the end of this phase no fast forward was done and no alias is set,
         * a command not found event is being fired and processing stops completely.
         */
        BEFORE_COMMAND_COMPUTATION,

        /**
         * At the start of this phase, usually the command context is fully populated.
         *
         * <p>If at the end of this phase no command is set,
         * a command not found event is being fired and processing stops completely.
         */
        AFTER_COMMAND_COMPUTATION
    }

    /**
     * Transforms the given command context in the given {@link Phase phase}. The fields of the given command context
     * can be empty depending on the current phase. The fields that are set in the returned command context can cause
     * future phases to be skipped if the according data is already computed in an earlier phase. The description of
     * the single phases specifies which fields in the given command context should be set and which fields in the
     * returned command context should be set and the respective effect that is caused by this.
     * The returned value must not be {@code null}.
     *
     * @param commandContext the command context to be transformed, usually populated
     *                       according to the phase description, but not necessarily
     * @param phase          the phase this transformer is currently called in
     * @param <T>            the class of the messages for which this transformer is triggered
     * @return the transformed command context
     * @see Phase
     */
    <T extends M> CommandContext<T> transform(CommandContext<T> commandContext, Phase phase);

    /**
     * A CDI qualifier that is used for defining the phase in which a command context transformer should be called.
     */
    @Retention(RUNTIME)
    @Target({ TYPE, FIELD, METHOD, PARAMETER })
    @Documented
    @Repeatable(InPhases.class)
    @Qualifier
    @interface InPhase {
        /**
         * Returns the phase in which the annotated command context transformer should be called.
         *
         * @return the phase in which the annotated command context transformer should be called
         */
        Phase value();

        /**
         * An annotation literal for programmatic CDI lookup.
         */
        class Literal extends AnnotationLiteral<InPhase> implements InPhase {
            /**
             * The serial version UID of this class.
             */
            private static final long serialVersionUID = 1;

            /**
             * The phase in which a command context transformer should be called.
             */
            private final Phase phase;

            /**
             * Constructs a new in phase annotation literal.
             *
             * @param phase the phase in which the annotated command context transformer should be called
             */
            public Literal(Phase phase) {
                this.phase = phase;
            }

            @Override
            public Phase value() {
                return phase;
            }
        }
    }

    /**
     * An annotation which serves as container for applying multiple {@link InPhase @InPhase} annotations.
     * This container annotation is used implicitly and should usually not be applied manually.
     * Just use multiple {@code @InPhase} annotations on the same class instead.
     *
     * @see InPhase @InPhase
     */
    @Retention(RUNTIME)
    @Target({ TYPE, FIELD, METHOD, PARAMETER })
    @Documented
    @interface InPhases {
        /**
         * Returns the phases in which the annotated command context transformer should be called.
         *
         * @return the phases in which the annotated command context transformer should be called
         */
        InPhase[] value();
    }
}
