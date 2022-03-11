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

package net.kautler.command.util

import javax.enterprise.context.ApplicationScoped
import javax.enterprise.inject.Instance
import javax.inject.Inject

import net.kautler.command.api.slash.javacord.SlashCommandJavacord
import net.kautler.test.ContextualInstanceCategory
import org.javacord.api.interaction.SlashCommandBuilder
import org.jboss.weld.junit.MockBean
import org.jboss.weld.junit4.WeldInitiator
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject
import spock.util.mop.Use

import static org.javacord.api.interaction.SlashCommandOption.createStringOption
import static org.javacord.api.interaction.SlashCommandOptionType.SUB_COMMAND
import static org.javacord.api.interaction.SlashCommandOptionType.SUB_COMMAND_GROUP

@Subject(SlashCommandBuilderProducer)
class SlashCommandBuilderProducerTest extends Specification {
    SlashCommandJavacord command1 = Mock()

    SlashCommandJavacord command2 = Mock()

    SlashCommandJavacord command3 = Mock()

    SlashCommandJavacord command4 = Mock()

    SlashCommandJavacord command5 = Mock()

    SlashCommandJavacord command6 = Mock()

    SlashCommandJavacord command7 = Mock()

    @Rule
    WeldInitiator weld = WeldInitiator
            .from(SlashCommandBuilderProducer)
            .addBeans(
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(SlashCommandJavacord)
                            .creating(command1)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(SlashCommandJavacord)
                            .creating(command2)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(SlashCommandJavacord)
                            .creating(command3)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(SlashCommandJavacord)
                            .creating(command4)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(SlashCommandJavacord)
                            .creating(command5)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(SlashCommandJavacord)
                            .creating(command6)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(SlashCommandJavacord)
                            .creating(command7)
                            .build()
            )
            .inject(this)
            .build()

    @Inject
    Instance<List<SlashCommandBuilder>> slashCommandBuilders

    def prepareCommands() {
        command1.with {
            it.aliases >> ['foo/bar1/test1']
            it.description >> ['The command foo/bar1/test1']
            it.options >> [createStringOption('foo/bar1/test1 option', 'The foo/bar1/test1 option', false)]
        }
        command2.with {
            it.aliases >> ['foo/bar1/test2']
            it.description >> ['The command foo/bar1/test2']
        }
        command3.with {
            it.aliases >> ['foo/bar2/test1']
            it.description >> ['The command foo/bar2/test1']
        }
        command4.with {
            it.aliases >> ['foo/bar2/test2']
            it.description >> ['The command foo/bar2/test2']
        }
        command5.with {
            it.aliases >> ['foo/test1']
            it.description >> ['The command foo/test1']
            it.options >> [createStringOption('foo/test1 option', 'The foo/test1 option', true)]
        }
        command6.with {
            it.aliases >> ['foo/test2']
            it.description >> ['The command foo/test2']
        }
        command7.with {
            it.aliases >> ['bar']
            it.description >> ['The command bar']
            it.options >> [createStringOption('bar option', 'The bar option', false)]
        }
    }

    def 'slash command builder list should be properly constructed for valid setup'() {
        given:
            prepareCommands()

        when:
            def slashCommandBuilders = slashCommandBuilders.get()

        then:
            slashCommandBuilders.size() == 2
            slashCommandBuilders*.delegate*.name.sort() == ['bar', 'foo']
            with(slashCommandBuilders.find { it.delegate.name == 'foo' }) {
                with(it.delegate) {
                    it.options.size() == 4
                    it.options*.name.sort() == ['bar1', 'bar2', 'test1', 'test2']
                    with(it.options.find { it.name == 'bar1' }) {
                        it.type == SUB_COMMAND_GROUP
                        it.options.size() == 2
                        it.options*.name.sort() == ['test1', 'test2']
                        with(it.options.find { it.name == 'test1' }) {
                            it.type == SUB_COMMAND
                            it.description == 'The command foo/bar1/test1'
                            it.options.size() == 1
                            with(it.options.first()) {
                                it.name == 'foo/bar1/test1 option'
                                it.description == 'The foo/bar1/test1 option'
                                !it.required
                            }
                        }
                        with(it.options.find { it.name == 'test2' }) {
                            it.type == SUB_COMMAND
                            it.description == 'The command foo/bar1/test2'
                            it.options.size() == 0
                        }
                    }
                    with(it.options.find { it.name == 'bar2' }) {
                        it.type == SUB_COMMAND_GROUP
                        it.options.size() == 2
                        it.options*.name.sort() == ['test1', 'test2']
                        with(it.options.find { it.name == 'test1' }) {
                            it.type == SUB_COMMAND
                            it.description == 'The command foo/bar2/test1'
                            it.options.size() == 0
                        }
                        with(it.options.find { it.name == 'test2' }) {
                            it.type == SUB_COMMAND
                            it.description == 'The command foo/bar2/test2'
                            it.options.size() == 0
                        }
                    }
                    with(it.options.find { it.name == 'test1' }) {
                        it.type == SUB_COMMAND
                        it.description == 'The command foo/test1'
                        it.options.size() == 1
                        with(it.options.first()) {
                            it.name == 'foo/test1 option'
                            it.description == 'The foo/test1 option'
                            it.required
                        }
                    }
                    with(it.options.find { it.name == 'test2' }) {
                        it.type == SUB_COMMAND
                        it.description == 'The command foo/test2'
                        it.options.size() == 0
                    }
                }
            }
            with(slashCommandBuilders.find { it.delegate.name == 'bar' }) {
                with(it.delegate) {
                    it.description == 'The command bar'
                    it.options.size() == 1
                    with(it.options.first()) {
                        it.name == 'bar option'
                        it.description == 'The bar option'
                        !it.required
                    }
                }
            }
    }

    @Use(ContextualInstanceCategory)
    def 'should throw exception if description is missing in command #command'() {
        given:
            command = this."$command"
            command.description >> Optional.empty()
            prepareCommands()

        when:
            slashCommandBuilders.get().ci()

        then:
            IllegalStateException ise = thrown()
            ise.message == "Descriptions are mandatory for slash commands, but $label does not have one"

        where:
            command    | label
            'command1' | '''subcommand 'foo/bar1/test1\''''
            'command2' | '''subcommand 'foo/bar1/test2\''''
            'command3' | '''subcommand 'foo/bar2/test1\''''
            'command4' | '''subcommand 'foo/bar2/test2\''''
            'command5' | '''subcommand 'foo/test1\''''
            'command6' | '''subcommand 'foo/test2\''''
            'command7' | '''command 'bar\''''
    }

    @Use(ContextualInstanceCategory)
    def 'should throw exception if alias has too many parts'() {
        given:
            command1.aliases >> ['a/b/c/d']
            prepareCommands()

        when:
            slashCommandBuilders.get().ci()

        then:
            IllegalStateException ise = thrown()
            ise.message == 'Alias must be one, two, or three slash-separated parts for command, ' +
                    '''subcommand group and subcommand, but alias 'a/b/c/d' has 4 parts'''
    }
}
