package org.contentauth.c2pa

import android.content.Context
import org.contentauth.c2pa.test.shared.TestBase
import java.io.File

object ResourceTestHelper {

    fun loadResourceAsBytes(resourceName: String): ByteArray {
        val sharedResource =
            TestBase.loadSharedResourceAsBytes("$resourceName.jpg")
                ?: TestBase.loadSharedResourceAsBytes("$resourceName.pem")
                ?: TestBase.loadSharedResourceAsBytes("$resourceName.key")

        return sharedResource ?: throw IllegalArgumentException("Resource not found: $resourceName")
    }

    fun loadResourceAsString(resourceName: String): String {
        val sharedResource =
            TestBase.loadSharedResourceAsString("$resourceName.jpg")
                ?: TestBase.loadSharedResourceAsString("$resourceName.pem")
                ?: TestBase.loadSharedResourceAsString("$resourceName.key")

        return sharedResource ?: throw IllegalArgumentException("Resource not found: $resourceName")
    }

    fun copyResourceToFile(context: Context, resourceName: String, fileName: String): File {
        val file = File(context.filesDir, fileName)
        val resourceBytes = loadResourceAsBytes(resourceName)
        file.writeBytes(resourceBytes)
        return file
    }
}
