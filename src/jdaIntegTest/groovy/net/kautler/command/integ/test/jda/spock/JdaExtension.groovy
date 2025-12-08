/*
 * Copyright 2019-2025 Björn Kautler
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

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent
import net.dv8tion.jda.api.requests.GatewayIntent
import org.spockframework.runtime.extension.IGlobalExtension
import org.spockframework.runtime.model.SpecInfo
import spock.config.RunnerConfiguration
import spock.util.concurrent.BlockingVariable

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import static java.util.EnumSet.allOf
import static java.util.EnumSet.noneOf
import static java.util.UUID.randomUUID
import static net.dv8tion.jda.api.Permission.ADMINISTRATOR
import static net.dv8tion.jda.api.entities.channel.ChannelType.TEXT
import static org.spockframework.runtime.model.MethodInfo.MISSING_ARGUMENT

@ApplicationScoped
class JdaExtension implements IGlobalExtension {
    @Produces
    @ApplicationScoped
    static JDA botJda

    @Produces
    @ApplicationScoped
    static Guild guildAsBot

    private static JDA userJda

    private static Guild guildAsUser

    private static ExecutorService threadPool

    private final RunnerConfiguration runnerConfiguration

    JdaExtension() {
        this(null)
    }

    JdaExtension(RunnerConfiguration runnerConfiguration) {
        this.runnerConfiguration = runnerConfiguration
    }

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

        def manualUserRoles = guildAsBot.retrieveMemberById(System.properties.testDiscordUserId).complete().roles
        if (!manualUserRoles.empty && (guildAsBot.selfMember.roles.first() <= manualUserRoles.first())) {
            throw new IllegalArgumentException('Bot with testDiscordToken1 must have higher role than highest role of user with testDiscordUserId')
        }

        if (runnerConfiguration.parallel.enabled) {
            threadPool = Executors.newFixedThreadPool(runnerConfiguration.parallel.parallelExecutionConfiguration.parallelism)
        }
    }

    @Override
    void visitSpec(SpecInfo spec) {
        // work-around for https://github.com/junit-team/junit5/issues/3108
        if (runnerConfiguration.parallel.enabled) {
            spec.allFeatures*.addIterationInterceptor {
                threadPool.submit(it::proceed).get()
            }
        }

        (spec.setupSpecMethods + spec.cleanupSpecMethods)*.addInterceptor { invocation ->
            invocation
                    .method
                    .reflection
                    .parameterTypes
                    .eachWithIndex { parameterType, i ->
                        if (invocation.arguments[i] != MISSING_ARGUMENT) {
                            return
                        }

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
            def subscription = botJda
                    .listenOnce(GuildMemberRoleRemoveEvent)
                    .filter { it.guild == guildAsBot }
                    .filter { guildAsBot.retrieveMember(userJda.selfUser).complete().roles.empty }
                    .subscribe { rolesUpdateReceived.set(true) }
            try {
                if (guildAsBot.retrieveMember(userJda.selfUser).complete().roles.empty) {
                    rolesUpdateReceived.set(true)
                }

                guildAsBot
                        .retrieveMember(userJda.selfUser)
                        .flatMap { guildAsBot.modifyMemberRoles(it) }
                        .complete()

                rolesUpdateReceived.get()
            } finally {
                subscription.cancel()
            }

            rolesUpdateReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            subscription = botJda
                    .listenOnce(GuildMemberRoleRemoveEvent)
                    .filter { it.guild == guildAsBot }
                    .filter { guildAsBot.retrieveMemberById(System.properties.testDiscordUserId).complete().roles.empty }
                    .subscribe { rolesUpdateReceived.set(true) }
            try {
                if (guildAsBot.retrieveMemberById(System.properties.testDiscordUserId).complete().roles.empty) {
                    rolesUpdateReceived.set(true)
                }

                guildAsBot
                        .retrieveMemberById(System.properties.testDiscordUserId)
                        .flatMap { guildAsBot.modifyMemberRoles(it) }
                        .complete()

                rolesUpdateReceived.get()
            } finally {
                subscription.cancel()
            }

            TextChannel textChannelAsBot
            try {
                def parameterNames = invocation.feature.parameterNames
                if (['textChannelAsBot', 'textChannelAsUser'].any { it in parameterNames }) {
                    textChannelAsBot = guildAsBot
                            .retrieveMemberById(System.properties.testDiscordUserId)
                            .flatMap { testDiscordUser ->
                                guildAsBot
                                        .createTextChannel("command-framework integration test ${randomUUID()}")
                                        .addPermissionOverride(guildAsBot.publicRole, noneOf(Permission), allOf(Permission))
                                        .addPermissionOverride(guildAsUser.selfMember, allOf(Permission), noneOf(Permission))
                                        .addPermissionOverride(testDiscordUser, allOf(Permission), noneOf(Permission))
                            }
                            .complete()
                }

                parameterNames.eachWithIndex { parameterName, i ->
                    if (invocation.arguments[i] != MISSING_ARGUMENT) {
                        return
                    }

                    switch (parameterName) {
                        case { this.hasProperty("$parameterName") }:
                            invocation.arguments[i] = this."$parameterName"
                            break

                        case 'textChannelAsBot':
                            invocation.arguments[i] = textChannelAsBot
                            break

                        case 'textChannelAsUser':
                            def textChannelAsUser = new BlockingVariable<TextChannel>(System.properties.testResponseTimeout as double)
                            subscription = userJda
                                .listenOnce(ChannelCreateEvent)
                                .filter { it.channelType == TEXT }
                                .filter { it.channel.idLong == textChannelAsBot.idLong }
                                .subscribe { textChannelAsUser.set(it.channel.asTextChannel()) }
                            try {
                                invocation.arguments[i] = guildAsUser.getTextChannelById(textChannelAsBot.idLong)
                                if (invocation.arguments[i] == null) {
                                    invocation.arguments[i] = textChannelAsUser.get()
                                }
                            } finally {
                                subscription.cancel()
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
        threadPool?.shutdown()
    }
}
