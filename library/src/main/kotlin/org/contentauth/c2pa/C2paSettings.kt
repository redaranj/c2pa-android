/*
This file is licensed to you under the Apache License, Version 2.0
(http://www.apache.org/licenses/LICENSE-2.0) or the MIT license
(http://opensource.org/licenses/MIT), at your option.

Unless required by applicable law or agreed to in writing, this software is
distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS OF
ANY KIND, either express or implied. See the LICENSE-MIT and LICENSE-APACHE
files for the specific language governing permissions and limitations under
each license.
*/

package org.contentauth.c2pa

import java.io.Closeable

/**
 * C2PA Settings for configuring context-based operations.
 *
 * C2paSettings wraps the native C2paSettings struct and provides a fluent API for
 * configuring settings that can be passed to [C2paContext].
 *
 * ## Usage
 *
 * ```kotlin
 * val settings = C2paSettings()
 *     .updateFromString("""{"version": 1, "builder": {"created_assertion_labels": ["c2pa.actions"]}}""", "json")
 *     .setValue("verify.verify_after_sign", "true")
 *
 * val context = C2paContext(settings)
 * settings.close() // settings can be closed after creating the context
 * ```
 *
 * ## Resource Management
 *
 * C2paSettings implements [Closeable] and must be closed when done to free native resources.
 *
 * @property ptr Internal pointer to the native C2paSettings instance
 * @see C2paContext
 * @since 1.0.0
 */
class C2paSettings : Closeable {

    internal var ptr: Long

    companion object {
        init {
            loadC2PALibraries()
        }

        @JvmStatic private external fun nativeNew(): Long
    }

    init {
        ptr = nativeNew()
        if (ptr == 0L) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to create C2paSettings")
        }
    }

    /**
     * Updates settings from a JSON or TOML string.
     *
     * @param settingsStr The settings string in JSON or TOML format
     * @param format The format of the string ("json" or "toml")
     * @return This settings instance for fluent chaining
     * @throws C2PAError.Api if the settings string is invalid
     */
    @Throws(C2PAError::class)
    fun updateFromString(settingsStr: String, format: String): C2paSettings {
        val result = updateFromStringNative(ptr, settingsStr, format)
        if (result < 0) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to update settings from string")
        }
        return this
    }

    /**
     * Sets a specific configuration value using dot notation.
     *
     * @param path Dot-separated path (e.g., "verify.verify_after_sign")
     * @param value JSON value as a string (e.g., "true", "\"ps256\"", "42")
     * @return This settings instance for fluent chaining
     * @throws C2PAError.Api if the path or value is invalid
     */
    @Throws(C2PAError::class)
    fun setValue(path: String, value: String): C2paSettings {
        val result = setValueNative(ptr, path, value)
        if (result < 0) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to set settings value")
        }
        return this
    }

    override fun close() {
        if (ptr != 0L) {
            free(ptr)
            ptr = 0
        }
    }

    private external fun free(handle: Long)
    private external fun updateFromStringNative(handle: Long, settingsStr: String, format: String): Int
    private external fun setValueNative(handle: Long, path: String, value: String): Int
}
