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
import org.contentauth.c2pa.DigitalSourceType
import org.contentauth.c2pa.PredefinedAction

/**
 * Represents an action performed on the asset for use in manifest assertions.
 *
 * This is the serializable version of Action for use within AssertionDefinition.
 *
 * @property action The action name (use [PredefinedAction.value] or a custom action string).
 * @property digitalSourceType A URL identifying an IPTC digital source type.
 * @property softwareAgent The software or hardware used to perform the action.
 * @property parameters Additional information describing the action.
 * @property when The timestamp when the action was performed (ISO 8601 format).
 * @property changes Regions of interest describing what changed.
 * @property related Related ingredient labels.
 * @property reason The reason for performing the action.
 * @see AssertionDefinition
 * @see PredefinedAction
 */
@Serializable
data class ActionAssertion(
    val action: String,
    val digitalSourceType: String? = null,
    val softwareAgent: String? = null,
    val parameters: Map<String, String>? = null,
    @SerialName("when")
    val whenPerformed: String? = null,
    val changes: List<RegionOfInterest>? = null,
    val related: List<String>? = null,
    val reason: String? = null,
) {
    /**
     * Creates an action using a [PredefinedAction] and [DigitalSourceType].
     */
    constructor(
        action: PredefinedAction,
        digitalSourceType: DigitalSourceType? = null,
        softwareAgent: String? = null,
        parameters: Map<String, String>? = null,
        whenPerformed: String? = null,
        changes: List<RegionOfInterest>? = null,
        related: List<String>? = null,
        reason: String? = null,
    ) : this(
        action = action.value,
        digitalSourceType = digitalSourceType?.toIptcUrl(),
        softwareAgent = softwareAgent,
        parameters = parameters,
        whenPerformed = whenPerformed,
        changes = changes,
        related = related,
        reason = reason,
    )

    companion object {
        /**
         * Creates a "created" action with the specified digital source type.
         */
        fun created(
            digitalSourceType: DigitalSourceType,
            softwareAgent: String? = null,
        ) = ActionAssertion(
            action = PredefinedAction.CREATED,
            digitalSourceType = digitalSourceType,
            softwareAgent = softwareAgent,
        )

        /**
         * Creates an "edited" action.
         */
        fun edited(
            softwareAgent: String? = null,
            changes: List<RegionOfInterest>? = null,
        ) = ActionAssertion(
            action = PredefinedAction.EDITED,
            softwareAgent = softwareAgent,
            changes = changes,
        )
    }
}
