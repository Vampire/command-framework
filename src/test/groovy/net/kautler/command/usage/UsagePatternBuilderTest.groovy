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

package net.kautler.command.usage

import net.kautler.command.usage.UsageParser.UsageContext
import net.kautler.test.ContextualInstanceCategory
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.jboss.weld.junit4.WeldInitiator
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject
import spock.util.mop.Use

import javax.inject.Inject

import static org.powermock.reflect.Whitebox.getAllInstanceFields
import static org.powermock.reflect.Whitebox.getField
import static org.powermock.reflect.Whitebox.newInstance

@Subject([UsageLexer, UsageParser, UsageBaseVisitor])
class UsagePatternBuilderTest extends Specification {
    @Rule
    WeldInitiator weld = WeldInitiator
            .from(UsagePatternBuilder)
            .inject(this)
            .build()

    @Inject
    @Subject
    UsagePatternBuilder testee

    def 'usage "#usage" with parameter string "#parameterString" should #match'() {
        given:
            def usageLexer = new UsageLexer(CharStreams.fromString(usage))
            def usageParser = new UsageParser(new CommonTokenStream(usageLexer))
            def usageTree = usageParser.usage()

        when:
            def matcher = parameterString =~ testee.getPattern(usageTree)

        then:
            if (groups == null) {
                assert !matcher.matches()
            } else {
                assert matcher.matches()
                assert (1..matcher.groupCount())
                        .collect { matcher.group(it) }
                        .findAll() == groups
                assert testee.getGroupNamesByTokenName(usageTree)
                        .collect { tokenName, groupNames ->
                            [tokenName, groupNames.collect { matcher.group(it) }.findAll()]
                        }
                        .findAll { it[1] }
                        .collectEntries { it } == tokenValues
            }

        where:
            usage                       | parameterString         || groups                      | tokenValues
            //-----------------------------------------------------------------------------------------------------------------------------------//
            '<foo>'                     | 'test'                  || ['test']                    | [foo: ['test']]
            '<foo>'                     | 'test1  test2'          || null                        | null
            '<foo>'                     | ''                      || null                        | null
            '<foo bar>'                 | 'test'                  || ['test']                    | ['foo bar': ['test']]
            '<foo bar>'                 | 'test1  test2'          || null                        | null
            '<foo bar>'                 | ''                      || null                        | null
            '<foo> <bar>'               | 'test'                  || null                        | null
            '<foo> <bar>'               | 'test1  test2'          || ['test1', 'test2']          | [foo: ['test1'], bar: ['test2']]
            '<foo> <bar>'               | ''                      || null                        | null
            '<foo><bar>'                | 'test'                  || null                        | null
            '<foo><bar>'                | 'test1  test2'          || ['test1', 'test2']          | [foo: ['test1'], bar: ['test2']]
            '<foo><bar>'                | ''                      || null                        | null
            ' <foo bar> '               | 'test'                  || ['test']                    | ['foo bar': ['test']]
            ' <foo bar> '               | 'test1 test2'           || null                        | null
            ' <foo bar> '               | ''                      || null                        | null
            '<foo> <foo>'               | 'test'                  || null                        | null
            '<foo> <foo>'               | 'test1  test2'          || ['test1', 'test2']          | ['foo': ['test1', 'test2']]
            '<foo> <foo>'               | ''                      || null                        | null
            //-----------------------------------------------------------------------------------------------------------------------------------//
            '<foo...>'                  | 'test'                  || ['test']                    | [foo: ['test']]
            '<foo...>'                  | 'test1  test2'          || ['test1  test2']            | [foo: ['test1  test2']]
            '<foo...>'                  | ''                      || null                        | null
            '<foo bar...>'              | 'test'                  || ['test']                    | ['foo bar': ['test']]
            '<foo bar...>'              | 'test1  test2'          || ['test1  test2']            | ['foo bar': ['test1  test2']]
            '<foo bar...>'              | ''                      || null                        | null
            '<foo> <bar...>'            | 'test'                  || null                        | null
            '<foo> <bar...>'            | 'test1  test2'          || ['test1', 'test2']          | [foo: ['test1'], bar: ['test2']]
            '<foo> <bar...>'            | 'test1  test2  test3'   || ['test1', 'test2  test3']   | [foo: ['test1'], bar: ['test2  test3']]
            '<foo> <bar...>'            | ''                      || null                        | null
            '<foo><bar...>'             | 'test'                  || null                        | null
            '<foo><bar...>'             | 'test1  test2'          || ['test1', 'test2']          | [foo: ['test1'], bar: ['test2']]
            '<foo><bar...>'             | 'test1  test2  test3'   || ['test1', 'test2  test3']   | [foo: ['test1'], bar: ['test2  test3']]
            '<foo><bar...>'             | ''                      || null                        | null
            '<foo><bar...><baz>'        | 'test1  test2  test3'   || null                        | null
            '<foo><bar...>[<baz>]'      | 'test1  test2  test3'   || ['test1', 'test2  test3']   | [foo: ['test1'], bar: ['test2  test3']]
            ' <foo bar...> '            | 'test'                  || ['test']                    | ['foo bar': ['test']]
            ' <foo bar...> '            | 'test1  test2'          || ['test1  test2']            | ['foo bar': ['test1  test2']]
            ' <foo bar...> '            | ''                      || null                        | null
            '<foo><foo...>'             | 'test'                  || null                        | null
            '<foo><foo...>'             | 'test1  test2'          || ['test1', 'test2']          | [foo: ['test1', 'test2']]
            '<foo><foo...>'             | 'test1  test2  test3'   || ['test1', 'test2  test3']   | [foo: ['test1', 'test2  test3']]
            '<foo><foo...>'             | ''                      || null                        | null
            '<foo><foo...>'             | 'test1  test2\n test3'  || ['test1', 'test2\n test3']  | [foo: ['test1', 'test2\n test3']]
            '<foo><foo...>'             | 'test1 \n test2\ntest3' || ['test1', 'test2\ntest3']   | [foo: ['test1', 'test2\ntest3']]
            '<foo><foo...>'             | 'test1\n test2\ntest3'  || ['test1', 'test2\ntest3']   | [foo: ['test1', 'test2\ntest3']]
            //-----------------------------------------------------------------------------------------------------------------------------------//
            /'test'/                    | 'test'                  || ['test']                    | [test: ['test']]
            /'test'/                    | 'test1  test2'          || null                        | null
            /'test'/                    | ''                      || null                        | null
            / 'test1' 'test2' /         | 'test1'                 || null                        | null
            / 'test1' 'test2' /         | 'test1  test2'          || ['test1', 'test2']          | [test1: ['test1'], test2: ['test2']]
            / 'test1' 'test2' /         | ''                      || null                        | null
            /'test1''test2'/            | 'test1'                 || null                        | null
            /'test1''test2'/            | 'test1  test2'          || ['test1', 'test2']          | [test1: ['test1'], test2: ['test2']]
            /'test1''test2'/            | ''                      || null                        | null
            / 'test1' 'test1' /         | 'test1'                 || null                        | null
            / 'test1' 'test1' /         | 'test1  test1'          || ['test1', 'test1']          | [test1: ['test1', 'test1']]
            / 'test1' 'test1' /         | ''                      || null                        | null
            //-----------------------------------------------------------------------------------------------------------------------------------//
            '[<foo>]'                   | 'test'                  || ['test']                    | [foo: ['test']]
            '[<foo>]'                   | 'test1  test2'          || null                        | null
            '[<foo>]'                   | ''                      || []                          | [:]
            '[ <foo> ]'                 | 'test'                  || ['test']                    | [foo: ['test']]
            '[ <foo> ]'                 | 'test1  test2'          || null                        | null
            '[ <foo> ]'                 | ''                      || []                          | [:]
            '[<foo>] [<bar>]'           | 'test'                  || ['test']                    | [foo: ['test']]
            '[<foo>] [<bar>]'           | 'test1  test2'          || ['test1', 'test2']          | [foo: ['test1'], bar: ['test2']]
            '[<foo>] [<bar>]'           | ''                      || []                          | [:]
            '[<foo>][<bar>]'            | 'test'                  || ['test']                    | [foo: ['test']]
            '[<foo>][<bar>]'            | 'test1  test2'          || ['test1', 'test2']          | [foo: ['test1'], bar: ['test2']]
            '[<foo>][<bar>]'            | ''                      || []                          | [:]
            ' [<foo>] '                 | 'test'                  || ['test']                    | [foo: ['test']]
            ' [<foo>] '                 | 'test1 test2'           || null                        | null
            ' [<foo>] '                 | ''                      || []                          | [:]
            '[<foo>] [<foo>]'           | 'test'                  || ['test']                    | [foo: ['test']]
            '[<foo>] [<foo>]'           | 'test1  test2'          || ['test1', 'test2']          | [foo: ['test1', 'test2']]
            '[<foo>] [<foo>]'           | ''                      || []                          | [:]
            '[<foo...>]'                | 'test'                  || ['test']                    | [foo: ['test']]
            '[<foo...>]'                | 'test1  test2'          || ['test1  test2']            | [foo: ['test1  test2']]
            '[<foo...>]'                | ''                      || []                          | [:]
            /['test']/                  | 'test'                  || ['test']                    | [test: ['test']]
            /['test']/                  | 'test1  test2'          || null                        | null
            /['test']/                  | ''                      || []                          | [:]
            '[[<foo>] [<bar>]]'         | 'test'                  || ['test']                    | [foo: ['test']]
            '[[<foo>] [<bar>]]'         | 'test1  test2'          || ['test1', 'test2']          | [foo: ['test1'], bar: ['test2']]
            '[[<foo>] [<bar>]]'         | ''                      || []                          | [:]
            /[('test1'|'test2')]/       | 'test1'                 || ['test1']                   | [test1: ['test1']]
            /[('test1'|'test2')]/       | 'test2'                 || ['test2']                   | [test2: ['test2']]
            /[('test1'|'test2')]/       | 'test1  test2'          || null                        | null
            /[('test1'|'test2')]/       | ''                      || []                          | [:]
            //-----------------------------------------------------------------------------------------------------------------------------------//
            /('test1'|'test2')/         | 'test1'                 || ['test1']                   | [test1: ['test1']]
            /('test1'|'test2')/         | 'test2'                 || ['test2']                   | [test2: ['test2']]
            /('test1'|'test2')/         | 'test1  test2'          || null                        | null
            /('test1'|'test2')/         | ''                      || null                        | null
            /( 'test1' | 'test2' )/     | 'test1'                 || ['test1']                   | [test1: ['test1']]
            /( 'test1' | 'test2' )/     | 'test2'                 || ['test2']                   | [test2: ['test2']]
            /( 'test1' | 'test2' )/     | 'test1  test2'          || null                        | null
            /( 'test1' | 'test2' )/     | ''                      || null                        | null
            / ('test1'|'test2') /       | 'test1'                 || ['test1']                   | [test1: ['test1']]
            / ('test1'|'test2') /       | 'test2'                 || ['test2']                   | [test2: ['test2']]
            / ('test1'|'test2') /       | 'test1  test2'          || null                        | null
            / ('test1'|'test2') /       | ''                      || null                        | null
            '( <test1> | <test1...> )'  | 'test1'                 || ['test1']                   | [test1: ['test1']]
            '( <test1> | <test1...> )'  | 'test1  test2'          || ['test1  test2']            | [test1: ['test1  test2']]
            '( <test1> | <test2...> )'  | 'test1  test2'          || ['test1  test2']            | [test2: ['test1  test2']]
            '( <test1...> | <test2> )'  | 'test1  test2'          || ['test1  test2']            | [test1: ['test1  test2']]
            /( 'test1' | 'test1' )/     | 'test1  test2'          || null                        | null
            /( 'test1' | 'test1' )/     | ''                      || null                        | null
            /('test1'|'test2' 'test3')/ | 'test1'                 || ['test1']                   | [test1: ['test1']]
            /('test1'|'test2' 'test3')/ | 'test2 test3'           || ['test2', 'test3']          | [test2: ['test2'], test3: ['test3']]
            //-----------------------------------------------------------------------------------------------------------------------------------//
            '<foo>'                     | 'test1'                 || ['test1']                   | [foo: ['test1']]
            '<foo>'                     | ' test1'                || null                        | null
            '<foo>'                     | 'test1 '                || null                        | null
            '<foo>'                     | ' test1 '               || null                        | null
            //-----------------------------------------------------------------------------------------------------------------------------------//
            '[<foo>]'                   | 'test1'                 || ['test1']                   | [foo: ['test1']]
            '[<foo>]'                   | ' test1'                || null                        | null
            '[<foo>]'                   | 'test1 '                || null                        | null
            '[<foo>]'                   | ' test1 '               || null                        | null
            //-----------------------------------------------------------------------------------------------------------------------------------//
            '<foo> <bar>'               | 'test1 test2'           || ['test1', 'test2']          | [foo: ['test1'], bar: ['test2']]
            '<foo> <bar>'               | ' test1 test2'          || null                        | null
            '<foo> <bar>'               | 'test1 test2 '          || null                        | null
            '<foo> <bar>'               | ' test1 test2 '         || null                        | null
            //-----------------------------------------------------------------------------------------------------------------------------------//
            '[<foo>] <bar>'             | 'test1'                 || ['test1']                   | [bar: ['test1']]
            '[<foo>] <bar>'             | ' test1'                || null                        | null
            '[<foo>] <bar>'             | 'test1 '                || null                        | null
            '[<foo>] <bar>'             | ' test1 '               || null                        | null
            '[<foo>] <bar>'             | 'test1 test2'           || ['test1', 'test2']          | [foo: ['test1'], bar: ['test2']]
            '[<foo>] <bar>'             | ' test1 test2'          || null                        | null
            '[<foo>] <bar>'             | 'test1 test2 '          || null                        | null
            '[<foo>] <bar>'             | ' test1 test2 '         || null                        | null
            //-----------------------------------------------------------------------------------------------------------------------------------//
            '<foo> [<bar>]'             | 'test1'                 || ['test1']                   | [foo: ['test1']]
            '<foo> [<bar>]'             | ' test1'                || null                        | null
            '<foo> [<bar>]'             | 'test1 '                || null                        | null
            '<foo> [<bar>]'             | ' test1 '               || null                        | null
            '<foo> [<bar>]'             | 'test1 test2'           || ['test1', 'test2']          | [foo: ['test1'], bar: ['test2']]
            '<foo> [<bar>]'             | ' test1 test2'          || null                        | null
            '<foo> [<bar>]'             | 'test1 test2 '          || null                        | null
            '<foo> [<bar>]'             | ' test1 test2 '         || null                        | null
            //-----------------------------------------------------------------------------------------------------------------------------------//
            '[<foo>] [<bar>]'           | ''                      || []                          | [:]
            '[<foo>] [<bar>]'           | ' '                     || null                        | null
            '[<foo>] [<bar>]'           | 'test1'                 || ['test1']                   | [foo: ['test1']]
            '[<foo>] [<bar>]'           | ' test1'                || null                        | null
            '[<foo>] [<bar>]'           | 'test1 '                || null                        | null
            '[<foo>] [<bar>]'           | ' test1 '               || null                        | null
            '[<foo>] [<bar>]'           | 'test1 test2'           || ['test1', 'test2']          | [foo: ['test1'], bar: ['test2']]
            '[<foo>] [<bar>]'           | ' test1 test2'          || null                        | null
            '[<foo>] [<bar>]'           | 'test1 test2 '          || null                        | null
            '[<foo>] [<bar>]'           | ' test1 test2 '         || null                        | null
            //-----------------------------------------------------------------------------------------------------------------------------------//
            '<foo> <bar> <baz>'         | 'test1 test2 test3'     || ['test1', 'test2', 'test3'] | [foo: ['test1'], bar: ['test2'], baz: ['test3']]
            '<foo> <bar> <baz>'         | ' test1 test2 test3'    || null                        | null
            '<foo> <bar> <baz>'         | 'test1 test2 test3 '    || null                        | null
            '<foo> <bar> <baz>'         | ' test1 test2 test3 '   || null                        | null
            //-----------------------------------------------------------------------------------------------------------------------------------//
            '[<foo>] <bar> <baz>'       | 'test1 test2'           || ['test1', 'test2']          | [bar: ['test1'], baz: ['test2']]
            '[<foo>] <bar> <baz>'       | ' test1 test2'          || null                        | null
            '[<foo>] <bar> <baz>'       | 'test1 test2 '          || null                        | null
            '[<foo>] <bar> <baz>'       | ' test1 test2 '         || null                        | null
            '[<foo>] <bar> <baz>'       | 'test1 test2 test3'     || ['test1', 'test2', 'test3'] | [foo: ['test1'], bar: ['test2'], baz: ['test3']]
            '[<foo>] <bar> <baz>'       | ' test1 test2 test3'    || null                        | null
            '[<foo>] <bar> <baz>'       | 'test1 test2 test3 '    || null                        | null
            '[<foo>] <bar> <baz>'       | ' test1 test2 test3 '   || null                        | null
            //-----------------------------------------------------------------------------------------------------------------------------------//
            '<foo> [<bar>] <baz>'       | 'test1 test2'           || ['test1', 'test2']          | [foo: ['test1'], baz: ['test2']]
            '<foo> [<bar>] <baz>'       | ' test1 test2'          || null                        | null
            '<foo> [<bar>] <baz>'       | 'test1 test2 '          || null                        | null
            '<foo> [<bar>] <baz>'       | ' test1 test2 '         || null                        | null
            '<foo> [<bar>] <baz>'       | 'test1 test2 test3'     || ['test1', 'test2', 'test3'] | [foo: ['test1'], bar: ['test2'], baz: ['test3']]
            '<foo> [<bar>] <baz>'       | ' test1 test2 test3'    || null                        | null
            '<foo> [<bar>] <baz>'       | 'test1 test2 test3 '    || null                        | null
            '<foo> [<bar>] <baz>'       | ' test1 test2 test3 '   || null                        | null
            //-----------------------------------------------------------------------------------------------------------------------------------//
            '<foo> <bar> [<baz>]'       | 'test1 test2'           || ['test1', 'test2']          | [foo: ['test1'], bar: ['test2']]
            '<foo> <bar> [<baz>]'       | ' test1 test2'          || null                        | null
            '<foo> <bar> [<baz>]'       | 'test1 test2 '          || null                        | null
            '<foo> <bar> [<baz>]'       | ' test1 test2 '         || null                        | null
            '<foo> <bar> [<baz>]'       | 'test1 test2 test3'     || ['test1', 'test2', 'test3'] | [foo: ['test1'], bar: ['test2'], baz: ['test3']]
            '<foo> <bar> [<baz>]'       | ' test1 test2 test3'    || null                        | null
            '<foo> <bar> [<baz>]'       | 'test1 test2 test3 '    || null                        | null
            '<foo> <bar> [<baz>]'       | ' test1 test2 test3 '   || null                        | null
            //-----------------------------------------------------------------------------------------------------------------------------------//
            '[<foo>] [<bar>] <baz>'     | 'test1'                 || ['test1']                   | [baz: ['test1']]
            '[<foo>] [<bar>] <baz>'     | ' test1'                || null                        | null
            '[<foo>] [<bar>] <baz>'     | 'test1 '                || null                        | null
            '[<foo>] [<bar>] <baz>'     | ' test1 '               || null                        | null
            '[<foo>] [<bar>] <baz>'     | 'test1 test2'           || ['test1', 'test2']          | [foo: ['test1'], baz: ['test2']]
            '[<foo>] [<bar>] <baz>'     | ' test1 test2'          || null                        | null
            '[<foo>] [<bar>] <baz>'     | 'test1 test2 '          || null                        | null
            '[<foo>] [<bar>] <baz>'     | ' test1 test2 '         || null                        | null
            '[<foo>] [<bar>] <baz>'     | 'test1 test2 test3'     || ['test1', 'test2', 'test3'] | [foo: ['test1'], bar: ['test2'], baz: ['test3']]
            '[<foo>] [<bar>] <baz>'     | ' test1 test2 test3'    || null                        | null
            '[<foo>] [<bar>] <baz>'     | 'test1 test2 test3 '    || null                        | null
            '[<foo>] [<bar>] <baz>'     | ' test1 test2 test3 '   || null                        | null
            //-----------------------------------------------------------------------------------------------------------------------------------//
            '[<foo>] <bar> [<baz>]'     | 'test1'                 || ['test1']                   | [bar: ['test1']]
            '[<foo>] <bar> [<baz>]'     | ' test1'                || null                        | null
            '[<foo>] <bar> [<baz>]'     | 'test1 '                || null                        | null
            '[<foo>] <bar> [<baz>]'     | ' test1 '               || null                        | null
            '[<foo>] <bar> [<baz>]'     | 'test1 test2'           || ['test1', 'test2']          | [foo: ['test1'], bar: ['test2']]
            '[<foo>] <bar> [<baz>]'     | ' test1 test2'          || null                        | null
            '[<foo>] <bar> [<baz>]'     | 'test1 test2 '          || null                        | null
            '[<foo>] <bar> [<baz>]'     | ' test1 test2 '         || null                        | null
            '[<foo>] <bar> [<baz>]'     | 'test1 test2 test3'     || ['test1', 'test2', 'test3'] | [foo: ['test1'], bar: ['test2'], baz: ['test3']]
            '[<foo>] <bar> [<baz>]'     | ' test1 test2 test3'    || null                        | null
            '[<foo>] <bar> [<baz>]'     | 'test1 test2 test3 '    || null                        | null
            '[<foo>] <bar> [<baz>]'     | ' test1 test2 test3 '   || null                        | null
            //-----------------------------------------------------------------------------------------------------------------------------------//
            '<foo> [<bar>] [<baz>]'     | 'test1'                 || ['test1']                   | [foo: ['test1']]
            '<foo> [<bar>] [<baz>]'     | ' test1'                || null                        | null
            '<foo> [<bar>] [<baz>]'     | 'test1 '                || null                        | null
            '<foo> [<bar>] [<baz>]'     | ' test1 '               || null                        | null
            '<foo> [<bar>] [<baz>]'     | 'test1 test2'           || ['test1', 'test2']          | [foo: ['test1'], bar: ['test2']]
            '<foo> [<bar>] [<baz>]'     | ' test1 test2'          || null                        | null
            '<foo> [<bar>] [<baz>]'     | 'test1 test2 '          || null                        | null
            '<foo> [<bar>] [<baz>]'     | ' test1 test2 '         || null                        | null
            '<foo> [<bar>] [<baz>]'     | 'test1 test2 test3'     || ['test1', 'test2', 'test3'] | [foo: ['test1'], bar: ['test2'], baz: ['test3']]
            '<foo> [<bar>] [<baz>]'     | ' test1 test2 test3'    || null                        | null
            '<foo> [<bar>] [<baz>]'     | 'test1 test2 test3 '    || null                        | null
            '<foo> [<bar>] [<baz>]'     | ' test1 test2 test3 '   || null                        | null
            //-----------------------------------------------------------------------------------------------------------------------------------//
            '[<foo>] [<bar>] [<baz>]'   | ''                      || []                          | [:]
            '[<foo>] [<bar>] [<baz>]'   | ' '                     || null                        | null
            '[<foo>] [<bar>] [<baz>]'   | 'test1'                 || ['test1']                   | [foo: ['test1']]
            '[<foo>] [<bar>] [<baz>]'   | ' test1'                || null                        | null
            '[<foo>] [<bar>] [<baz>]'   | 'test1 '                || null                        | null
            '[<foo>] [<bar>] [<baz>]'   | ' test1 '               || null                        | null
            '[<foo>] [<bar>] [<baz>]'   | 'test1 test2'           || ['test1', 'test2']          | [foo: ['test1'], bar: ['test2']]
            '[<foo>] [<bar>] [<baz>]'   | ' test1 test2'          || null                        | null
            '[<foo>] [<bar>] [<baz>]'   | 'test1 test2 '          || null                        | null
            '[<foo>] [<bar>] [<baz>]'   | ' test1 test2 '         || null                        | null
            '[<foo>] [<bar>] [<baz>]'   | 'test1 test2 test3'     || ['test1', 'test2', 'test3'] | [foo: ['test1'], bar: ['test2'], baz: ['test3']]
            '[<foo>] [<bar>] [<baz>]'   | ' test1 test2 test3'    || null                        | null
            '[<foo>] [<bar>] [<baz>]'   | 'test1 test2 test3 '    || null                        | null
            '[<foo>] [<bar>] [<baz>]'   | ' test1 test2 test3 '   || null                        | null
            //-----------------------------------------------------------------------------------------------------------------------------------//
            /<foo> '|' <bar>/           | 'test1 | test2'         || ['test1', '|', 'test2']     | [foo: ['test1'], '|': ['|'], bar: ['test2']]

        and:
            match = (groups == null) ? 'not match' : "match to $tokenValues"
    }

    def 'usage "#usage" should throw exception'() {
        when:
            def usageLexer = new UsageLexer(CharStreams.fromString(usage))
            def usageParser = new UsageParser(new CommonTokenStream(usageLexer))
            usageParser.usage()

        then:
            thrown(IllegalArgumentException)

        where:
            usage             | _
            ''                | _
            'foo'             | _
            '<foo'            | _
            '<foo...'         | _
            /'foo/            | _
            /'foo bar'/       | _
            '[foo'            | _
            '[[foo]]'         | _
            '(foo|bar'        | _
            '(foo)'           | _
            '(foo|[bar])'     | _
            '(foo|(bar|baz))' | _
    }

    def 'unexpected usage parser rule context where all methods return empty values should throw exception'() {
        when:
            testee.getPattern(Stub(UsageContext))

        then:
            AssertionError ae = thrown()
            ae.message == 'Unhandled case'
    }

    def 'group names by token name should be filled after getPattern was called'() {
        given:
            def usageLexer = new UsageLexer(CharStreams.fromString('<foo>'))
            def usageParser = new UsageParser(new CommonTokenStream(usageLexer))
            def usageTree = usageParser.usage()

        expect:
            testee.getGroupNamesByTokenName(usageTree) == [:]

        when:
            testee.getPattern(usageTree)

        then:
            testee.getGroupNamesByTokenName(usageTree).containsKey('foo')
    }

    def 'group names should not start with null unless the token starts with null'() {
        given:
            def usageLexer = new UsageLexer(CharStreams.fromString('<foo>'))
            def usageParser = new UsageParser(new CommonTokenStream(usageLexer))
            def usageTree = usageParser.usage()
            testee.getPattern(usageTree)

        expect:
            !testee.getGroupNamesByTokenName(usageTree)
                    .any { key, value -> value.any { it.startsWith('null') } }
    }

    @Use(ContextualInstanceCategory)
    def 'temporary cache should be cleared at the end of processing'() {
        given:
            def usageLexer = new UsageLexer(CharStreams.fromString('<foo>'))
            def usageParser = new UsageParser(new CommonTokenStream(usageLexer))
            def usageTree = usageParser.usage()

        when:
            testee.getPattern(usageTree)

        then:
            !testee.ci().groupNamesBySanitizedTokenNameByUsageContext
    }

    @Use(ContextualInstanceCategory)
    def 'toString should start with class name'() {
        expect:
            testee.toString().startsWith("${testee.ci().getClass().simpleName}[")
    }

    @Use(ContextualInstanceCategory)
    def 'toString should contain field name and value for "#field.name"'() {
        given:
            testee.ci().patternCache[Stub(UsageContext, name: 'foo')] = ~/[^\w\W]/
            testee.ci().groupNamesBySanitizedTokenNameByUsageContext[Stub(UsageContext, name: 'bar')] = [:]
            testee.ci().groupNamesByTokenNameByUsageContext[Stub(UsageContext, name: 'baz')] = [:]

        when:
            def toStringResult = testee.toString()

        then:
            toStringResult.contains("$field.name=")
            field.type == String ?
                    toStringResult.contains("'${field.get(testee.ci())}'") :
                    toStringResult.contains(field.get(testee.ci()).toString())

        where:
            field << getAllInstanceFields(newInstance(getField(getClass(), 'testee').type))
    }
}
