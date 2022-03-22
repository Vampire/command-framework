/*
 * Copyright 2020-2022 Björn Kautler
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

import jakarta.inject.Inject
import org.jboss.weld.junit4.WeldInitiator
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException

class ExceptionUtilTest extends Specification {
    @Rule
    WeldInitiator weld = WeldInitiator
            .from(ExceptionUtil)
            .inject(this)
            .build()

    @Inject
    @Subject
    ExceptionUtil testee

    def 'sneaky throw should throw given throwable'() {
        given:
            def exception = new Exception()

        when:
            testee.sneakyThrow(exception)

        then:
            Exception e = thrown()
            e.is(exception)
    }

    def 'unwrap throwable should return argument if not a supported exception'() {
        given:
            def exception = new Exception()

        expect:
            testee.unwrapThrowable(exception).is(exception)
    }

    def 'unwrap throwable should throw exception if null given'() {
        when:
            testee.unwrapThrowable(null)

        then:
            thrown(NullPointerException)
    }

    def 'unwrap throwable should unwrap completion exceptions'() {
        given:
            def exception = new Exception()

        expect:
            testee.unwrapThrowable(new CompletionException(exception)).is(exception)
    }

    def 'unwrap throwable should unwrap invocation target exceptions'() {
        given:
            def exception = new Exception()

        expect:
            testee.unwrapThrowable(new InvocationTargetException(exception)).is(exception)
    }

    def 'unwrap throwable should unwrap execution exceptions'() {
        given:
            def exception = new Exception()

        expect:
            testee.unwrapThrowable(new ExecutionException(exception)).is(exception)
    }

    def 'unwrap throwable should unwrap nested exceptions'() {
        given:
            def exception = new Exception()

        expect:
            testee.unwrapThrowable(
                    new ExecutionException(
                            new InvocationTargetException(
                                    new CompletionException(
                                            new ExecutionException(
                                                    new InvocationTargetException(
                                                            new CompletionException(exception)))))))
                    .is(exception)
    }

    def 'unwrap throwable should return bottom-most unwrappable exception if it has no cause'() {
        given:
            def exception = new ExecutionException(null as Throwable)

        expect:
            testee.unwrapThrowable(
                    new ExecutionException(
                            new InvocationTargetException(
                                    new CompletionException(
                                            new ExecutionException(
                                                    new InvocationTargetException(
                                                            new CompletionException(exception)))))))
                    .is(exception)
    }
}
