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

import geb.Page

class LoginPage extends Page {
    static url = 'login'

    static at = {
        $('div', class: contains('mainLoginContainer'))
    }

    static content = {
        username { $('input', name: 'email') }
        password { $('input', name: 'password') }
        loginButton { $('button', type: 'submit') }
        captchaFrame(required: false) { $('iframe', src: contains('captcha')) }
        newLoginDetected(required: false) { $('div', text: contains('check your e-mail')) }
    }
}
