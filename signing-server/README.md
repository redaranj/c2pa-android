# C2PA Signing Server

Development/testing server for C2PA signing operations. Provides certificate signing and manifest signing for Android test suite.

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

```bash
# From project root:
make signing-server-run     # Run in foreground
# OR
make signing-server-start   # Run in background
```

Server starts on http://localhost:8080 (or set PORT environment variable)

## Testing

Use Make commands to manage the server:

```bash
make signing-server-start   # Start server
make signing-server-stop    # Stop server
make tests-with-server      # Run tests with server
```

**For Android emulator**: Tests use `http://10.0.2.2:8080`  
**For physical device**: Configure network access to host machine
