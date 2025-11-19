#!/bin/bash
set -e
echo "=========================================="
echo "Starting DriveSaathi server..."
echo "=========================================="

# Get the script directory (repo root)
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR/docker/compose"

echo ""
echo "Starting Docker containers..."
docker-compose -f traccar-mysql.yaml up -d