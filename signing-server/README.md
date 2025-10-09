# C2PA Signing Server

Development/testing server for C2PA signing operations. Provides C2PA configuration, remote signing, and certificate signing for the Android test suite.

## Features

- **Bearer Token Authentication**: Secure API access with configurable bearer tokens
- **C2PA Configuration**: Provides signing configuration including algorithm, certificate chain, and timestamp URL
- **Remote Signing**: Signs C2PA manifest data with ECDSA (ES256)
- **Certificate Signing**: Issues certificates for CSRs (Certificate Signing Requests)

## API Endpoints

### Health Check
```
GET /
GET /health
```

Returns server status and version information.

### C2PA Configuration (Authenticated)
```
GET /api/v1/c2pa/configuration
Authorization: Bearer <token>
```

Returns:
```json
{
  "algorithm": "es256",
  "timestamp_url": "http://timestamp.digicert.com",
  "signing_url": "http://10.0.2.2:8080/api/v1/c2pa/sign",
  "certificate_chain": "base64-encoded-certificate-chain"
}
```

### C2PA Signing (Authenticated)
```
POST /api/v1/c2pa/sign
Authorization: Bearer <token>
Content-Type: application/json

{
  "claim": "base64-encoded-data-to-sign"
}
```

Returns:
```json
{
  "signature": "base64-encoded-signature"
}
```

### Certificate Signing
```
POST /api/v1/certificates/sign
Content-Type: application/json

{
  "csr": "-----BEGIN CERTIFICATE REQUEST-----..."
}
```

## Configuration

The server requires the following environment variables:

- **BEARER_TOKEN**: Token for authenticating C2PA API requests (default: none, auth disabled)
- **SIGNING_SERVER_URL**: Public URL where the server is accessible (required for configuration endpoint)

For testing with Android emulator:
```bash
BEARER_TOKEN=test-12345 SIGNING_SERVER_URL=http://10.0.2.2:8080
```

## Running the Server

### Using Make (Recommended)

```bash
# From project root:
make signing-server-start   # Start in background (auto-configured for emulator)
make signing-server-stop    # Stop server
make signing-server-status  # Check if running
make signing-server-logs    # View logs
```

### Manual Start

```bash
# Start in foreground
cd signing-server
BEARER_TOKEN=test-12345 SIGNING_SERVER_URL=http://10.0.2.2:8080 ../gradlew run

# Start in background
BEARER_TOKEN=test-12345 SIGNING_SERVER_URL=http://10.0.2.2:8080 \
  nohup ../gradlew run > signing-server.log 2>&1 &
```

Server starts on `http://0.0.0.0:8080` by default.

## Testing

Run the complete test suite with automatic server management:

```bash
make tests-with-server      # Starts server, runs tests, stops server
```

Or manually:

```bash
make signing-server-start   # Start server
./gradlew :library:connectedDebugAndroidTest  # Run tests
make signing-server-stop    # Stop server
```

## Network Access

- **Android Emulator**: Use `http://10.0.2.2:8080` (emulator's special alias for host)
- **Physical Device**: Ensure device can reach host machine's network
- **Host Machine**: Use `http://localhost:8080`

## Implementation Notes

- Uses Ktor framework for the web server
- BouncyCastle provider for cryptographic operations
- Automatic DER-to-raw signature conversion for ECDSA algorithms
- Bearer token authentication via Ktor's authentication plugin
- Thread-safe signing operations
