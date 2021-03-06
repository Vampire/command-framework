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

package net.kautler.command.example.ping;

import net.kautler.command.api.Command;
import org.apache.logging.log4j.Logger;
import org.javacord.api.entity.message.Message;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class PingCommand implements Command<Message> {
    @Inject
    private Logger logger;

    @Override
    public void execute(Message incomingMessage, String prefix, String usedAlias, String parameterString) {
        incomingMessage
                .getChannel()
                .sendMessage("pong: " + parameterString)
                .whenComplete((sentMessage, throwable) -> {
                    if (throwable != null) {
                        logger.error("Exception while executing ping command", throwable);
                    }
                });
    }
}
