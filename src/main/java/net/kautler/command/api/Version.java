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

package net.kautler.command.api;

import javax.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Properties;
import java.util.StringJoiner;

import static java.lang.String.format;

/**
 * A class that provides information about the version of this library.
 */
@ApplicationScoped
public class Version {
    /**
     * The version of this library.
     *
     * @see #displayVersion
     */
    private final String version;

    /**
     * The commit ID from which this library was built.
     *
     * @see #displayVersion
     */
    private final String commitId;

    /**
     * The build timestamp at which this library was built.
     *
     * @see #displayVersion
     */
    private final Instant buildTimestamp;

    /**
     * The display version of this library.
     * If the current version is a release version, it is equal to {@link #version}.
     * If the current version is a snapshot version, it consists of the {@link #version}, the {@link #commitId},
     * and the {@link #buildTimestamp}.
     * For displaying the version somewhere it is best to use this constant.
     *
     * @see #version
     * @see #commitId
     * @see #buildTimestamp
     */
    private final String displayVersion;

    /**
     * Constructs a new instance of this class, initializing the version constants.
     */
    public Version() {
        Properties versionProperties = new Properties();
        try (InputStream versionPropertiesStream = Version.class.getResourceAsStream("../version.properties")) {
            versionProperties.load(versionPropertiesStream);
        } catch (IOException ignored) {
        }

        String version = versionProperties.getProperty("version", "$version");
        this.version = "$version".equals(version) ? "<unknown>" : version;

        String commitId = versionProperties.getProperty("commitId", "$commitId");
        this.commitId = "$commitId".equals(commitId) ? "<unknown>" : commitId;

        String buildTimestamp = versionProperties.getProperty("buildTimestamp", "$buildTimestamp");
        this.buildTimestamp = "$buildTimestamp".equals(buildTimestamp) ? null : Instant.parse(buildTimestamp);

        displayVersion = version.endsWith("-SNAPSHOT")
                ? format("%s [%s | %s]", version, commitId, buildTimestamp)
                : version;
    }

    /**
     * Returns the version of this library.
     *
     * @return the version of this library
     * @see #getDisplayVersion()
     */
    public String getVersion() {
        return version;
    }

    /**
     * Returns the commit ID from which this library was built.
     *
     * @return the commit ID from which this library was built
     * @see #getDisplayVersion()
     */
    public String getCommitId() {
        return commitId;
    }

    /**
     * Returns the build timestamp at which this library was built.
     *
     * @return the build timestamp at which this library was built
     * @see #getDisplayVersion()
     */
    public Instant getBuildTimestamp() {
        return buildTimestamp;
    }

    /**
     * Returns the display version of this library.
     * If the current version is a release version, it is equal to {@link #version}.
     * If the current version is a snapshot version, it consists of the {@link #version}, the {@link #commitId},
     * and the {@link #buildTimestamp}.
     * For displaying the version somewhere it is best to use this method.
     *
     * @return the display version of this library
     * @see #getVersion()
     * @see #getCommitId()
     * @see #getDisplayVersion()
     */
    public String getDisplayVersion() {
        return displayVersion;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Version.class.getSimpleName() + "[", "]")
                .add("version='" + version + "'")
                .add("commitId='" + commitId + "'")
                .add("buildTimestamp=" + buildTimestamp)
                .add("displayVersion='" + displayVersion + "'")
                .toString();
    }
}
