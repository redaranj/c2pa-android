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

package org.contentauth.c2pa.manifest

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import java.util.Base64

/**
 * A URI reference with an associated hash for integrity verification.
 *
 * The hash is held as raw bytes. In a manifest definition it is written as a JSON array of
 * byte values, which is the form c2pa-rs reads as bytes. A manifest read back from an asset
 * reports hashes as a string in `b64'...'` notation; that form is decoded to the same bytes.
 *
 * @property url The URL being referenced.
 * @property hash The hash bytes for integrity verification.
 * @property alg The algorithm used for hashing.
 * @see MetadataActor
 */
@Serializable
data class HashedUri(
    val url: String,
    @Serializable(with = HashBytesSerializer::class)
    val hash: ByteArray,
    val alg: String? = null,
) {

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is HashedUri && url == other.url && hash.contentEquals(other.hash) && alg == other.alg)

    override fun hashCode(): Int = 31 * (31 * url.hashCode() + hash.contentHashCode()) + (alg?.hashCode() ?: 0)

    override fun toString(): String =
        "HashedUri(url=$url, hash=${Base64.getEncoder().encodeToString(hash)}, alg=$alg)"
}

/**
 * Serializes hash bytes the way c2pa-rs exchanges them in JSON.
 *
 * Writes a JSON array of byte values (0 to 255). Reads that array, or a string in the
 * `b64'...'` notation c2pa-rs uses when it reports bytes. Any other string is rejected: c2pa-rs
 * would take it as the bytes of the string itself, which is never what a hash is meant to be.
 */
internal object HashBytesSerializer : KSerializer<ByteArray> {

    private const val NOTATION_PREFIX = "b64'"
    private const val NOTATION_SUFFIX = "'"
    private const val BYTE_MASK = 0xFF

    override val descriptor: SerialDescriptor = ByteArraySerializer().descriptor

    override fun serialize(encoder: Encoder, value: ByteArray) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("Hash bytes can only be serialized to JSON")
        jsonEncoder.encodeJsonElement(JsonArray(value.map { JsonPrimitive(it.toInt() and BYTE_MASK) }))
    }

    override fun deserialize(decoder: Decoder): ByteArray {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("Hash bytes can only be deserialized from JSON")
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonArray -> ByteArray(element.size) { index -> byteAt(element, index) }
            is JsonPrimitive -> fromNotation(element)
            else -> throw SerializationException("Expected hash bytes as an array or a b64'...' string")
        }
    }

    private fun byteAt(array: JsonArray, index: Int): Byte {
        val value = try {
            (array[index] as? JsonPrimitive)?.takeUnless { it.isString }?.int
        } catch (e: NumberFormatException) {
            null
        }
        if (value == null || value !in 0..BYTE_MASK) {
            throw SerializationException("Hash byte at index $index is not a number from 0 to 255")
        }
        return value.toByte()
    }

    private fun fromNotation(primitive: JsonPrimitive): ByteArray {
        val text = primitive.content
        val isNotation = primitive.isString &&
            text.length >= NOTATION_PREFIX.length + NOTATION_SUFFIX.length &&
            text.startsWith(NOTATION_PREFIX) &&
            text.endsWith(NOTATION_SUFFIX)
        if (!isNotation) {
            throw SerializationException("Expected hash bytes as an array or a b64'...' string")
        }
        val encoded = text.substring(NOTATION_PREFIX.length, text.length - NOTATION_SUFFIX.length)
        return try {
            Base64.getDecoder().decode(encoded)
        } catch (e: IllegalArgumentException) {
            throw SerializationException("Hash in b64'...' notation is not valid base64", e)
        }
    }
}
