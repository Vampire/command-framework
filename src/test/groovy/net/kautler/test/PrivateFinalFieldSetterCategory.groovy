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

package net.kautler.test

import java.lang.reflect.Field

import static java.lang.reflect.Modifier.FINAL
import static org.powermock.reflect.Whitebox.getField

// work-around for https://github.com/powermock/powermock/pull/1010
class PrivateFinalFieldSetterCategory {
    static setFinalBooleanField(Object object, String fieldName, boolean value) {
        getFinalFieldForSetting(object, fieldName).setBoolean(object, value)
    }

    static setFinalCharField(Object object, String fieldName, char value) {
        getFinalFieldForSetting(object, fieldName).setChar(object, value)
    }

    static setFinalByteField(Object object, String fieldName, byte value) {
        getFinalFieldForSetting(object, fieldName).setByte(object, value)
    }

    static setFinalShortField(Object object, String fieldName, short value) {
        getFinalFieldForSetting(object, fieldName).setShort(object, value)
    }

    static setFinalIntField(Object object, String fieldName, int value) {
        getFinalFieldForSetting(object, fieldName).setInt(object, value)
    }

    static setFinalLongField(Object object, String fieldName, long value) {
        getFinalFieldForSetting(object, fieldName).setLong(object, value)
    }

    static setFinalFloatField(Object object, String fieldName, float value) {
        getFinalFieldForSetting(object, fieldName).setFloat(object, value)
    }

    static setFinalDoubleField(Object object, String fieldName, double value) {
        getFinalFieldForSetting(object, fieldName).setDouble(object, value)
    }

    static setFinalField(Object object, String fieldName, Object value) {
        getFinalFieldForSetting(object, fieldName).set(object, value)
    }

    static setFinalBooleanField(Class clazz, String fieldName, boolean value) {
        getFinalFieldForSetting(clazz, fieldName).setBoolean(null, value)
    }

    static setFinalCharField(Class clazz, String fieldName, char value) {
        getFinalFieldForSetting(clazz, fieldName).setChar(null, value)
    }

    static setFinalByteField(Class clazz, String fieldName, byte value) {
        getFinalFieldForSetting(clazz, fieldName).setByte(null, value)
    }

    static setFinalShortField(Class clazz, String fieldName, short value) {
        getFinalFieldForSetting(clazz, fieldName).setShort(null, value)
    }

    static setFinalIntField(Class clazz, String fieldName, int value) {
        getFinalFieldForSetting(clazz, fieldName).setInt(null, value)
    }

    static setFinalLongField(Class clazz, String fieldName, long value) {
        getFinalFieldForSetting(clazz, fieldName).setLong(null, value)
    }

    static setFinalFloatField(Class clazz, String fieldName, float value) {
        getFinalFieldForSetting(clazz, fieldName).setFloat(null, value)
    }

    static setFinalDoubleField(Class clazz, String fieldName, double value) {
        getFinalFieldForSetting(clazz, fieldName).setDouble(null, value)
    }

    static setFinalField(Class clazz, String fieldName, Object value) {
        getFinalFieldForSetting(clazz, fieldName).set(null, value)
    }

    static getFinalFieldForSetting(Object object, String fieldName) {
        getFinalFieldForSetting(object.getClass(), fieldName)
    }

    static getFinalFieldForSetting(Class clazz, String fieldName) {
        def field = getField(clazz, fieldName)
        def modifiers = Field.getDeclaredFields0(false).find { it.name == 'modifiers' }
        modifiers.accessible = true
        modifiers.setInt(field, field.modifiers & ~FINAL)
        field
    }
}
