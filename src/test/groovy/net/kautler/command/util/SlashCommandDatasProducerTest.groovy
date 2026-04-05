/*
 * Copyright 2025-2026 Björn Kautler
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

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.kautler.command.api.slash.jda.SlashCommandJda
import net.kautler.test.ContextualInstanceCategory
import org.jboss.weld.junit.MockBean
import org.jboss.weld.spock.EnableWeld
import org.jboss.weld.spock.WeldInitiator
import org.jboss.weld.spock.WeldSetup
import spock.lang.Specification
import spock.lang.Subject
import spock.util.mop.Use

import static net.dv8tion.jda.api.interactions.IntegrationType.GUILD_INSTALL
import static net.dv8tion.jda.api.interactions.IntegrationType.USER_INSTALL
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING

@EnableWeld
@Subject(SlashCommandDatasProducer)
class SlashCommandDatasProducerTest extends Specification {
    SlashCommandJda command1 = Stub()

    SlashCommandJda command2 = Stub()

    SlashCommandJda command3 = Stub()

    SlashCommandJda command4 = Stub()

    SlashCommandJda command5 = Stub()

    SlashCommandJda command6 = Stub()

    SlashCommandJda command7 = Stub()

    SlashCommandJda command8 = Stub()

    @WeldSetup
    def weld = WeldInitiator
            .from(SlashCommandDatasProducer)
            .addBeans(
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(SlashCommandJda)
                            .creating(command1)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(SlashCommandJda)
                            .creating(command2)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(SlashCommandJda)
                            .creating(command3)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(SlashCommandJda)
                            .creating(command4)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(SlashCommandJda)
                            .creating(command5)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(SlashCommandJda)
                            .creating(command6)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(SlashCommandJda)
                            .creating(command7)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(SlashCommandJda)
                            .creating(command8)
                            .build()
            )
            .inject(this)
            .build()

    @Inject
    Instance<Collection<SlashCommandData>> slashCommandDatas

    def prepareCommands() {
        command1.with {
            it.aliases >> ['foo/bar1/test1']
            it.description >> Optional.of('The command foo/bar1/test1')
            it.prepareSubcommandData(_) >> { SubcommandData subcommandData ->
                subcommandData.addOption(STRING, 'foo-bar1-test1-option', 'The foo/bar1/test1 option')
            }
        }
        command2.with {
            it.aliases >> ['foo/bar1/test2']
            it.description >> Optional.of('The command foo/bar1/test2')
            it.prepareSubcommandData(_) >> { it.first() }
        }
        command3.with {
            it.aliases >> ['foo/bar2/test1']
            it.description >> Optional.of('The command foo/bar2/test1')
            it.prepareSubcommandData(_) >> { it.first() }
        }
        command4.with {
            it.aliases >> ['foo/bar2/test2']
            it.description >> Optional.of('The command foo/bar2/test2')
            it.prepareSubcommandData(_) >> { it.first() }
        }
        command5.with {
            it.aliases >> ['foo/test1']
            it.description >> Optional.of('The command foo/test1')
            it.prepareSubcommandData(_) >> { SubcommandData subcommandData ->
                subcommandData.addOption(STRING, 'foo-test1-option', 'The foo/test1 option', true)
            }
        }
        command6.with {
            it.aliases >> ['foo/test2']
            it.description >> Optional.of('The command foo/test2')
            it.prepareSubcommandData(_) >> { it.first() }
        }
        command7.with {
            it.aliases >> ['bar/baz']
            it.description >> Optional.of('The command bar/baz')
            it.prepareSubcommandData(_) >> { SubcommandData subcommandData ->
                subcommandData.addOption(STRING, 'bar-baz-option', 'The bar/baz option')
            }
        }
        command8.with {
            it.aliases >> ['bam']
            it.description >> Optional.of('The command bam')
            it.prepareSlashCommandData(_) >> { SlashCommandData slashCommandData ->
                slashCommandData.addOption(STRING, 'bam-option', 'The bam option')
            }
        }
    }

    def 'slash command builder list should be properly constructed for valid setup'() {
        given:
            prepareCommands()

        when:
            def slashCommandDatas = slashCommandDatas.get()

        then:
            slashCommandDatas.size() == 3
            slashCommandDatas*.name ==~ ['bar', 'bam', 'foo']
            with(slashCommandDatas.find { it.name == 'foo' }) { SlashCommandData it ->
                it.subcommandGroups*.name ==~ ['bar1', 'bar2']
                it.subcommands*.name ==~ ['test1', 'test2']
                with(it.subcommandGroups.find { it.name == 'bar1' }) {
                    it.subcommands*.name ==~ ['test1', 'test2']
                    with(it.subcommands.find { it.name == 'test1' }) {
                        it.description == 'The command foo/bar1/test1'
                        it.options.size() == 1
                        with(it.options.first()) {
                            it.name == 'foo-bar1-test1-option'
                            it.description == 'The foo/bar1/test1 option'
                            !it.required
                        }
                    }
                    with(it.subcommands.find { it.name == 'test2' }) {
                        it.description == 'The command foo/bar1/test2'
                        it.options.size() == 0
                    }
                }
                with(it.subcommandGroups.find { it.name == 'bar2' }) {
                    it.subcommands*.name ==~ ['test1', 'test2']
                    with(it.subcommands.find { it.name == 'test1' }) {
                        it.description == 'The command foo/bar2/test1'
                        it.options.size() == 0
                    }
                    with(it.subcommands.find { it.name == 'test2' }) {
                        it.description == 'The command foo/bar2/test2'
                        it.options.size() == 0
                    }
                }
                with(it.subcommands.find { it.name == 'test1' }) {
                    it.description == 'The command foo/test1'
                    it.options.size() == 1
                    with(it.options.first()) {
                        it.name == 'foo-test1-option'
                        it.description == 'The foo/test1 option'
                        it.required
                    }
                }
                with(it.subcommands.find { it.name == 'test2' }) {
                    it.description == 'The command foo/test2'
                    it.options.size() == 0
                }
            }
            with(slashCommandDatas.find { it.name == 'bar' }) {
                it.subcommands*.name == ['baz']
                it.integrationTypes ==~ [GUILD_INSTALL]
                with(it.subcommands.find { it.name == 'baz' }) {
                    it.description == 'The command bar/baz'
                    it.options.size() == 1
                    with(it.options.first()) {
                        it.name == 'bar-baz-option'
                        it.description == 'The bar/baz option'
                        !it.required
                    }
                }
            }
            with(slashCommandDatas.find { it.name == 'bam' }) {
                it.description == 'The command bam'
                it.options.size() == 1
                with(it.options.first()) {
                    it.name == 'bam-option'
                    it.description == 'The bam option'
                    !it.required
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
            slashCommandDatas.get().ci()

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
            'command7' | '''subcommand 'bar/baz\''''
            'command8' | '''command 'bam\''''
    }

    @Use(ContextualInstanceCategory)
    def 'should throw exception if alias has too many parts'() {
        given:
            command1.aliases >> ['a/b/c/d']
            prepareCommands()

        when:
            slashCommandDatas.get().ci()

        then:
            IllegalStateException ise = thrown()
            ise.message == 'Alias must be one, two, or three slash-separated parts for command, ' +
                    '''subcommand group and subcommand, but alias 'a/b/c/d' has 4 parts'''
    }

    def 'if top-level command has also subcommands top-level command should configure parent of subcommands'() {
        given:
            command8.aliases >> ['bar']
            command8.description >> Optional.of('The command bar')
            command8.prepareSlashCommandData(_) >> { SlashCommandData slashCommandData ->
                slashCommandData.integrationTypes = USER_INSTALL
                slashCommandData
            }
            prepareCommands()

        when:
            def slashCommandDatas = slashCommandDatas.get()

        then:
            with(slashCommandDatas.find { it.name == 'bar' }) {
                it.description == 'The command bar'
                it.integrationTypes ==~ [USER_INSTALL]
                it.subcommands*.name == ['baz']
                with(it.subcommands.find { it.name == 'baz' }) {
                    it.description == 'The command bar/baz'
                    it.options.size() == 1
                    with(it.options.first()) {
                        it.name == 'bar-baz-option'
                        it.description == 'The bar/baz option'
                        !it.required
                    }
                }
            }
    }
}
