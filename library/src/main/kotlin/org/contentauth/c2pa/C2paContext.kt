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
 * C2PA Context for creating readers and builders with shared configuration.
 *
 * C2paContext wraps the native C2paContext struct and provides an immutable, shareable
 * configuration context. Once created, a context can be used to create multiple
 * [Reader] and [Builder] instances that share the same settings.
 *
 * ## Usage
 *
 * ### Default context
 * ```kotlin
 * val context = C2paContext()
 * val builder = Builder.fromContext(context)
 * val reader = Reader.fromContext(context)
 * context.close()
 * ```
 *
 * ### Custom settings
 * ```kotlin
 * val settings = C2paSettings()
 *     .updateFromString(settingsJson, "json")
 *
 * val context = C2paContext(settings)
 * settings.close()
 *
 * val builder = Builder.fromContext(context)
 *     .withDefinition(manifestJson)
 * ```
 *
 * ## Resource Management
 *
 * C2paContext implements [Closeable] and must be closed when done to free native resources.
 * The context can be closed after creating readers/builders from it.
 *
 * @property ptr Internal pointer to the native C2paContext instance
 * @see C2paSettings
 * @see Builder
 * @see Reader
 * @since 1.0.0
 */
class C2paContext : Closeable {

    internal var ptr: Long

    companion object {
        init {
            loadC2PALibraries()
        }

        @JvmStatic private external fun nativeNew(): Long
        @JvmStatic private external fun nativeNewWithSettings(settingsPtr: Long): Long
    }

    /**
     * Creates a context with default settings.
     *
     * @throws C2PAError.Api if the context cannot be created
     */
    @Throws(C2PAError::class)
    constructor() {
        ptr = nativeNew()
        if (ptr == 0L) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to create C2paContext")
        }
    }

    /**
     * Creates a context with custom settings.
     *
     * The settings are cloned internally, so the caller retains ownership of [settings].
     *
     * @param settings The settings to configure this context with
     * @throws C2PAError.Api if the context cannot be created with the given settings
     */
    @Throws(C2PAError::class)
    constructor(settings: C2paSettings) {
        ptr = nativeNewWithSettings(settings.ptr)
        if (ptr == 0L) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to create C2paContext with settings")
        }
    }

    override fun close() {
        if (ptr != 0L) {
            free(ptr)
            ptr = 0
        }
    }

    private external fun free(handle: Long)
}
