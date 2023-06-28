/*
 * Copyright 2023-2025 Björn Kautler
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

package net.kautler.command.integ.test.jda.restriction;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import net.dv8tion.jda.api.entities.Webhook;

/**
 * A utility class that provides helper methods for sending messages to webhooks from a JDA webhook,
 * because Groovy cannot compile the code due to optional absent classes being present in the signature.
 */
public final class WebhookSenderHelper {
    private WebhookSenderHelper() {
    }

    /**
     * Sends the given message to the given webhook.
     *
     * @param webhook the webhook to send the message to
     * @param message the message to send
     */
    public static void send(Webhook webhook, String message) {
        try (WebhookClient webhookClient = WebhookClientBuilder
            .fromJDA(webhook)
            .setWait(false)
            .build()) {

            webhookClient
                .send(message)
                .join();
        }
    }
}
