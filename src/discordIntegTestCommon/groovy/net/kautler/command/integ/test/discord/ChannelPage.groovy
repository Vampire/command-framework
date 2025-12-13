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

package net.kautler.command.integ.test.discord

import geb.Page

import static org.openqa.selenium.Keys.ENTER

class ChannelPage extends Page {
    static url = 'channels'

    static at = {
        isSelected
    }

    static content = {
        isSelected {
            $('a', href: endsWith(convertToPath()))
                .parents('div', class: contains('selected'))
        }
        primaryButton { $('button', class: contains('primary')) }
        textBox(required: false) { $('div', role: 'textbox') }
    }

    BigInteger serverId

    BigInteger channelId

    @Override
    String convertToPath(Object... args) {
        "/${serverId ?: '@me'}/$channelId"
    }

    void sendMessage(String message) {
        textBox << message << ENTER
    }

    void sendSlashCommand(String message) {
        def messageTokens = message.tokenize()

        def command = messageTokens.first().replaceAll('(?<!^)/', ' ')
        textBox << command
        waitFor {
            $('div', class: contains('autocompleteRow'))
                .$('div', text: command)
        }
        textBox << ENTER

        if (messageTokens.size() == 1) {
            textBox << ENTER
        } else {
            messageTokens.tail().each { argument ->
                textBox << argument << ENTER
            }
        }
    }
}
