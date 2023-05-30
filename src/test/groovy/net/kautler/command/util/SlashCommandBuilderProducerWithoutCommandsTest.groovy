/*
 * Copyright 2022-2023 Björn Kautler
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

package net.kautler.command.util

import jakarta.inject.Inject
import org.javacord.api.interaction.SlashCommandBuilder
import org.jboss.weld.spock.EnableWeld
import org.jboss.weld.spock.WeldInitiator
import org.jboss.weld.spock.WeldSetup
import spock.lang.Specification
import spock.lang.Subject

@EnableWeld
@Subject(SlashCommandBuilderProducer)
class SlashCommandBuilderProducerWithoutCommandsTest extends Specification {
    @WeldSetup
    def weld = WeldInitiator
            .from(SlashCommandBuilderProducer)
            .inject(this)
            .build()

    @Inject
    List<SlashCommandBuilder> slashCommandBuilders

    def 'no commands available should result in empty list'() {
        expect:
            slashCommandBuilders == []
    }
}
