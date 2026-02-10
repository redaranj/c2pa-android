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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Defines a C2PA manifest for content authenticity.
 *
 * ManifestDefinition is the root type for building C2PA manifests. It contains all the
 * information needed to create a signed manifest, including claims, assertions, and ingredients.
 *
 * ## Created vs Gathered Assertions (C2PA 2.3 Spec)
 *
 * The C2PA 2.3 specification distinguishes between two types of assertions:
 *
 * - **created_assertions** (the `assertions` field): Sourced from the claim generator, directly
 *   attributed to the signer. The spec states: "All created_assertions are attributed to the
 *   signer as the Trust Model is rooted in the trust of the signer."
 *
 * - **gathered_assertions** (the `gatheredAssertions` field): From other workflow components,
 *   explicitly NOT attributed to the signer. The manifest signer ensures their integrity but
 *   does not attest to their accuracy.
 *
 * ## CAWG Identity Assertions
 *
 * **Important**: Per the CAWG specification, CAWG identity assertions (`cawg.identity`) MUST be
 * included as gathered assertions, NOT created assertions. This is because they are signed by
 * a third party (identity claims aggregator or X.509 certificate holder), and the manifest
 * signer should not take attribution for them.
 *
 * ## Usage
 *
 * ```kotlin
 * val manifest = ManifestDefinition(
 *     title = "My Photo",
 *     claimGeneratorInfo = listOf(ClaimGeneratorInfo.fromContext(context)),
 *     assertions = listOf(
 *         AssertionDefinition.actions(listOf(
 *             ActionAssertion.created(DigitalSourceType.DIGITAL_CAPTURE)
 *         ))
 *     )
 * )
 *
 * // Convert to JSON for use with Builder
 * val json = manifest.toJson()
 * val builder = Builder.fromJson(json)
 * ```
 *
 * @property title The title of the asset.
 * @property claimGeneratorInfo Information about the software creating the claim.
 * @property assertions The list of created assertions in this manifest. These are attributed
 *                      to the signer.
 * @property gatheredAssertions The list of gathered assertions from other workflow components.
 *                               These are NOT attributed to the signer. CAWG identity assertions
 *                               MUST be placed here per spec.
 * @property ingredients The list of ingredients (parent assets) used in this manifest.
 * @property thumbnail Reference to a thumbnail image for this asset.
 * @property format The MIME type of the asset (e.g., "image/jpeg").
 * @property vendor An optional vendor identifier.
 * @property label An optional unique label for this manifest.
 * @property instanceId An optional instance identifier.
 * @property redactions A list of assertion URIs to redact from ingredients.
 * @see AssertionDefinition
 * @see Ingredient
 * @see ClaimGeneratorInfo
 */
@Serializable
data class ManifestDefinition(
    val title: String,
    @SerialName("claim_generator_info")
    val claimGeneratorInfo: List<ClaimGeneratorInfo>,
    /**
     * The claim version. Defaults to 2 for C2PA 2.x specification compliance.
     * Version 2 claims properly separate created_assertions from gathered_assertions.
     */
    @SerialName("claim_version")
    val claimVersion: Int = 2,
    val assertions: List<AssertionDefinition> = emptyList(),
    @SerialName("gathered_assertions")
    val gatheredAssertions: List<AssertionDefinition> = emptyList(),
    val ingredients: List<Ingredient> = emptyList(),
    val thumbnail: ResourceRef? = null,
    val format: String? = null,
    val vendor: String? = null,
    val label: String? = null,
    @SerialName("instance_id")
    val instanceId: String? = null,
    val redactions: List<String>? = null,
) {
    /**
     * Returns the base labels of assertions that should be placed in `created_assertions`.
     *
     * This method extracts unique base labels from the `assertions` field (not `gatheredAssertions`).
     * Use these labels when calling [org.contentauth.c2pa.Builder.fromJson] with the
     * `createdAssertionLabels` parameter to ensure proper assertion placement.
     *
     * @return A list of unique assertion base labels.
     */
    fun createdAssertionLabels(): List<String> =
        assertions.map { it.baseLabel() }.distinct()

    /**
     * Converts this manifest definition to a JSON string.
     *
     * The resulting JSON can be used with [org.contentauth.c2pa.Builder.fromJson].
     *
     * @return The manifest as a JSON string.
     */
    fun toJson(): String = json.encodeToString(this)

    /**
     * Converts this manifest definition to a pretty-printed JSON string.
     *
     * @return The manifest as a formatted JSON string.
     */
    fun toPrettyJson(): String = prettyJson.encodeToString(this)

    override fun toString(): String = toJson()

    companion object {
        private val json = Json {
            encodeDefaults = false
            ignoreUnknownKeys = true
        }

        private val prettyJson = Json {
            encodeDefaults = false
            ignoreUnknownKeys = true
            prettyPrint = true
        }

        /**
         * Parses a ManifestDefinition from a JSON string.
         *
         * @param jsonString The JSON string to parse.
         * @return The parsed ManifestDefinition.
         */
        fun fromJson(jsonString: String): ManifestDefinition =
            json.decodeFromString(jsonString)

        /**
         * Creates a minimal manifest definition for a newly created asset.
         *
         * @param title The title of the asset.
         * @param claimGeneratorInfo The claim generator info.
         * @param digitalSourceType The digital source type for the created action.
         */
        fun created(
            title: String,
            claimGeneratorInfo: ClaimGeneratorInfo,
            digitalSourceType: org.contentauth.c2pa.DigitalSourceType,
        ) = ManifestDefinition(
            title = title,
            claimGeneratorInfo = listOf(claimGeneratorInfo),
            assertions = listOf(
                AssertionDefinition.action(
                    ActionAssertion.created(digitalSourceType),
                ),
            ),
        )

        /**
         * Creates a manifest definition for an edited asset with a parent ingredient.
         *
         * @param title The title of the asset.
         * @param claimGeneratorInfo The claim generator info.
         * @param parentIngredient The parent ingredient that was edited.
         * @param editActions The list of edit actions performed.
         */
        fun edited(
            title: String,
            claimGeneratorInfo: ClaimGeneratorInfo,
            parentIngredient: Ingredient,
            editActions: List<ActionAssertion>,
        ) = ManifestDefinition(
            title = title,
            claimGeneratorInfo = listOf(claimGeneratorInfo),
            assertions = listOf(
                AssertionDefinition.actions(editActions),
            ),
            ingredients = listOf(parentIngredient),
        )

        /**
         * Creates a manifest definition with explicit created and gathered assertions.
         *
         * Use this when you need to include gathered assertions (assertions from other
         * workflow components that are NOT attributed to the signer).
         *
         * **CAWG Identity Assertions**: Per the CAWG spec, identity assertions (`cawg.identity`)
         * MUST be placed in `gatheredAssertions`, NOT in `createdAssertions`.
         *
         * @param title The title of the asset.
         * @param claimGeneratorInfo The claim generator info.
         * @param createdAssertions Assertions attributed to the signer.
         * @param gatheredAssertions Assertions NOT attributed to the signer (e.g., CAWG identity).
         * @param ingredients Optional list of ingredients.
         */
        fun withAssertions(
            title: String,
            claimGeneratorInfo: ClaimGeneratorInfo,
            createdAssertions: List<AssertionDefinition>,
            gatheredAssertions: List<AssertionDefinition> = emptyList(),
            ingredients: List<Ingredient> = emptyList(),
        ) = ManifestDefinition(
            title = title,
            claimGeneratorInfo = listOf(claimGeneratorInfo),
            assertions = createdAssertions,
            gatheredAssertions = gatheredAssertions,
            ingredients = ingredients,
        )

        /**
         * Creates a manifest definition with a CAWG identity assertion.
         *
         * This convenience method ensures the CAWG identity assertion is correctly placed
         * in `gatheredAssertions` as required by the CAWG specification.
         *
         * @param title The title of the asset.
         * @param claimGeneratorInfo The claim generator info.
         * @param createdAssertions Assertions attributed to the signer (actions, metadata, etc.).
         * @param cawgIdentityAssertion The CAWG identity assertion (will be added to gathered).
         * @param ingredients Optional list of ingredients.
         */
        fun withCawgIdentity(
            title: String,
            claimGeneratorInfo: ClaimGeneratorInfo,
            createdAssertions: List<AssertionDefinition>,
            cawgIdentityAssertion: AssertionDefinition,
            ingredients: List<Ingredient> = emptyList(),
        ) = ManifestDefinition(
            title = title,
            claimGeneratorInfo = listOf(claimGeneratorInfo),
            assertions = createdAssertions,
            gatheredAssertions = listOf(cawgIdentityAssertion),
            ingredients = ingredients,
        )
    }
}
