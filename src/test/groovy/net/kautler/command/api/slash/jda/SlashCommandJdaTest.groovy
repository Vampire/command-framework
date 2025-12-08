/*
 * Copyright 2025 Björn Kautler
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

package net.kautler.command.api.slash.jda

import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.kautler.command.api.CommandContext
import spock.lang.Specification
import spock.lang.Subject

@Subject(SlashCommandJda)
class SlashCommandJdaTest extends Specification {
    def testee = new SlashCommandJda() {
        @Override
        void execute(CommandContext<? extends SlashCommandInteraction> commandContext) {
        }
    }

    def 'default prepareSlashCommandData should return argument'() {
        given:
            def slashCommandData = Stub(SlashCommandData)

        expect:
            testee.prepareSlashCommandData(slashCommandData) is slashCommandData
    }

    def 'default prepareSubcommandData should return argument'() {
        given:
            def subcommandData = Stub(SubcommandData)

        expect:
            testee.prepareSubcommandData(subcommandData) is subcommandData
    }
}
