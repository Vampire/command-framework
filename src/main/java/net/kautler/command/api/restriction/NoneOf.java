/*
 * Copyright 2019-2022 Björn Kautler
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

package net.kautler.command.api.restriction;

import jakarta.enterprise.context.ApplicationScoped;
import net.kautler.command.api.CommandContext;

import java.util.Collection;

import static java.util.Arrays.asList;

/**
 * A restriction that checks multiple other restrictions none of which may allow a command.
 * To use it, create a trivial subclass of this class and make it a discoverable CDI bean,
 * for example by annotating it with {@link ApplicationScoped @ApplicationScoped}.
 *
 * <p><b>Example:</b>
 * <pre>{@code
 * }&#64;{@code ApplicationScoped
 * public class NeitherRoleANorRoleB extends NoneOf<Message> }{{@code
 *     // make bean proxyable according to CDI spec
 *     public NeitherRoleANorRoleB() {
 *         super();
 *     }
 *
 *     }&#64;{@code Inject
 *     NeitherRoleANorRoleB(RoleA roleA, RoleB roleB) {
 *         super(roleA, roleB);
 *     }
 * }}{@code
 * }</pre>
 *
 * @param <M> the class of the messages for which this restriction can check allowance
 * @see AllOf
 * @see AnyOf
 */
public abstract class NoneOf<M> implements Restriction<M> {
    /**
     * The restrictions of which none may allow a command.
     */
    private final Collection<Restriction<? super M>> restrictions;

    /**
     * Constructs a new none-of restriction.
     *
     * @param restrictions the restrictions of which none may allow a command
     */
    @SafeVarargs
    public NoneOf(Restriction<? super M>... restrictions) {
        this.restrictions = asList(restrictions);
    }

    @Override
    public boolean allowCommand(CommandContext<? extends M> commandContext) {
        return restrictions.stream().noneMatch(restriction -> restriction.allowCommand(commandContext));
    }
}
