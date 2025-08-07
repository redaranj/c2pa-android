#!/bin/bash

# Script to run the C2PA Signing Server

# Set environment variables
export JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 17)}
export SERVER_ENV=${SERVER_ENV:-development}
export PORT=${PORT:-8080}
export HOST=${HOST:-0.0.0.0}

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}Starting C2PA Signing Server${NC}"
echo -e "${YELLOW}Environment: $SERVER_ENV${NC}"
echo -e "${YELLOW}Port: $PORT${NC}"
echo -e "${YELLOW}Host: $HOST${NC}"

# Check if gradlew exists
if [ ! -f "../gradlew" ]; then
    echo -e "${RED}Error: gradlew not found in parent directory${NC}"
    exit 1
fi

# Build the project first
echo -e "${GREEN}Building the project...${NC}"
../gradlew :signing-server:build

if [ $? -ne 0 ]; then
    echo -e "${RED}Build failed${NC}"
    exit 1
fi

# Run the server
echo -e "${GREEN}Starting server...${NC}"
../gradlew :signing-server:run