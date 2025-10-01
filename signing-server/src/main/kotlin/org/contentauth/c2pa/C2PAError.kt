package org.contentauth.c2pa

/**
 * Error model for C2PA operations
 */
sealed class C2PAError : Exception() {
    data class Api(override val message: String) : C2PAError() {
        override fun toString() = "C2PA-API error: $message"
    }
    
    object NilPointer : C2PAError() {
        override fun toString() = "Unexpected NULL pointer"
    }
    
    object Utf8 : C2PAError() {
        override fun toString() = "Invalid UTF-8 from C2PA"
    }
    
    data class Negative(val value: Long) : C2PAError() {
        override fun toString() = "C2PA negative status $value"
    }
}