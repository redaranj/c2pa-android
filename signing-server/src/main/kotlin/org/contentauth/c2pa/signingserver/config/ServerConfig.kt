package org.contentauth.c2pa.signingserver.config

import io.ktor.server.application.Application
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.config.tryGetString

data class ServerConfig(
    val port: Int = 8080,
    val host: String = "0.0.0.0",
    val environment: String = "development",
    val maxRequestSize: Long = 52428800L, // 50MB
    val apiKeyRequired: Boolean = false,
    val apiKey: String? = null,
    val corsAllowedHosts: List<String> = listOf("*"),
    val certificateConfig: CertificateConfig = CertificateConfig()
)

data class CertificateConfig(
    val useExternalCerts: Boolean = false,
    val certsPath: String? = null,
    val privateKeyPath: String? = null,
    val rootCAValidityDays: Int = 3650,       // 10 years
    val intermediateCAValidityDays: Int = 1825, // 5 years
    val endEntityValidityDays: Int = 365,      // 1 year
    val tempCertValidityDays: Int = 1          // 1 day
)

fun Application.loadServerConfig(): ServerConfig {
    val config = environment.config

    return ServerConfig(
        port = config.tryGetString("ktor.deployment.port")?.toIntOrNull() ?: 8080,
        host = config.tryGetString("ktor.deployment.host") ?: "0.0.0.0",
        environment = config.tryGetString("server.environment") ?: "development",
        maxRequestSize = config.tryGetString("server.maxRequestSize")?.toLongOrNull() ?: 52428800L,
        apiKeyRequired = config.tryGetString("security.apiKeyRequired")?.toBoolean() ?: false,
        apiKey = config.tryGetString("security.apiKey"),
        certificateConfig = CertificateConfig(
            useExternalCerts = config.tryGetString("certificates.useExternal")?.toBoolean()
                ?: false,
            certsPath = config.tryGetString("certificates.certsPath"),
            privateKeyPath = config.tryGetString("certificates.privateKeyPath"),
            rootCAValidityDays = config.tryGetString("certificates.rootCAValidityDays")
                ?.toIntOrNull() ?: 3650,
            intermediateCAValidityDays = config.tryGetString("certificates.intermediateCAValidityDays")
                ?.toIntOrNull() ?: 1825,
            endEntityValidityDays = config.tryGetString("certificates.endEntityValidityDays")
                ?.toIntOrNull() ?: 365,
            tempCertValidityDays = config.tryGetString("certificates.tempCertValidityDays")
                ?.toIntOrNull() ?: 1
        )
    )
}

private fun HoconApplicationConfig.tryGetString(path: String): String? {
    return try {
        propertyOrNull(path)?.getString()
    } catch (e: Exception) {
        null
    }
}
