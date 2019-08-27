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
import static org.pitest.mutationtest.DetectionStatus.NO_COVERAGE;

/**
 * A mutation result listener that throws an exception at the end
 * if any uncovered mutants were encountered.
 */
public class UncoveredDetector implements MutationResultListener {
    /**
     * The amount of encountered survivors.
     */
    private int uncovered;

    @Override
    public void runStart() {
        uncovered = 0;
    }

    @Override
    public void handleMutationResult(ClassMutationResults results) {
        uncovered += results.getMutations()
                .stream()
                .map(MutationResult::getStatus)
                .filter(NO_COVERAGE::equals)
                .count();
    }

    @Override
    public void runEnd() {
        if (uncovered > 0) {
            throw new IllegalStateException(format(
                    "{0, number} uncovered mutant{0, choice, 1#| 2#s} discovered",
                    uncovered));
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
        UncoveredDetector that = (UncoveredDetector) obj;
        return uncovered == that.uncovered;
    }

    @Override
    public int hashCode() {
        return Objects.hash(uncovered);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", UncoveredDetector.class.getSimpleName() + "[", "]")
                .add("uncovered=" + uncovered)
                .toString();
    }
}
