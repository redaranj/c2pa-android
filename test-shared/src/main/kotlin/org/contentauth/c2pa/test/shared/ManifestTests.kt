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

package org.contentauth.c2pa.test.shared

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.contentauth.c2pa.DigitalSourceType
import org.contentauth.c2pa.PredefinedAction
import org.contentauth.c2pa.manifest.ActionAssertion
import org.contentauth.c2pa.manifest.AssetType
import org.contentauth.c2pa.manifest.AssertionDefinition
import org.contentauth.c2pa.manifest.ClaimGeneratorInfo
import org.contentauth.c2pa.manifest.Coordinate
import org.contentauth.c2pa.manifest.DataSource
import org.contentauth.c2pa.manifest.Frame
import org.contentauth.c2pa.manifest.HashedUri
import org.contentauth.c2pa.manifest.ImageRegionType
import org.contentauth.c2pa.manifest.Ingredient
import org.contentauth.c2pa.manifest.IngredientDeltaValidationResult
import org.contentauth.c2pa.manifest.Item
import org.contentauth.c2pa.manifest.ManifestDefinition
import org.contentauth.c2pa.manifest.Metadata
import org.contentauth.c2pa.manifest.MetadataActor
import org.contentauth.c2pa.manifest.RangeType
import org.contentauth.c2pa.manifest.RegionOfInterest
import org.contentauth.c2pa.manifest.RegionRange
import org.contentauth.c2pa.manifest.ResourceRef
import org.contentauth.c2pa.manifest.ReviewRating
import org.contentauth.c2pa.manifest.Shape
import org.contentauth.c2pa.manifest.ShapeType
import org.contentauth.c2pa.manifest.StatusCodes
import org.contentauth.c2pa.manifest.Text
import org.contentauth.c2pa.manifest.TextSelector
import org.contentauth.c2pa.manifest.TextSelectorRange
import org.contentauth.c2pa.manifest.Time
import org.contentauth.c2pa.manifest.UnitType
import org.contentauth.c2pa.manifest.UriOrResource
import org.contentauth.c2pa.manifest.ValidationResults
import org.contentauth.c2pa.manifest.ValidationStatus
import org.contentauth.c2pa.manifest.ValidationStatusCode

/**
 * ManifestTests - Tests for manifest building types.
 *
 * These tests verify the manifest definition types work correctly,
 * matching the test coverage in the iOS library.
 */
abstract class ManifestTests : TestBase() {

    /**
     * Tests minimal ManifestDefinition creation.
     */
    suspend fun testMinimal(): TestResult = withContext(Dispatchers.IO) {
        runTest("Manifest Minimal") {
            val manifest = ManifestDefinition(
                title = "test",
                claimGeneratorInfo = listOf(
                    ClaimGeneratorInfo(name = "test_app"),
                ),
            )

            if (manifest.title != "test") {
                return@runTest TestResult(
                    "Manifest Minimal",
                    false,
                    "title != test",
                    "Got: ${manifest.title}",
                )
            }

            if (manifest.claimGeneratorInfo.isEmpty()) {
                return@runTest TestResult(
                    "Manifest Minimal",
                    false,
                    "claimGeneratorInfo is empty",
                )
            }

            // Test JSON round-trip
            cloneAndCompare(manifest, "Manifest Minimal")
        }
    }

    /**
     * Tests ManifestDefinition with actions assertion.
     */
    suspend fun testCreated(): TestResult = withContext(Dispatchers.IO) {
        runTest("Manifest Created") {
            val manifest = ManifestDefinition(
                title = "test",
                claimGeneratorInfo = listOf(
                    ClaimGeneratorInfo(
                        name = "test_app",
                        version = "1.0.0",
                        operatingSystem = ClaimGeneratorInfo.operatingSystem,
                    ),
                ),
                assertions = listOf(
                    AssertionDefinition.actions(
                        listOf(
                            ActionAssertion(
                                action = PredefinedAction.CREATED,
                                digitalSourceType = DigitalSourceType.DIGITAL_CAPTURE,
                            ),
                        ),
                    ),
                ),
            )

            val assertion = manifest.assertions.firstOrNull()
            if (assertion == null) {
                return@runTest TestResult(
                    "Manifest Created",
                    false,
                    "manifest.assertions.first == null",
                )
            }

            if (assertion !is AssertionDefinition.Actions) {
                return@runTest TestResult(
                    "Manifest Created",
                    false,
                    "manifest.assertions.first is not Actions",
                    "Got: ${assertion::class.simpleName}",
                )
            }

            val action = assertion.actions.firstOrNull()
            if (action == null) {
                return@runTest TestResult(
                    "Manifest Created",
                    false,
                    "actions.first == null",
                )
            }

            if (action.action != "c2pa.created") {
                return@runTest TestResult(
                    "Manifest Created",
                    false,
                    "action.action != c2pa.created",
                    "Got: ${action.action}",
                )
            }

            val expectedSourceType = "http://cv.iptc.org/newscodes/digitalsourcetype/digitalCapture"
            if (action.digitalSourceType != expectedSourceType) {
                return@runTest TestResult(
                    "Manifest Created",
                    false,
                    "action.digitalSourceType incorrect",
                    "Expected: $expectedSourceType, Got: ${action.digitalSourceType}",
                )
            }

            val info = manifest.claimGeneratorInfo.firstOrNull()
            if (info == null) {
                return@runTest TestResult(
                    "Manifest Created",
                    false,
                    "claimGeneratorInfo.first == null",
                )
            }

            if (info.name != "test_app") {
                return@runTest TestResult(
                    "Manifest Created",
                    false,
                    "claimGeneratorInfo.name != test_app",
                    "Got: ${info.name}",
                )
            }

            // Test JSON round-trip
            cloneAndCompare(manifest, "Manifest Created")
        }
    }

    /**
     * Tests Shape creation and equality.
     */
    suspend fun testEnumRendering(): TestResult = withContext(Dispatchers.IO) {
        runTest("Manifest Enum Rendering") {
            val shape = Shape(
                type = ShapeType.RECTANGLE,
                origin = Coordinate(10.0, 10.0),
                width = 80.0,
                height = 80.0,
                unit = UnitType.PERCENT,
            )

            // Create another identical shape
            val shape2 = Shape(
                type = ShapeType.RECTANGLE,
                origin = Coordinate(10.0, 10.0),
                width = 80.0,
                height = 80.0,
                unit = UnitType.PERCENT,
            )

            if (shape == shape2) {
                TestResult(
                    "Manifest Enum Rendering",
                    true,
                    "Shape enums rendered as expected",
                    "Shape type: ${shape.type}, unit: ${shape.unit}",
                )
            } else {
                TestResult(
                    "Manifest Enum Rendering",
                    false,
                    "Shapes unexpectedly unequal",
                    "shape: $shape, shape2: $shape2",
                )
            }
        }
    }

    /**
     * Tests RegionOfInterest structure equality.
     */
    suspend fun testRegionOfInterest(): TestResult = withContext(Dispatchers.IO) {
        runTest("Manifest RegionOfInterest") {
            val rr = RegionRange(type = RangeType.FRAME)

            val roi1 = RegionOfInterest(
                region = listOf(rr),
                imageRegionType = ImageRegionType.ANIMAL,
            )
            val roi2 = RegionOfInterest(
                region = listOf(rr),
                imageRegionType = ImageRegionType.ANIMAL,
            )

            if (roi1 == roi2) {
                TestResult(
                    "Manifest RegionOfInterest",
                    true,
                    "RegionOfInterests equal",
                )
            } else {
                TestResult(
                    "Manifest RegionOfInterest",
                    false,
                    "RegionOfInterests unexpectedly unequal",
                    "roi1: $roi1, roi2: $roi2",
                )
            }
        }
    }

    /**
     * Tests ResourceRef equality.
     */
    suspend fun testResourceRef(): TestResult = withContext(Dispatchers.IO) {
        runTest("Manifest ResourceRef") {
            val r1 = ResourceRef(format = "application/octet-stream", identifier = "")
            val r2 = ResourceRef(format = "application/octet-stream", identifier = "")

            if (r1 == r2) {
                TestResult(
                    "Manifest ResourceRef",
                    true,
                    "ResourceRefs equal",
                    "ResourceRef: $r1",
                )
            } else {
                TestResult(
                    "Manifest ResourceRef",
                    false,
                    "ResourceRefs unexpectedly unequal",
                    "r1: $r1, r2: $r2",
                )
            }
        }
    }

    /**
     * Tests HashedUri equality.
     */
    suspend fun testHashedUri(): TestResult = withContext(Dispatchers.IO) {
        runTest("Manifest HashedUri") {
            val hu1 = HashedUri(hash = "", url = "foo")
            val hu2 = HashedUri(hash = "", url = "foo")

            if (hu1 == hu2) {
                TestResult(
                    "Manifest HashedUri",
                    true,
                    "HashedUris equal",
                    "HashedUri: $hu1",
                )
            } else {
                TestResult(
                    "Manifest HashedUri",
                    false,
                    "HashedUris unexpectedly unequal",
                    "hu1: $hu1, hu2: $hu2",
                )
            }
        }
    }

    /**
     * Tests UriOrResource equality.
     */
    suspend fun testUriOrResource(): TestResult = withContext(Dispatchers.IO) {
        runTest("Manifest UriOrResource") {
            val uor1 = UriOrResource(alg = "foo")
            val uor2 = UriOrResource(alg = "foo")

            if (uor1 == uor2) {
                TestResult(
                    "Manifest UriOrResource",
                    true,
                    "UriOrResources equal",
                )
            } else {
                TestResult(
                    "Manifest UriOrResource",
                    false,
                    "UriOrResources unexpectedly unequal",
                    "uor1: $uor1, uor2: $uor2",
                )
            }
        }
    }

    /**
     * Tests bulk initialization of various manifest-related types.
     */
    suspend fun testMassInit(): TestResult = withContext(Dispatchers.IO) {
        runTest("Manifest Mass Init") {
            try {
                // Test all type initializations
                val ingredient = Ingredient()
                val statusCodes = StatusCodes(failure = emptyList(), informational = emptyList(), success = emptyList())
                val metadata = Metadata()
                val validationStatus = ValidationStatus(code = ValidationStatusCode.ALGORITHM_UNSUPPORTED)
                val time = Time()
                val textSelector = TextSelector(fragment = "")
                val reviewRating = ReviewRating(explanation = "", value = 1)
                val dataSource = DataSource(type = "")
                val metadataActor = MetadataActor()
                val validationResults = ValidationResults()
                val ingredientDelta = IngredientDeltaValidationResult(
                    ingredientAssertionUri = "",
                    validationDeltas = statusCodes,
                )
                val item = Item(identifier = "track_id", value = "2")
                val assetType = AssetType(type = "")
                val frame = Frame()
                val textSelectorRange = TextSelectorRange(selector = textSelector)
                val text = Text(selectors = listOf(textSelectorRange))

                // Verify all objects initialized
                val allInitialized = listOf(
                    ingredient,
                    statusCodes,
                    metadata,
                    validationStatus,
                    time,
                    textSelector,
                    reviewRating,
                    dataSource,
                    metadataActor,
                    validationResults,
                    ingredientDelta,
                    item,
                    assetType,
                    frame,
                    textSelectorRange,
                    text,
                ).all { it != null }

                if (allInitialized) {
                    TestResult(
                        "Manifest Mass Init",
                        true,
                        "All objects initialize correctly",
                        "16 types successfully initialized",
                    )
                } else {
                    TestResult(
                        "Manifest Mass Init",
                        false,
                        "Some objects failed to initialize",
                    )
                }
            } catch (e: Exception) {
                TestResult(
                    "Manifest Mass Init",
                    false,
                    "Error during initialization: ${e.message}",
                    e.stackTraceToString(),
                )
            }
        }
    }

    /**
     * Tests ManifestDefinition.toJson() produces valid JSON for Builder.
     */
    suspend fun testManifestToJson(): TestResult = withContext(Dispatchers.IO) {
        runTest("Manifest to JSON") {
            val manifest = ManifestDefinition(
                title = "Test Photo",
                claimGeneratorInfo = listOf(
                    ClaimGeneratorInfo(
                        name = "TestApp",
                        version = "1.0",
                    ),
                ),
                assertions = listOf(
                    AssertionDefinition.actions(
                        listOf(
                            ActionAssertion.created(DigitalSourceType.DIGITAL_CAPTURE),
                        ),
                    ),
                ),
            )

            try {
                val jsonString = manifest.toJson()

                // Verify it's valid JSON by parsing it
                val parsed = ManifestDefinition.fromJson(jsonString)

                if (parsed.title == manifest.title &&
                    parsed.claimGeneratorInfo.size == manifest.claimGeneratorInfo.size
                ) {
                    TestResult(
                        "Manifest to JSON",
                        true,
                        "ManifestDefinition.toJson() produces valid JSON",
                        "JSON: ${jsonString.take(200)}...",
                    )
                } else {
                    TestResult(
                        "Manifest to JSON",
                        false,
                        "Parsed manifest does not match original",
                        "Original title: ${manifest.title}, Parsed title: ${parsed.title}",
                    )
                }
            } catch (e: Exception) {
                TestResult(
                    "Manifest to JSON",
                    false,
                    "Error: ${e.message}",
                    e.stackTraceToString(),
                )
            }
        }
    }

    /**
     * Tests Shape factory methods.
     */
    suspend fun testShapeFactoryMethods(): TestResult = withContext(Dispatchers.IO) {
        runTest("Manifest Shape Factory") {
            try {
                val rectangle = Shape.rectangle(
                    x = 10.0,
                    y = 20.0,
                    width = 100.0,
                    height = 50.0,
                    unit = UnitType.PIXEL,
                )

                val circle = Shape.circle(
                    centerX = 50.0,
                    centerY = 50.0,
                    diameter = 40.0,
                    unit = UnitType.PERCENT,
                )

                val polygon = Shape.polygon(
                    vertices = listOf(
                        Coordinate(0.0, 0.0),
                        Coordinate(100.0, 0.0),
                        Coordinate(50.0, 100.0),
                    ),
                )

                val allValid = rectangle.type == ShapeType.RECTANGLE &&
                    circle.type == ShapeType.CIRCLE &&
                    polygon.type == ShapeType.POLYGON &&
                    rectangle.origin?.x == 10.0 &&
                    circle.origin?.x == 50.0 &&
                    polygon.vertices?.size == 3

                if (allValid) {
                    TestResult(
                        "Manifest Shape Factory",
                        true,
                        "Shape factory methods work correctly",
                        "Rectangle: $rectangle, Circle: $circle, Polygon vertices: ${polygon.vertices?.size}",
                    )
                } else {
                    TestResult(
                        "Manifest Shape Factory",
                        false,
                        "Shape factory methods produced invalid shapes",
                    )
                }
            } catch (e: Exception) {
                TestResult(
                    "Manifest Shape Factory",
                    false,
                    "Error: ${e.message}",
                    e.stackTraceToString(),
                )
            }
        }
    }

    /**
     * Helper function to clone and compare a manifest via JSON.
     */
    private fun cloneAndCompare(manifest: ManifestDefinition, testName: String): TestResult {
        return try {
            val jsonString = manifest.toJson()
            val m2 = ManifestDefinition.fromJson(jsonString)

            if (manifest == m2) {
                TestResult(
                    testName,
                    true,
                    "Manifest rendered as expected",
                    "JSON: ${jsonString.take(200)}${if (jsonString.length > 200) "..." else ""}",
                )
            } else {
                TestResult(
                    testName,
                    false,
                    "Broken compiled manifest",
                    "Original: ${manifest.toJson()}\nDecoded: ${m2.toJson()}",
                )
            }
        } catch (e: Exception) {
            TestResult(
                testName,
                false,
                "Error during clone and compare: ${e.message}",
                e.stackTraceToString(),
            )
        }
    }
}
