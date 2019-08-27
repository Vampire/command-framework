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

import org.pitest.mutationtest.ClassMutationResults;
import org.pitest.mutationtest.MutationResult;
import org.pitest.mutationtest.MutationResultListener;

import java.util.Objects;
import java.util.StringJoiner;

import static java.text.MessageFormat.format;
import static org.pitest.mutationtest.DetectionStatus.KILLED;
import static org.pitest.mutationtest.DetectionStatus.NO_COVERAGE;

/**
 * A mutation result listener that throws an exception at the end
 * if any survivors were encountered. This does not count mutants
 * in uncovered lines.
 */
public class SurvivorDetector implements MutationResultListener {
    /**
     * The amount of encountered survivors.
     */
    private int survivors;

    @Override
    public void runStart() {
        survivors = 0;
    }

    @Override
    public void handleMutationResult(ClassMutationResults results) {
        survivors += results.getMutations()
                .stream()
                .map(MutationResult::getStatus)
                .filter(other -> other != KILLED
                        && other != NO_COVERAGE)
                .count();
    }

    @Override
    public void runEnd() {
        if (survivors > 0) {
            throw new IllegalStateException(format(
                    "{0, number} surviving mutant{0, choice, 1#| 2#s} discovered",
                    survivors));
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if ((obj == null) || (getClass() != obj.getClass())) {
            return false;
        }
        SurvivorDetector that = (SurvivorDetector) obj;
        return survivors == that.survivors;
    }

    @Override
    public int hashCode() {
        return Objects.hash(survivors);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", SurvivorDetector.class.getSimpleName() + "[", "]")
                .add("survivors=" + survivors)
                .toString();
    }
}
