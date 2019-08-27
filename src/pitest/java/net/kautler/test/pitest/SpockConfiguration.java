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

package net.kautler.test.pitest;

import org.pitest.help.PitHelpError;
import org.pitest.junit.JUnit4SuiteFinder;
import org.pitest.testapi.Configuration;
import org.pitest.testapi.TestGroupConfig;
import org.pitest.testapi.TestSuiteFinder;
import org.pitest.testapi.TestUnitFinder;

import java.util.Collection;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * A Spock configuration.
 */
public class SpockConfiguration implements Configuration {
    /**
     * The test group config.
     */
    private final TestGroupConfig config;

    /**
     * Runners that should be excluded.
     */
    private final Collection<String> excludedRunners;

    /**
     * Test methods that should be included.
     */
    private final Collection<String> includedTestMethods;

    /**
     * Constructs a new spock configuration.
     *
     * @param config              the test group config
     * @param excludedRunners     runners that should be excluded
     * @param includedTestMethods test methods that should be included
     */
    public SpockConfiguration(TestGroupConfig config,
                              Collection<String> excludedRunners,
                              Collection<String> includedTestMethods) {
        this.config = config;
        this.excludedRunners = excludedRunners;
        this.includedTestMethods = includedTestMethods;
    }

    @Override
    public TestSuiteFinder testSuiteFinder() {
        return new JUnit4SuiteFinder();
    }

    @Override
    public TestUnitFinder testUnitFinder() {
        return new SpockTestUnitFinder(config, excludedRunners, includedTestMethods);
    }

    @Override
    public Optional<PitHelpError> verifyEnvironment() {
        return Optional.empty();
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", SpockConfiguration.class.getSimpleName() + "[", "]")
                .add("config=" + config)
                .add("excludedRunners=" + excludedRunners)
                .add("includedTestMethods=" + includedTestMethods)
                .toString();
    }
}
