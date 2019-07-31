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

package net.kautler

import org.kohsuke.github.GHMilestone
import org.kohsuke.github.GitHub

fun GHMilestone.updateTitle(title: String) {
    val requesterClass = Class.forName("org.kohsuke.github.Requester")

    val constructor = requesterClass.getDeclaredConstructor(GitHub::class.java)
    constructor.isAccessible = true
    var requester = constructor.newInstance(root)

    val withMethod = requesterClass.getDeclaredMethod("_with", String::class.java, Object::class.java)
    withMethod.isAccessible = true
    requester = withMethod.invoke(requester, "title", title)

    val methodMethod = requesterClass.getDeclaredMethod("method", String::class.java)
    methodMethod.isAccessible = true
    requester = methodMethod.invoke(requester, "PATCH")

    val getApiRouteMethod = GHMilestone::class.java.getDeclaredMethod("getApiRoute")
    getApiRouteMethod.isAccessible = true

    val toMethod = requesterClass.getDeclaredMethod("to", String::class.java)
    toMethod.isAccessible = true
    toMethod.invoke(requester, getApiRouteMethod.invoke(this))
}
