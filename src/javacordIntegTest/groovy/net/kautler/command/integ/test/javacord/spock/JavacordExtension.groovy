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

package net.kautler.command.integ.test.javacord.spock

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import org.javacord.api.DiscordApi
import org.javacord.api.DiscordApiBuilder
import org.javacord.api.entity.channel.ServerChannel
import org.javacord.api.entity.permission.PermissionsBuilder
import org.javacord.api.entity.server.Server
import org.spockframework.runtime.extension.IGlobalExtension
import org.spockframework.runtime.model.SpecInfo
import spock.util.concurrent.BlockingVariable

import static java.lang.Boolean.FALSE
import static java.lang.System.arraycopy
import static java.util.UUID.randomUUID
import static org.javacord.api.entity.intent.Intent.GUILDS
import static org.javacord.api.entity.permission.PermissionType.ADMINISTRATOR

@ApplicationScoped
class JavacordExtension implements IGlobalExtension {
    @Produces
    @ApplicationScoped
    static DiscordApi botDiscordApi

    @Produces
    @ApplicationScoped
    private static Server serverAsBot

    private static DiscordApi userDiscordApi

    private static Server serverAsUser

    @Override
    void start() {
        botDiscordApi = new DiscordApiBuilder()
                .setToken(System.properties.testDiscordToken1)
                .setAllIntents()
                .login()
                .join()

        serverAsBot = botDiscordApi
                .getServerById(System.properties.testDiscordServerId)
                .orElseThrow {
                    new IllegalArgumentException('Bot with testDiscordToken1 is not a member of server testDiscordServerId')
                }

        userDiscordApi = new DiscordApiBuilder()
                .setToken(System.properties.testDiscordToken2)
                .setIntents(GUILDS)
                .login()
                .join()

        serverAsUser = userDiscordApi
                .getServerById(System.properties.testDiscordServerId)
                .orElseThrow {
                    new IllegalArgumentException('Bot with testDiscordToken2 is not a member of server testDiscordServerId')
                }

        if (!serverAsBot.hasPermission(botDiscordApi.yourself, ADMINISTRATOR)) {
            throw new IllegalArgumentException('Bot with testDiscordToken1 must have ADMINISTRATOR permission')
        }

        if (serverAsBot
                .getHighestRole(botDiscordApi.yourself)
                .flatMap { highestBotRole ->
                    serverAsBot
                            .getHighestRole(userDiscordApi.yourself)
                            .map { it >= highestBotRole }
                }
                .orElse(FALSE)) {
            throw new IllegalArgumentException('Bot with testDiscordToken1 must have higher role than highest role of bot with testDiscordToken2')
        }

        // make sure the owner has not roles to begin with,
        // so all roles deleted further below were added by some test
        if (botDiscordApi
                .owner
                .join()
                .getRoles(serverAsBot) != [serverAsBot.everyoneRole]) {
            throw new IllegalArgumentException('The owner of bot with testDiscordToken1 must not have any roles in the test server')
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
                    case DiscordApi:
                        invocation.arguments[i] = botDiscordApi
                        break

                    case Server:
                        invocation.arguments[i] = serverAsBot
                        break
                }
            }

            invocation.proceed()
        }

        spec.allFeatures.featureMethod*.addInterceptor { invocation ->
            def rolesUpdateReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def listenerManager = serverAsBot.addUserRoleRemoveListener {
                if (serverAsBot.getRoles(userDiscordApi.yourself) == [serverAsBot.everyoneRole]) {
                    rolesUpdateReceived.set(true)
                }
            }
            try {
                if (serverAsBot.getRoles(userDiscordApi.yourself) == [serverAsBot.everyoneRole]) {
                    rolesUpdateReceived.set(true)
                }

                serverAsBot
                        .createUpdater()
                        .removeAllRolesFromUser(userDiscordApi.yourself)
                        .update()
                        .join()

                rolesUpdateReceived.get()
            } finally {
                listenerManager?.remove()
            }

            // remove all roles from owner
            // this is necessary for RoleJavacordSlashIntegTest
            // if these get automated somehow with another account,
            // this can be removed or rather changed to modify that account
            rolesUpdateReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            listenerManager = serverAsBot.addUserRoleRemoveListener {
                if (serverAsBot.getRoles(botDiscordApi.owner.join()) == [serverAsBot.everyoneRole]) {
                    rolesUpdateReceived.set(true)
                }
            }
            try {
                if (serverAsBot.getRoles(botDiscordApi.owner.join()) == [serverAsBot.everyoneRole]) {
                    rolesUpdateReceived.set(true)
                }

                serverAsBot
                        .createUpdater()
                        .removeAllRolesFromUser(botDiscordApi.owner.join())
                        .update()
                        .join()

                rolesUpdateReceived.get()
            } finally {
                listenerManager?.remove()
            }

            def serverTextChannelAsBot
            try {
                def parameterNames = invocation.feature.parameterNames
                if (['serverTextChannelAsBot', 'serverTextChannelAsUser'].any { it in parameterNames }) {
                    serverTextChannelAsBot = serverAsBot
                            .createTextChannelBuilder()
                            .setName("command-framework integration test ${randomUUID()}")
                            .addPermissionOverwrite(
                                    serverAsBot.everyoneRole,
                                    new PermissionsBuilder().setAllDenied().build())
                            .addPermissionOverwrite(
                                    userDiscordApi.yourself,
                                    new PermissionsBuilder().setAllAllowed().build())
                            .create()
                            .join()
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

                        case 'serverTextChannelAsBot':
                            invocation.arguments[i] = serverTextChannelAsBot
                            break

                        case 'serverTextChannelAsUser':
                            def serverTextChannelAsUser = new BlockingVariable<ServerChannel>(System.properties.testResponseTimeout as double)
                            listenerManager = serverAsUser.addServerChannelCreateListener {
                                if (it.channel.id == serverTextChannelAsBot.id) {
                                    serverTextChannelAsUser.set(it.channel)
                                }
                            }
                            try {
                                invocation.arguments[i] = serverAsUser
                                        .getTextChannelById(serverTextChannelAsBot.id)
                                        .orElseGet { serverTextChannelAsUser.get() }
                            } finally {
                                listenerManager?.remove()
                            }
                            break
                    }
                }

                invocation.proceed()
            } finally {
                serverTextChannelAsBot?.delete()?.join()
            }
        }
    }

    @Override
    void stop() {
        userDiscordApi?.disconnect()
        botDiscordApi?.disconnect()
    }
}
