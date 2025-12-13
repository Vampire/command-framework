/*
 * Copyright 2025 Björn Kautler
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

package net.kautler.command.integ.test.discord

import geb.Browser
import geb.spock.GebSpec
import geb.spock.SpockGebTestManagerBuilder
import geb.test.GebTestManager

class DiscordGebSpec extends GebSpec {
    private final static GebTestManager TEST_MANAGER = new SpockGebTestManagerBuilder()
        .withBrowserCreator {
            Browser.drive(new Browser()) {
                via AppPage
                outer:
                    while (true) {
                        switch (page(AppPage, LoginPage)) {
                            case AppPage:
                                break outer

                            case LoginPage:
                                if (captchaFrame || newLoginDetected) {
                                    pause()
                                } else {
                                    username = System.properties.testDiscordUserEmail
                                    password = System.properties.testDiscordUserPassword
                                    loginButton.click()
                                    waitFor(noException: true) {
                                        !isAt(page, false) || captchaFrame || newLoginDetected
                                    }
                                }
                                break

                            default:
                                throw new AssertionError()
                        }
                    }
            }
        }
        .build()

    @Override
    @Delegate(includes = ['getBrowser'])
    GebTestManager getTestManager() {
        TEST_MANAGER
    }
}
