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

package net.kautler.command.api

import net.kautler.test.PrivateFinalFieldSetterCategory
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.util.mop.Use

import java.util.function.Supplier

import static java.util.UUID.randomUUID
import static org.powermock.reflect.Whitebox.getAllInstanceFields
import static org.powermock.reflect.Whitebox.getField
import static org.powermock.reflect.Whitebox.newInstance

class CommandContextTest extends Specification {
    def message = randomUUID()
    def messageContent = randomUUID() as String
    def prefix = randomUUID() as String
    def alias = randomUUID() as String
    def parameterString = randomUUID() as String
    Command command = Stub()
    @Shared
    def additionalDataKey = randomUUID() as String
    @Shared
    def additionalDataValue = randomUUID()

    @Subject
    CommandContext testee = new CommandContext.Builder(message, messageContent)
            .withPrefix(prefix)
            .withAlias(alias)
            .withParameterString(parameterString)
            .withCommand(command)
            .withAdditionalData(additionalDataKey, additionalDataValue)
            .build()

    def 'constructor should not accept null value'() {
        when:
            new CommandContext(null)

        then:
            thrown(NullPointerException)
    }

    def 'constructor should not accept null message in given builder'() {
        given:
            def commandContextBuilder = new CommandContext.Builder(_, '')
            commandContextBuilder.message = null

        when:
            new CommandContext(commandContextBuilder)

        then:
            thrown(NullPointerException)
    }

    def 'constructor should not accept null message content in given builder'() {
        given:
            def commandContextBuilder = new CommandContext.Builder(_, '')
            commandContextBuilder.messageContent = null

        when:
            new CommandContext(commandContextBuilder)

        then:
            thrown(NullPointerException)
    }

    @Use(PrivateFinalFieldSetterCategory)
    def 'constructor should not accept null additional data in given builder'() {
        given:
            def commandContextBuilder = new CommandContext.Builder(_, '')
            commandContextBuilder.setFinalField('additionalData', null)

        when:
            new CommandContext(commandContextBuilder)

        then:
            thrown(NullPointerException)
    }

    def 'constructor should not accept null key in additional data of given builder'() {
        given:
            def commandContextBuilder = new CommandContext.Builder(_, '')
            commandContextBuilder.additionalData[null] = _

        when:
            new CommandContext(commandContextBuilder)

        then:
            thrown(NullPointerException)
    }

    def 'constructor should not accept null value in additional data of given builder'() {
        given:
            def commandContextBuilder = new CommandContext.Builder(_, '')
            commandContextBuilder.additionalData.key = null

        when:
            new CommandContext(commandContextBuilder)

        then:
            thrown(NullPointerException)
    }

    def 'getMessage should return message'() {
        expect:
            testee.message == message
    }

    def 'withMessage should set message'() {
        given:
            def message = randomUUID()

        expect:
            testee.withMessage(message).build().message == message
    }

    def 'getMessageContent should return message content'() {
        expect:
            testee.messageContent == messageContent
    }

    def 'withMessageContent should set message content'() {
        given:
            def messageContent = randomUUID() as String

        expect:
            testee.withMessageContent(messageContent).build().messageContent == messageContent
    }

    def 'getPrefix should return prefix'() {
        expect:
            testee.prefix.get() == prefix
    }

    def 'withPrefix should set prefix'() {
        given:
            def prefix = randomUUID() as String

        expect:
            testee.withPrefix(prefix).build().prefix.get() == prefix
    }

    def 'getAlias should return alias'() {
        expect:
            testee.alias.get() == alias
    }

    def 'withAlias should set alias'() {
        given:
            def alias = randomUUID() as String

        expect:
            testee.withAlias(alias).build().alias.get() == alias
    }

    def 'getParameterString should return parameter string'() {
        expect:
            testee.parameterString.get() == parameterString
    }

    def 'withParameterString should set parameter string'() {
        given:
            def parameterString = randomUUID() as String

        expect:
            testee.withParameterString(parameterString).build().parameterString.get() == parameterString
    }

    def 'getCommand should return command'() {
        expect:
            testee.command.get() == command
    }

    def 'withCommand should set command'() {
        given:
            Command command = Stub()

        expect:
            testee.withCommand(command).build().command.get() == command
    }

    def 'getAdditionalData "#key" should return optional of "#value"'() {
        expect:
            testee.getAdditionalData(key) == Optional.ofNullable(value)

        where:
            key                    || value
            additionalDataKey      || additionalDataValue
            randomUUID() as String || null
    }

    def 'getAdditionalData "#key" with default value should return "#value"'() {
        expect:
            testee.getAdditionalData(key, 'default') == value

        where:
            key                    || value
            additionalDataKey      || additionalDataValue
            randomUUID() as String || 'default'
    }

    def 'getAdditionalData "#key" with default value supplier should return "#value"'() {
        expect:
            testee.getAdditionalData(key, { 'default' } as Supplier) == value

        where:
            key                    || value
            additionalDataKey      || additionalDataValue
            randomUUID() as String || 'default'
    }

    def 'setAdditionalData should not accept null values [key: #key, value: #value]'() {
        when:
            testee.setAdditionalData(key, value)

        then:
            thrown(NullPointerException)

        where:
            key                    | value
            null                   | _
            randomUUID() as String | null
            null                   | null
    }

    def 'setAdditionalData "#key" should set value and return previous value'() {
        expect:
            testee.setAdditionalData(key, value) == Optional.ofNullable(oldValue)
            testee.getAdditionalData(key, null) == _

        where:
            key                    | value | oldValue
            additionalDataKey      | _     | additionalDataValue
            randomUUID() as String | _     | null
    }

    def 'removeAdditionalData "#key" should unset value and return previous value'() {
        expect:
            testee.removeAdditionalData(key) == Optional.ofNullable(oldValue)
            testee.getAdditionalData(key, null) == null

        where:
            key                    | oldValue
            additionalDataKey      | additionalDataValue
            randomUUID() as String | null
    }

    def 'equals should return true for same content'() {
        given:
            def testee2 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)
                    .build()

        expect:
            testee == testee2
    }

    def 'equals should return false with different message'() {
        given:
            def testee2 = new CommandContext.Builder(randomUUID() as String, messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)
                    .build()

        expect:
            testee != testee2
    }

    def 'equals should return false with different message content'() {
        given:
            def testee2 = new CommandContext.Builder(message, randomUUID() as String)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)
                    .build()

        expect:
            testee != testee2
    }

    def 'equals should return false with different prefix'() {
        given:
            def testee2 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(randomUUID() as String)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)
                    .build()

        expect:
            testee != testee2
    }

    def 'equals should return false with different alias'() {
        given:
            def testee2 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(randomUUID() as String)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)
                    .build()

        expect:
            testee != testee2
    }

    def 'equals should return false with different parameter string'() {
        given:
            def testee2 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(randomUUID() as String)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)
                    .build()

        expect:
            testee != testee2
    }

    def 'equals should return false with different command'() {
        given:
            def testee2 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(Stub(Command))
                    .withAdditionalData(additionalDataKey, additionalDataValue)
                    .build()

        expect:
            testee != testee2
    }

    def 'equals should return false with different additional data keys'() {
        given:
            def testee2 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(randomUUID() as String, additionalDataValue)
                    .build()

        expect:
            testee != testee2
    }

    def 'equals should return false with different additional data values'() {
        given:
            def testee2 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, randomUUID())
                    .build()

        expect:
            testee != testee2
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
            def testee2 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)
                    .build()

        expect:
            testee.hashCode() == testee2.hashCode()
    }

    def 'hash code should not be the same with different message'() {
        given:
            def testee2 = new CommandContext.Builder(randomUUID() as String, messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)
                    .build()

        expect:
            testee.hashCode() != testee2.hashCode()
    }

    def 'hash code should not be the same with different message content'() {
        given:
            def testee2 = new CommandContext.Builder(message, randomUUID() as String)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)
                    .build()

        expect:
            testee.hashCode() != testee2.hashCode()
    }

    def 'hash code should not be the same with different prefix'() {
        given:
            def testee2 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(randomUUID() as String)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)
                    .build()

        expect:
            testee.hashCode() != testee2.hashCode()
    }

    def 'hash code should not be the same with different alias'() {
        given:
            def testee2 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(randomUUID() as String)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)
                    .build()

        expect:
            testee.hashCode() != testee2.hashCode()
    }

    def 'hash code should not be the same with different parameter string'() {
        given:
            def testee2 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(randomUUID() as String)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, additionalDataValue)
                    .build()

        expect:
            testee.hashCode() != testee2.hashCode()
    }

    def 'hash code should not be the same with different command'() {
        given:
            def testee2 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(Stub(Command))
                    .withAdditionalData(additionalDataKey, additionalDataValue)
                    .build()

        expect:
            testee.hashCode() != testee2.hashCode()
    }

    def 'hash code should not be the same with different additional data keys'() {
        given:
            def testee2 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(randomUUID() as String, additionalDataValue)
                    .build()

        expect:
            testee.hashCode() != testee2.hashCode()
    }

    def 'hash code should not be the same with different additional data values'() {
        given:
            def testee2 = new CommandContext.Builder(message, messageContent)
                    .withPrefix(prefix)
                    .withAlias(alias)
                    .withParameterString(parameterString)
                    .withCommand(command)
                    .withAdditionalData(additionalDataKey, randomUUID())
                    .build()

        expect:
            testee.hashCode() != testee2.hashCode()
    }

    def 'toString should start with class name'() {
        expect:
            testee.toString().startsWith("${testee.getClass().simpleName}[")
    }

    def 'toString should contain field name and value for "#field.name"'() {
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
