#!/bin/bash

# NEXUS Platform Workspace Reset Script
# Stops everything, deletes database, logs, and worker state dumps

PROJECT_ROOT="/Users/mack/Desktop/Code/Intershala/Nexus"

echo "============================================="
echo "        RESETTING NEXUS PLATFORM"
echo "============================================="

# 1. Stop all services
if [ -f "$PROJECT_ROOT/scripts/stop.sh" ]; then
  sh "$PROJECT_ROOT/scripts/stop.sh"
else
  echo "stop.sh not found, skipping halt..."
fi

echo "Cleaning files..."

# 2. Reset PostgreSQL database container
echo "Recreating PostgreSQL Docker container..."
docker rm -f nexus-postgres 2>/dev/null || true
docker run --name nexus-postgres -e POSTGRES_DB=nexusdb -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 -d postgres:15-alpine
echo "✅ Recreated PostgreSQL Docker database."

# 3. Delete worker states
rm -f "$PROJECT_ROOT"/processed-work-*.json
rm -f "$PROJECT_ROOT"/backend/processed-work-*.json
echo "✅ Deleted worker local processed-work JSON caches."

# 4. Delete log files
rm -f "$PROJECT_ROOT"/*.log
rm -f "$PROJECT_ROOT"/backend/*.log
rm -f "$PROJECT_ROOT"/backend/worker-*.log
echo "✅ Deleted log files."

echo "---------------------------------------------"
echo "Reset completed. Ready for a clean start."
echo "============================================="
