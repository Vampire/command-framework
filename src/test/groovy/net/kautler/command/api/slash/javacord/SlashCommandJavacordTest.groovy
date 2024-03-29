/*
 * Copyright 2022 Björn Kautler
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

package net.kautler.command.api.slash.javacord

import net.kautler.command.api.CommandContext
import org.javacord.api.interaction.SlashCommandInteraction
import spock.lang.Specification
import spock.lang.Subject

@Subject(SlashCommandJavacordTest)
class SlashCommandJavacordTest extends Specification {
    def testee = new SlashCommandJavacord() {
        @Override
        void execute(CommandContext<? extends SlashCommandInteraction> commandContext) {
        }
    }

    def 'default slash command options should be empty'() {
        expect:
            testee.options.size() == 0
    }
}
