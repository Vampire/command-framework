/*
 * Copyright 2019-2020 Björn Kautler
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

package net.kautler.command.integ.test.jda.spock

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.events.channel.text.TextChannelCreateEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.requests.GatewayIntent
import org.spockframework.runtime.extension.IGlobalExtension
import org.spockframework.runtime.model.SpecInfo
import spock.util.concurrent.BlockingVariable

import javax.enterprise.context.ApplicationScoped
import javax.enterprise.inject.Produces

import static java.lang.System.arraycopy
import static java.util.UUID.randomUUID
import static net.dv8tion.jda.api.Permission.ADMINISTRATOR
import static net.dv8tion.jda.api.Permission.ALL_PERMISSIONS

@ApplicationScoped
class JdaExtension implements IGlobalExtension {
    @Produces
    @ApplicationScoped
    private static JDA botJda

    private static Guild guildAsBot

    private static JDA userJda

    private static Guild guildAsUser

    @Override
    void start() {
        botJda = JDABuilder
                .create(System.properties.testDiscordToken1, GatewayIntent.values() as Collection)
                .build()
                .awaitReady()

        guildAsBot = botJda
                .getGuildById(System.properties.testDiscordServerId)

        if (guildAsBot == null) {
            new IllegalArgumentException('Bot with testDiscordToken1 is not a member of guild testDiscordServerId')
        }

        userJda = JDABuilder
                .createLight(System.properties.testDiscordToken2, [])
                .build()
                .awaitReady()

        guildAsUser = userJda
                .getGuildById(System.properties.testDiscordServerId)

        if (guildAsUser == null) {
            new IllegalArgumentException('Bot with testDiscordToken2 is not a member of guild testDiscordServerId')
        }

        if (!guildAsBot.selfMember.hasPermission(ADMINISTRATOR)) {
            throw new IllegalArgumentException('Bot with testDiscordToken1 must have ADMINISTRATOR permission')
        }

        def userRoles = guildAsBot.retrieveMember(userJda.selfUser).complete().roles
        if (!userRoles.empty && (guildAsBot.selfMember.roles.first() <= userRoles.first())) {
            throw new IllegalArgumentException('Bot with testDiscordToken1 must have higher role than highest role of bot with testDiscordToken2')
        }
    }

    @Override
    void visitSpec(SpecInfo spec) {
        (spec.setupSpecMethods + spec.cleanupSpecMethods)*.addInterceptor { invocation ->
            def parameterTypes = invocation.method.reflection.parameterTypes
            if (invocation.arguments.size() < parameterTypes.size()) {
                def newArguments = new Object[parameterTypes.size()]
                arraycopy(invocation.arguments, 0, newArguments, 0, invocation.arguments.size())
                invocation.arguments = newArguments
            }

            parameterTypes.eachWithIndex { parameterType, i ->
                switch (parameterType) {
                    case JDA:
                        invocation.arguments[i] = botJda
                        break

                    case Guild:
                        invocation.arguments[i] = guildAsBot
                        break
                }
            }

            invocation.proceed()
        }

        spec.allFeatures.featureMethod*.addInterceptor { invocation ->
            def rolesUpdateReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            EventListener eventListener = {
                if ((it instanceof GuildMemberRoleRemoveEvent) &&
                        guildAsBot.retrieveMember(userJda.selfUser).complete().roles.empty) {
                    rolesUpdateReceived.set(true)
                }
            }
            botJda.addEventListener(eventListener)
            try {
                if (guildAsBot.retrieveMember(userJda.selfUser).complete().roles.empty) {
                    rolesUpdateReceived.set(true)
                }

                guildAsBot
                        .modifyMemberRoles(guildAsBot.retrieveMember(userJda.selfUser).complete())
                        .complete()

                rolesUpdateReceived.get()
            } finally {
                botJda.removeEventListener(eventListener)
            }

            TextChannel textChannelAsBot
            try {
                def parameterNames = invocation.feature.parameterNames
                if (['textChannelAsBot', 'textChannelAsUser'].any { it in parameterNames }) {
                    textChannelAsBot = guildAsBot
                            .createTextChannel("command-framework integration test ${randomUUID()}")
                            .addPermissionOverride(guildAsBot.publicRole, 0, ALL_PERMISSIONS)
                            .addPermissionOverride(guildAsUser.selfMember, ALL_PERMISSIONS, 0)
                            .complete()
                }

                if (invocation.arguments.size() < parameterNames.size()) {
                    def newArguments = new Object[parameterNames.size()]
                    arraycopy(invocation.arguments, 0, newArguments, 0, invocation.arguments.size())
                    invocation.arguments = newArguments
                }

                parameterNames.eachWithIndex { parameterName, i ->
                    switch (parameterName) {
                        case { this.hasProperty("$parameterName") }:
                            invocation.arguments[i] = this."$parameterName"
                            break

                        case 'textChannelAsBot':
                            invocation.arguments[i] = textChannelAsBot
                            break

                        case 'textChannelAsUser':
                            def textChannelAsUser = new BlockingVariable<TextChannel>(System.properties.testResponseTimeout as double)
                            eventListener = {
                                if ((it instanceof TextChannelCreateEvent) &&
                                        it.channel.idLong == textChannelAsBot.idLong) {
                                    textChannelAsUser.set(it.channel)
                                }
                            }
                            userJda.addEventListener(eventListener)
                            try {
                                invocation.arguments[i] = guildAsUser.getTextChannelById(textChannelAsBot.idLong)
                                if (invocation.arguments[i] == null) {
                                    invocation.arguments[i] = textChannelAsUser.get()
                                }
                            } finally {
                                userJda.removeEventListener(eventListener)
                            }
                            break
                    }
                }

                invocation.proceed()
            } finally {
                textChannelAsBot?.delete()?.complete()
            }
        }
    }

    @Override
    void stop() {
        userJda?.shutdown()
        botJda?.shutdown()
    }
}
