#!/bin/bash

# Traccar Custom Backend - Stop Script
# This script stops all running services

set -e  # Exit on any error

echo "=========================================="
echo "Stopping Traccar Custom Backend"
echo "=========================================="

# Get the script directory (repo root)
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR/docker/compose"

echo ""
echo "Stopping Docker containers..."
docker compose -f traccar-mysql.yaml down

echo ""
echo "=========================================="
echo "Traccar Backend Stopped Successfully!"
echo "=========================================="
echo ""
echo "To start again: ./start.sh"
echo "To remove all data (including database): docker compose -f docker/compose/traccar-mysql.yaml down -v"
echo ""
