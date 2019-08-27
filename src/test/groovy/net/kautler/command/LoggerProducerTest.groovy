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

package net.kautler.command

import org.apache.logging.log4j.Logger
import org.jboss.weld.junit4.WeldInitiator
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject

@Subject(LoggerProducer)
class LoggerProducerTest extends Specification {
    @Rule
    WeldInitiator weld = WeldInitiator
            .from(
                    SubLoggerConsumer,
                    LoggerProducer
            )
            .inject(this)
            .build()

    @Inject
    SubLoggerConsumer subLoggerConsumer

    @Inject
    @Internal
    Logger logger

    def 'logger should be produced for the class where the logger field is declared'() {
        expect:
            logger.name == getClass().canonicalName
            subLoggerConsumer.logger.name == LoggerConsumer.canonicalName
            subLoggerConsumer.subLogger.name == SubLoggerConsumer.canonicalName
    }

    static class LoggerConsumer {
        @Inject
        @Internal
        Logger logger
    }

    @ApplicationScoped
    static class SubLoggerConsumer extends LoggerConsumer {
        @Inject
        @Internal
        Logger subLogger
    }
}
