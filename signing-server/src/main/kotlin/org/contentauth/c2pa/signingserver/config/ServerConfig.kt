package org.contentauth.c2pa.signingserver.config

data class ServerConfig(
    val port: Int = 8080,
    val host: String = "0.0.0.0",
    val maxRequestSize: Long = 52428800L // 50MB
)

data class CertificateConfig(
    val rootCAValidityDays: Int = 3650, // 10 years
    val intermediateCAValidityDays: Int = 1825, // 5 years
    val endEntityValidityDays: Int = 365, // 1 year
    val tempCertValidityDays: Int = 1 // 1 day
)
