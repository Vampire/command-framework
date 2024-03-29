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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.inject.Named;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

@ApplicationScoped
public class PingBot {
    @Produces
    @Named
    static String discordToken;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Please supply a Discord Bot token as sole argument");
            System.exit(1);
        }
        discordToken = args[0];
        SeContainerInitializer.newInstance()
                .addProperty("jakarta.enterprise.inject.scan.implicit", TRUE)
                .addProperty("org.jboss.weld.construction.relaxed", FALSE)
                .initialize();
    }
}
