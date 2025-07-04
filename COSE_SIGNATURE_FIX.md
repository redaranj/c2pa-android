# COSE Signature Format Fix

## Problem
The "Signer with Callback" test was failing with:
```
RuntimeException: Signature: internal error (COSE signature invalid)
```

## Root Cause
The C2PA library expects ECDSA signatures in **raw R,S format** for COSE (CBOR Object Signing and Encryption), but our SigningHelper was producing **DER-encoded signatures**.

### COSE vs DER Format:
- **DER Format**: `30 44 02 20 [32-byte R] 02 20 [32-byte S]` (with ASN.1 structure)
- **COSE Format**: `[32-byte R][32-byte S]` (raw concatenation)

## Solution
Updated `SigningHelper.signECDSA()` to:

1. **Generate DER signature** using standard Java crypto
2. **Parse DER structure** to extract R and S components
3. **Convert to raw format** by concatenating R and S
4. **Handle padding** to ensure correct component sizes

### Key Changes:

```kotlin
private fun convertDERToRawSignature(derSignature: ByteArray, hashAlgorithm: String): ByteArray {
    // Parse DER SEQUENCE { r INTEGER, s INTEGER }
    // Extract R and S components
    // Remove DER padding (leading zeros)
    // Pad to algorithm-specific size:
    //   - ES256 (P-256): 32 bytes each for R,S = 64 bytes total
    //   - ES384 (P-384): 48 bytes each for R,S = 96 bytes total  
    //   - ES512 (P-521): 66 bytes each for R,S = 132 bytes total
    // Return concatenated R + S
}
```

### Algorithm-Specific Sizes:
- **ES256 (P-256)**: 32-byte R + 32-byte S = 64 bytes
- **ES384 (P-384)**: 48-byte R + 48-byte S = 96 bytes
- **ES512 (P-521)**: 66-byte R + 66-byte S = 132 bytes

## Impact
- ✅ **Test 15 (Callback Signer)**: Now produces COSE-compatible signatures
- ✅ **Test 20 (Web Service Signer)**: Also benefits from correct signature format
- ✅ **Backward Compatible**: Falls back to DER if conversion fails
- ✅ **Standards Compliant**: Matches COSE signature format requirements

## Technical Details
The C2PA library uses COSE for digital signatures, which requires specific formatting:
- ECDSA signatures must be in raw format (IEEE P1363)
- No ASN.1/DER encoding wrapper
- Fixed-length R and S components based on curve size
- Big-endian byte order

This fix ensures our SigningHelper produces signatures that the C2PA library can properly validate and embed in C2PA manifests.