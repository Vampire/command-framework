/*
 * Copyright 2020 Björn Kautler
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

package net.kautler.command.api

import net.kautler.test.PrivateFinalFieldSetterCategory
import spock.lang.Specification
import spock.lang.Subject
import spock.util.mop.Use

import static java.util.UUID.randomUUID
import static org.powermock.reflect.Whitebox.getAllInstanceFields
import static org.powermock.reflect.Whitebox.getField
import static org.powermock.reflect.Whitebox.newInstance

class CommandContextBuilderTest extends Specification {
    @Subject
    CommandContext.Builder testee = new CommandContext.Builder(_, '')

    def 'constructor should not accept null values [message: #message, messageContent: #messageContent]'() {
        when:
            new CommandContext.Builder(message, messageContent)

        then:
            thrown(NullPointerException)

        where:
            message | messageContent
            null    | randomUUID() as String
            _       | null
            null    | null
    }

    def 'constructor should set message in built command context'() {
        when:
            def commandContext = testee.build()

        then:
            commandContext.message.is(_)
    }

    def 'constructor should set message content in built command context'() {
        given:
            def messageContent = randomUUID() as String
            def testee = new CommandContext.Builder(_, messageContent)

        when:
            def commandContext = testee.build()

        then:
            commandContext.messageContent.is(messageContent)
    }

    def 'command context constructor should not accept null value'() {
        when:
            new CommandContext.Builder(null)

        then:
            thrown(NullPointerException)
    }

    @Use(PrivateFinalFieldSetterCategory)
    def 'command context constructor should not accept null message in given command context'() {
        given:
            def commandContext = testee.build()
            commandContext.setFinalField('message', null)

        when:
            new CommandContext.Builder(commandContext)

        then:
            thrown(NullPointerException)
    }

    @Use(PrivateFinalFieldSetterCategory)
    def 'command context constructor should not accept null message content in given command context'() {
        given:
            def commandContext = testee.build()
            commandContext.setFinalField('messageContent', null)

        when:
            new CommandContext.Builder(commandContext)

        then:
            thrown(NullPointerException)
    }

    @Use(PrivateFinalFieldSetterCategory)
    def 'command context constructor should not accept null additional data in given command context'() {
        given:
            def commandContext = testee.build()
            commandContext.setFinalField('additionalData', null)

        when:
            new CommandContext.Builder(commandContext)

        then:
            thrown(NullPointerException)
    }

    @Use(PrivateFinalFieldSetterCategory)
    def 'command context constructor should not accept null key in additional data of given command context'() {
        given:
            def commandContext = testee.build()
            commandContext.setFinalField('additionalData', [(null): _])

        when:
            new CommandContext.Builder(commandContext)

        then:
            thrown(NullPointerException)
    }

    @Use(PrivateFinalFieldSetterCategory)
    def 'command context constructor should not accept null value in additional data of given command context'() {
        given:
            def commandContext = testee.build()
            commandContext.setFinalField('additionalData', [key: null])

        when:
            new CommandContext.Builder(commandContext)

        then:
            thrown(NullPointerException)
    }

    @Use(PrivateFinalFieldSetterCategory)
    def 'command context constructor should set message in built command context'() {
        given:
            def commandContext = testee.build()
            commandContext.setFinalField('message', _)

        expect:
            new CommandContext.Builder(commandContext).build().message.is(_)
    }

    @Use(PrivateFinalFieldSetterCategory)
    def 'command context constructor should set message content in built command context'() {
        given:
            def messageContent = randomUUID() as String
            def commandContext = testee.build()
            commandContext.setFinalField('messageContent', messageContent)

        expect:
            new CommandContext.Builder(commandContext).build().messageContent.is(messageContent)
    }

    @Use(PrivateFinalFieldSetterCategory)
    def 'command context constructor should set prefix in built command context'() {
        given:
            def prefix = randomUUID() as String
            def commandContext = testee.build()
            commandContext.setFinalField('prefix', prefix)

        expect:
            new CommandContext.Builder(commandContext).build().prefix.get().is(prefix)
    }

    @Use(PrivateFinalFieldSetterCategory)
    def 'command context constructor should set alias in built command context'() {
        given:
            def alias = randomUUID() as String
            def commandContext = testee.build()
            commandContext.setFinalField('alias', alias)

        expect:
            new CommandContext.Builder(commandContext).build().alias.get().is(alias)
    }

    @Use(PrivateFinalFieldSetterCategory)
    def 'command context constructor should set parameter string in built command context'() {
        given:
            def parameterString = randomUUID() as String
            def commandContext = testee.build()
            commandContext.setFinalField('parameterString', parameterString)

        expect:
            new CommandContext.Builder(commandContext).build().parameterString.get().is(parameterString)
    }

    @Use(PrivateFinalFieldSetterCategory)
    def 'command context constructor should set command in built command context'() {
        given:
            Command command = Stub()
            def commandContext = testee.build()
            commandContext.setFinalField('command', command)

        expect:
            new CommandContext.Builder(commandContext).build().command.get().is(command)
    }

    @Use(PrivateFinalFieldSetterCategory)
    def 'command context constructor should not set the same additional data container in built command context'() {
        given:
            def additionalData = [:]
            def commandContext = testee.build()
            commandContext.setFinalField('additionalData', additionalData)

        expect:
            !new CommandContext.Builder(commandContext).build().additionalData.is(additionalData)
    }

    def 'command context constructor should set the additional data in built command context'() {
        given:
            def additionalData = [
                    (randomUUID() as String): randomUUID(),
                    (randomUUID() as String): randomUUID()
            ]
            def commandContext = testee.build()
            commandContext.additionalData.putAll(additionalData)

        expect:
            new CommandContext.Builder(commandContext).build().additionalData == additionalData
    }

    def 'withMessage should not accept null value'() {
        when:
            testee.withMessage(null)

        then:
            thrown(NullPointerException)
    }

    def 'withMessage should return self'() {
        expect:
            testee.withMessage(_).is(testee)
    }

    def 'withMessage should set message in built command context'() {
        expect:
            testee.withMessage(_).build().message.is(_)
    }

    def 'withMessageContent should not accept null value'() {
        when:
            testee.withMessageContent(null)

        then:
            thrown(NullPointerException)
    }

    def 'withMessageContent should return self'() {
        expect:
            testee.withMessageContent('').is(testee)
    }

    def 'withMessageContent should set message content in built command context'() {
        given:
            def messageContent = randomUUID() as String

        expect:
            testee.withMessageContent(messageContent).build().messageContent.is(messageContent)
    }

    def 'withPrefix should return self'() {
        expect:
            testee.withPrefix('').is(testee)
    }

    def 'withPrefix should set prefix in built command context'() {
        given:
            def prefix = randomUUID() as String

        expect:
            testee.withPrefix(prefix).build().prefix.get().is(prefix)
    }

    def 'withAlias should return self'() {
        expect:
            testee.withAlias('').is(testee)
    }

    def 'withAlias should set alias in built command context'() {
        given:
            def alias = randomUUID() as String

        expect:
            testee.withAlias(alias).build().alias.get().is(alias)
    }

    def 'withParameterString should return self'() {
        expect:
            testee.withParameterString('').is(testee)
    }

    def 'withParameterString should set parameter string in built command context'() {
        given:
            def parameterString = randomUUID() as String

        expect:
            testee.withParameterString(parameterString).build().parameterString.get().is(parameterString)
    }

    def 'withCommand should return self'() {
        expect:
            testee.withCommand(Stub(Command)).is(testee)
    }

    def 'withCommand should set command in built command context'() {
        given:
            Command command = Stub()

        expect:
            testee.withCommand(command).build().command.get().is(command)
    }

    def 'withAdditionalData should not accept null values [key: #key, value: #value]'() {
        when:
            testee.withAdditionalData(key, value)

        then:
            thrown(NullPointerException)

        where:
            key                    | value
            null                   | _
            randomUUID() as String | null
            null                   | null
    }

    def 'withAdditionalData should return self'() {
        expect:
            testee.withAdditionalData('', _).is(testee)
    }

    def 'withAdditionalData should set additional data in built command context'() {
        given:
            def key = randomUUID() as String

        expect:
            testee.withAdditionalData(key, _).build().getAdditionalData(key, null).is(_)
    }

    def 'withoutAdditionalData should return self'() {
        expect:
            testee.withoutAdditionalData('').is(testee)
    }

    def 'withoutAdditionalData should unset additional data in built command context'() {
        given:
            def key = randomUUID() as String
            testee.additionalData.put(key, _)

        expect:
            testee.withoutAdditionalData(key).build().getAdditionalData(key, null) == null
    }

    def 'withoutAdditionalData without parameter should return self'() {
        expect:
            testee.withoutAdditionalData().is(testee)
    }

    def 'withoutAdditionalData without parameter should not set any additional data in built command context'() {
        given:
            def key = randomUUID() as String
            testee.additionalData.put(key, _)

        expect:
            testee.withoutAdditionalData().build().additionalData.isEmpty()
    }

    def 'build should return a new independent command context on each call'() {
        given:
            def testee = new CommandContext.Builder(randomUUID(), randomUUID() as String)
                    .withPrefix(randomUUID() as String)
                    .withAlias(randomUUID() as String)
                    .withParameterString(randomUUID() as String)
                    .withCommand(Stub(Command))
                    .withAdditionalData(randomUUID() as String, randomUUID())

        when:
            def commandContext1 = testee.build()

        and:
            def commandContext2 = testee.build()

        then:
            !commandContext1.is(commandContext2)
            commandContext1 == commandContext2
            with(commandContext1) {
                message.is(commandContext2.message)
                messageContent.is(commandContext2.messageContent)
                prefix.get().is(commandContext2.prefix.get())
                alias.get().is(commandContext2.alias.get())
                parameterString.get().is(commandContext2.parameterString.get())
                command.get().is(commandContext2.command.get())
                !additionalData.is(commandContext2.additionalData)
                additionalData == commandContext2.additionalData
            }

        when:
            commandContext2 = testee
                    .withMessage(randomUUID())
                    .withMessageContent(randomUUID() as String)
                    .withPrefix(randomUUID() as String)
                    .withAlias(randomUUID() as String)
                    .withParameterString(randomUUID() as String)
                    .withCommand(Stub(Command))
                    .withAdditionalData(randomUUID() as String, randomUUID())
                    .build()

        then:
            commandContext1 != commandContext2
            with(commandContext1) {
                message != commandContext2.message
                messageContent != commandContext2.messageContent
                prefix != commandContext2.prefix
                alias != commandContext2.alias
                parameterString != commandContext2.parameterString
                command != commandContext2.command
                additionalData != commandContext2.additionalData
            }
    }

    def 'equals should return true for same content'() {
        given:
            def message = randomUUID()
            def messageContent = randomUUID() as String
            def prefix = randomUUID() as String
            def alias = randomUUID() as String
            def parameterString = randomUUID() as String
            Command command = Stub()
            def additionalDataKey = randomUUID() as String
            def additionalDataValue = randomUUID()

        and:
            def testee1 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)

        and:
            def testee2 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)

        expect:
            testee1 == testee2
    }

    def 'equals should return false with different message'() {
        given:
            def messageContent = randomUUID() as String
            def prefix = randomUUID() as String
            def alias = randomUUID() as String
            def parameterString = randomUUID() as String
            Command command = Stub()
            def additionalDataKey = randomUUID() as String
            def additionalDataValue = randomUUID()

        and:
            def testee1 = new CommandContext.Builder(randomUUID(), messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)

        and:
            def testee2 = new CommandContext.Builder(randomUUID() as String, messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)

        expect:
            testee1 != testee2
    }

    def 'equals should return false with different message content'() {
        given:
            def message = randomUUID()
            def prefix = randomUUID() as String
            def alias = randomUUID() as String
            def parameterString = randomUUID() as String
            Command command = Stub()
            def additionalDataKey = randomUUID() as String
            def additionalDataValue = randomUUID()

        and:
            def testee1 = new CommandContext.Builder(message, randomUUID() as String)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)

        and:
            def testee2 = new CommandContext.Builder(message, randomUUID() as String)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)

        expect:
            testee1 != testee2
    }

    def 'equals should return false with different prefix'() {
        given:
            def message = randomUUID()
            def messageContent = randomUUID() as String
            def alias = randomUUID() as String
            def parameterString = randomUUID() as String
            Command command = Stub()
            def additionalDataKey = randomUUID() as String
            def additionalDataValue = randomUUID()

        and:
            def testee1 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(randomUUID() as String)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)

        and:
            def testee2 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(randomUUID() as String)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)

        expect:
            testee1 != testee2
    }

    def 'equals should return false with different alias'() {
        given:
            def message = randomUUID()
            def messageContent = randomUUID() as String
            def prefix = randomUUID() as String
            def parameterString = randomUUID() as String
            Command command = Stub()
            def additionalDataKey = randomUUID() as String
            def additionalDataValue = randomUUID()

        and:
            def testee1 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(randomUUID() as String)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)

        and:
            def testee2 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(randomUUID() as String)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)

        expect:
            testee1 != testee2
    }

    def 'equals should return false with different parameter string'() {
        given:
            def message = randomUUID()
            def messageContent = randomUUID() as String
            def prefix = randomUUID() as String
            def alias = randomUUID() as String
            Command command = Stub()
            def additionalDataKey = randomUUID() as String
            def additionalDataValue = randomUUID()

        and:
            def testee1 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(randomUUID() as String)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)

        and:
            def testee2 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(randomUUID() as String)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)

        expect:
            testee1 != testee2
    }

    def 'equals should return false with different command'() {
        given:
            def message = randomUUID()
            def messageContent = randomUUID() as String
            def prefix = randomUUID() as String
            def alias = randomUUID() as String
            def parameterString = randomUUID() as String
            def additionalDataKey = randomUUID() as String
            def additionalDataValue = randomUUID()

        and:
            def testee1 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(Stub(Command))
                    .withAdditionalData(additionalDataKey, additionalDataValue)

        and:
            def testee2 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(Stub(Command))
                    .withAdditionalData(additionalDataKey, additionalDataValue)

        expect:
            testee1 != testee2
    }

    def 'equals should return false with different additional data keys'() {
        given:
            def message = randomUUID()
            def messageContent = randomUUID() as String
            def prefix = randomUUID() as String
            def alias = randomUUID() as String
            def parameterString = randomUUID() as String
            Command command = Stub()
            def additionalDataValue = randomUUID()

        and:
            def testee1 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(randomUUID() as String, additionalDataValue)

        and:
            def testee2 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(randomUUID() as String, additionalDataValue)

        expect:
            testee1 != testee2
    }

    def 'equals should return false with different additional data values'() {
        given:
            def message = randomUUID()
            def messageContent = randomUUID() as String
            def prefix = randomUUID() as String
            def alias = randomUUID() as String
            def parameterString = randomUUID() as String
            Command command = Stub()
            def additionalDataKey = randomUUID() as String

        and:
            def testee1 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, randomUUID())

        and:
            def testee2 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, randomUUID())

        expect:
            testee1 != testee2
    }

    def 'equals should return false for null'() {
        expect:
            !testee.equals(null)
    }

    def 'equals should return false for foreign class instance'() {
        expect:
            testee != _
    }

    def 'equals should return true for the same instance'() {
        expect:
            testee.equals(testee)
    }

    def 'hash code should be the same for same content'() {
        given:
            def message = randomUUID()
            def messageContent = randomUUID() as String
            def prefix = randomUUID() as String
            def alias = randomUUID() as String
            def parameterString = randomUUID() as String
            Command command = Stub()
            def additionalDataKey = randomUUID() as String
            def additionalDataValue = randomUUID()

        and:
            def testee1 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)

        and:
            def testee2 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)

        expect:
            testee1.hashCode() == testee2.hashCode()
    }

    def 'hash code should not be the same with different message'() {
        given:
            def messageContent = randomUUID() as String
            def prefix = randomUUID() as String
            def alias = randomUUID() as String
            def parameterString = randomUUID() as String
            Command command = Stub()
            def additionalDataKey = randomUUID() as String
            def additionalDataValue = randomUUID()

        and:
            def testee1 = new CommandContext.Builder(randomUUID(), messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)

        and:
            def testee2 = new CommandContext.Builder(randomUUID() as String, messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)

        expect:
            testee1.hashCode() != testee2.hashCode()
    }

    def 'hash code should not be the same with different message content'() {
        given:
            def message = randomUUID()
            def prefix = randomUUID() as String
            def alias = randomUUID() as String
            def parameterString = randomUUID() as String
            Command command = Stub()
            def additionalDataKey = randomUUID() as String
            def additionalDataValue = randomUUID()

        and:
            def testee1 = new CommandContext.Builder(message, randomUUID() as String)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)

        and:
            def testee2 = new CommandContext.Builder(message, randomUUID() as String)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)

        expect:
            testee1.hashCode() != testee2.hashCode()
    }

    def 'hash code should not be the same with different prefix'() {
        given:
            def message = randomUUID()
            def messageContent = randomUUID() as String
            def alias = randomUUID() as String
            def parameterString = randomUUID() as String
            Command command = Stub()
            def additionalDataKey = randomUUID() as String
            def additionalDataValue = randomUUID()

        and:
            def testee1 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(randomUUID() as String)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)

        and:
            def testee2 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(randomUUID() as String)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)

        expect:
            testee1.hashCode() != testee2.hashCode()
    }

    def 'hash code should not be the same with different alias'() {
        given:
            def message = randomUUID()
            def messageContent = randomUUID() as String
            def prefix = randomUUID() as String
            def parameterString = randomUUID() as String
            Command command = Stub()
            def additionalDataKey = randomUUID() as String
            def additionalDataValue = randomUUID()

        and:
            def testee1 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(randomUUID() as String)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)

        and:
            def testee2 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(randomUUID() as String)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)

        expect:
            testee1.hashCode() != testee2.hashCode()
    }

    def 'hash code should not be the same with different parameter string'() {
        given:
            def message = randomUUID()
            def messageContent = randomUUID() as String
            def prefix = randomUUID() as String
            def alias = randomUUID() as String
            Command command = Stub()
            def additionalDataKey = randomUUID() as String
            def additionalDataValue = randomUUID()

        and:
            def testee1 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(randomUUID() as String)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)

        and:
            def testee2 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(randomUUID() as String)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)

        expect:
            testee1.hashCode() != testee2.hashCode()
    }

    def 'hash code should not be the same with different command'() {
        given:
            def message = randomUUID()
            def messageContent = randomUUID() as String
            def prefix = randomUUID() as String
            def alias = randomUUID() as String
            def parameterString = randomUUID() as String
            def additionalDataKey = randomUUID() as String
            def additionalDataValue = randomUUID()

        and:
            def testee1 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(Stub(Command))
                    .withAdditionalData(additionalDataKey, additionalDataValue)

        and:
            def testee2 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(Stub(Command))
                    .withAdditionalData(additionalDataKey, additionalDataValue)

        expect:
            testee1.hashCode() != testee2.hashCode()
    }

    def 'hash code should not be the same with different additional data keys'() {
        given:
            def message = randomUUID()
            def messageContent = randomUUID() as String
            def prefix = randomUUID() as String
            def alias = randomUUID() as String
            def parameterString = randomUUID() as String
            Command command = Stub()
            def additionalDataValue = randomUUID()

        and:
            def testee1 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(randomUUID() as String, additionalDataValue)

        and:
            def testee2 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(randomUUID() as String, additionalDataValue)

        expect:
            testee1.hashCode() != testee2.hashCode()
    }

    def 'hash code should not be the same with different additional data values'() {
        given:
            def message = randomUUID()
            def messageContent = randomUUID() as String
            def prefix = randomUUID() as String
            def alias = randomUUID() as String
            def parameterString = randomUUID() as String
            Command command = Stub()
            def additionalDataKey = randomUUID() as String

        and:
            def testee1 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, randomUUID())

        and:
            def testee2 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, randomUUID())

        expect:
            testee1.hashCode() != testee2.hashCode()
    }

    def 'toString should start with class name'() {
        expect:
            testee.toString().startsWith("${testee.getClass().simpleName}[")
    }

    def 'toString should contain field name and value for "#field.name"'() {
        given:
            def testee = new CommandContext.Builder(randomUUID(), randomUUID() as String)
                    .withPrefix(randomUUID() as String)
                    .withAlias(randomUUID() as String)
                    .withParameterString(randomUUID() as String)
                    .withCommand(Stub(Command))
                    .withAdditionalData(randomUUID() as String, randomUUID())

        when:
            def toStringResult = testee.toString()

        then:
            toStringResult.contains("$field.name=")
            field.type == String ?
                    toStringResult.contains("'${field.get(testee)}'") :
                    toStringResult.contains(String.valueOf(field.get(testee)))

        where:
            field << getAllInstanceFields(newInstance(getField(getClass(), 'testee').type))
    }
}
