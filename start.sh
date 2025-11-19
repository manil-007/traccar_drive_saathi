#!/bin/sh
echo "=========================================="
echo "Starting DriveSaathi server..."
echo "=========================================="

docker compose -f docker/compose/traccar-mysql.yaml up