
#!/bin/bash

# DriveSaathi Server - build Script
# This script rebuilds the JAR, builds the Docker image, and starts all services

set -e  # Exit on any error

echo "=========================================="
echo "Starting build"
echo "=========================================="

# Get the script directory (repo root)
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# Check if Docker is running
echo ""
echo "Checking Docker status..."
if ! docker info > /dev/null 2>&1; then
      echo ""
      echo "❌ ERROR: Docker is not running!"
      echo ""
      echo "Please start Docker Desktop first:"
      echo "  - On Windows: Open Docker Desktop from Start Menu"
      echo "  - On Linux: sudo systemctl start docker"
      echo ""
      echo "Then run this script again: ./start.sh"
      echo ""
      exit 1
fi
echo "✅ Docker is running"

echo ""
echo "Step 1/4: Building JAR with Gradle..."
chmod +x gradlew
./gradlew assemble
if [ ! -f "target/tracker-server.jar" ]; then
      echo "ERROR: JAR file not found at target/tracker-server.jar"
      exit 1
fi

echo ""
echo "Step 2/4: Building Docker image..."
docker build -f docker/Dockerfile.app -t my-traccar-app:latest .

echo ""
echo "=========================================="
echo "✅ Docker image built successfully"
echo "=========================================="
echo ""