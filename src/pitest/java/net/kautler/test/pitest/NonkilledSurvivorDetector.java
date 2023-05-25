/*
 * Copyright 2019-2023 Björn Kautler
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

import org.pitest.mutationtest.ClassMutationResults;
import org.pitest.mutationtest.MutationResult;
import org.pitest.mutationtest.MutationResultListener;

import java.util.StringJoiner;

import static java.text.MessageFormat.format;
import static org.pitest.mutationtest.DetectionStatus.KILLED;

/**
 * A mutation result listener that throws an exception at the end
 * if any non-killed survivors were encountered. This does not
 * count mutants in uncovered lines.
 *
 * <p>Unlike the built-in checks only KILLED are deemed non-survivors.
 * Other things like RUN_ERROR, MEMORY_ERROR, TIMED_OUT,
 * and so on are complained about using this listener, so they can either be
 * fixed or manually excluded. This way running saves time and increases
 * confidence in actually killing mutants by assertions.
 */
public class NonkilledSurvivorDetector implements MutationResultListener {
    /**
     * The amount of encountered non-killed survivors.
     */
    private int nonKilledSurvivors;

    @Override
    public void runStart() {
        nonKilledSurvivors = 0;
    }

    @Override
    public void handleMutationResult(ClassMutationResults results) {
        nonKilledSurvivors += results.getMutations()
                .stream()
                .map(MutationResult::getStatus)
                .filter(status -> (status != KILLED)
                        && status.isDetected())
                .count();
    }

    @Override
    public void runEnd() {
        if (nonKilledSurvivors > 0) {
            throw new IllegalStateException(format(
                    "{0, number} surviving mutant{0, choice, 1#| 2#s} discovered that {0, choice, 1#was| 2#were} not killed",
                    nonKilledSurvivors));
        }
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", NonkilledSurvivorDetector.class.getSimpleName() + "[", "]")
                .add("nonKilledSurvivors=" + nonKilledSurvivors)
                .toString();
    }
}
