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

package net.kautler.command.example.ping;

import net.dv8tion.jda.api.entities.Message;
import net.kautler.command.api.Command;
import net.kautler.command.api.CommandContext;
import org.apache.logging.log4j.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
class PingCommand implements Command<Message> {
    @Inject
    Logger logger;

    @Override
    public void execute(CommandContext<? extends Message> commandContext) {
        commandContext
                .getMessage()
                .getChannel()
                .sendMessage(commandContext
                        .getParameterString()
                        .filter(parameterString -> !parameterString.isEmpty())
                        .map(parameterString -> "pong: " + parameterString)
                        .orElse("pong"))
                .queue(null, throwable -> logger.error("Exception while executing ping command", throwable));
    }
}
