# C2PA Signing Server

A Ktor-based signing server for C2PA (Coalition for Content Provenance and Authenticity) that provides certificate signing and manifest signing services for Android applications.

## Features

- **Certificate Signing**: Sign Certificate Signing Requests (CSRs) with a test CA hierarchy
- **C2PA Manifest Signing**: Sign C2PA manifests for images with multiple signing methods
- **RESTful API**: Clean REST API compatible with the iOS signing server
- **Multiple Input Formats**: Support for both multipart/form-data and JSON requests
- **Built-in CA**: Test certificate authority for development and testing

## API Endpoints

### Health Check
```
GET /
GET /health
```

### Certificate Signing
```
POST /api/v1/certificates/sign
Content-Type: application/json

{
  "csr": "-----BEGIN CERTIFICATE REQUEST-----...",
  "metadata": {
    "deviceId": "device-123",
    "appVersion": "1.0.0",
    "purpose": "signing"
  }
}
```

### C2PA Manifest Signing

#### Multipart Request
```
POST /api/v1/c2pa/sign
Content-Type: multipart/form-data

Parts:
- request: JSON with manifestJSON and format
- image: Binary image data
```

#### JSON Request
```
POST /api/v1/c2pa/sign
Content-Type: application/json

{
  "manifestJSON": "{...}",
  "format": "image/jpeg",
  "imageData": "base64-encoded-image-data"
}
```

## Running the Server

### Development
```bash
cd signing-server
../gradlew run
```

The server will start on http://localhost:8080

### With Custom Port
```bash
PORT=9090 ../gradlew run
```

### Production Build
```bash
../gradlew installDist
./build/install/signing-server/bin/signing-server
```

## Configuration

Configuration can be set via environment variables:
- `PORT`: Server port (default: 8080)
- `HOST`: Server host (default: 0.0.0.0)

## Certificate Hierarchy

The server generates a test certificate hierarchy:
1. **Root CA**: 10-year validity, self-signed
2. **Intermediate CA**: 5-year validity, signed by Root CA
3. **End-Entity Certificates**: 1-year validity for CSRs, 1-day for temporary certificates

## Security Notes

⚠️ **WARNING**: This server uses test certificates and is intended for development and testing only. Do not use in production without proper certificate management and security measures.

## Dependencies

- Ktor 2.3.8 (Web framework)
- Bouncy Castle 1.77 (Cryptography)
- Kotlinx Serialization 1.6.3
- Kotlinx Coroutines 1.8.0
- C2PA Android Library (local dependency)

## Testing

### Using Make Commands

The easiest way to manage the signing server for testing is through the Makefile:

```bash
# Start the server
make signing-server-start

# Check server status
make signing-server-status

# View server logs
make signing-server-logs

# Stop the server
make signing-server-stop

# Run all tests with automatic server management
make tests-with-server
```

### Manual Testing

```bash
# Run tests
../gradlew test

# Test certificate signing
curl -X POST http://localhost:8080/api/v1/certificates/sign \
  -H "Content-Type: application/json" \
  -d '{"csr": "-----BEGIN CERTIFICATE REQUEST-----..."}'

# Test C2PA signing (JSON)
curl -X POST http://localhost:8080/api/v1/c2pa/sign \
  -H "Content-Type: application/json" \
  -d '{
    "manifestJSON": "{\"claim_generator\":\"TestApp/1.0\"}",
    "format": "image/jpeg",
    "imageData": "base64-image-data"
  }'
```

### Android Integration

The signing server is automatically used by the hardware signing tests in the Android test suite. When running tests that require hardware signing:

1. **For emulator**: The tests use `http://10.0.2.2:8080` to reach the host's localhost
2. **For physical device**: The tests use `http://localhost:8080` (ensure device can reach your machine)

The test suite will automatically skip hardware signing tests if the server is not available.
