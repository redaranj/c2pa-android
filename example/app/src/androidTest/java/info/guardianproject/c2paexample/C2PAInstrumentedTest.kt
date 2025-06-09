package info.guardianproject.c2paexample

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import com.adobe.c2pa.C2PA
import java.io.File

@RunWith(AndroidJUnit4::class)
class C2PAInstrumentedTest {
    
    @Test
    fun testC2PAVersion() {
        val version = C2PA.version()
        assertNotNull("C2PA version should not be null", version)
        assertTrue("C2PA version should not be empty", version.isNotEmpty())
        println("C2PA Version: $version")
    }
    
    @Test
    fun testC2PAError() {
        val error = C2PA.getError()
        assertNull("Initial error should be null", error)
    }
    
    @Test
    fun testLoadSettings() {
        val settings = """{"test": "value"}"""
        val result = C2PA.loadSettings(settings, "json")
        assertEquals("loadSettings should return 0 on success", 0, result)
    }
    
    @Test
    fun testReadFile() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Copy test image from raw resources to a file
        val testImageFile = File(appContext.cacheDir, "test_image.jpg")
        appContext.resources.openRawResource(R.raw.test_image).use { input ->
            testImageFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        // Test reading the manifest
        val manifest = C2PA.readFile(testImageFile.absolutePath)
        assertNotNull("Manifest should not be null", manifest)
        assertTrue("Manifest should be a non-empty JSON string", manifest!!.startsWith("{") || manifest.startsWith("["))
        println("Manifest: ${manifest.take(200)}...")
        
        // Check for error if any
        val error = C2PA.getError()
        assertNull("No error should occur when reading valid C2PA file", error)
    }
    
    @Test
    fun testReadIngredientFile() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Copy test image from raw resources to a file
        val testImageFile = File(appContext.cacheDir, "test_image.jpg")
        appContext.resources.openRawResource(R.raw.test_image).use { input ->
            testImageFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        // Test reading the ingredient
        val ingredient = C2PA.readIngredientFile(testImageFile.absolutePath)
        if (ingredient != null) {
            assertTrue("Ingredient should be a JSON string", ingredient.startsWith("{") || ingredient.startsWith("["))
            println("Ingredient: ${ingredient.take(200)}...")
        }
    }
    
    @Test
    fun testReadNonExistentFile() {
        val result = C2PA.readFile("/non/existent/file.jpg")
        assertNull("Reading non-existent file should return null", result)
        
        val error = C2PA.getError()
        assertNotNull("Error should be set when reading fails", error)
        println("Error message: $error")
    }
    
    @Test
    fun testManifestBuilder() {
        val builder = C2PA.ManifestBuilder()
        assertNotNull("ManifestBuilder should be created", builder)
        
        // Test adding an assertion
        val assertionJson = """{"test_assertion": true}"""
        builder.addAssertionJson("c2pa.test", assertionJson)
        
        // Test setting claim generator
        val generatorJson = """{"name": "Test Generator", "version": "1.0"}"""
        builder.setClaimGeneratorInfo(generatorJson)
        
        // Clean up
        builder.close()
    }
    
    @Test
    fun testSigner() {
        try {
            val signer = C2PA.Signer()
            assertNotNull("Signer should be created", signer)
            signer.close()
        } catch (e: Exception) {
            // It's OK if Signer creation fails due to missing certificates
            println("Signer creation failed (expected without certificates): ${e.message}")
        }
    }
}