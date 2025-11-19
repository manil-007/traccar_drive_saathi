#!/bin/bash
set -e
echo "=========================================="
echo "Stopping DriveSaathi server..."
echo "=========================================="

# Get the script directory (repo root)
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR/docker/compose"

echo ""
echo "Stopping Docker containers..."
docker-compose -f traccar-mysql.yaml down

echo "=========================================="
echo "DriveSaathi server Stopped Successfully!"
echo "=========================================="
echo ""
echo "To start again: ./start.sh"
echo "To remove all data (including database): docker compose -f traccar-mysql.yaml down -v"
echo "To remove all data (including database): docker-compose -f traccar-mysql.yaml down -v"
echo ""