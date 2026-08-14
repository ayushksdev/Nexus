#!/bin/bash

# NEXUS Platform Startup Script
# Starts backend (8080) and frontend (5173) in background

PROJECT_ROOT="/Users/mack/Desktop/Code/Intershala/Nexus"

echo "============================================="
echo "        STARTING NEXUS PLATFORM"
echo "============================================="

# 0. Ensure PostgreSQL is running
echo "Checking PostgreSQL database..."
if [ "$(docker ps -q -f name=nexus-postgres)" ]; then
  echo "✅ PostgreSQL Docker container is already running."
elif [ "$(docker ps -aq -f status=exited -f name=nexus-postgres)" ]; then
  echo "Starting existing PostgreSQL Docker container..."
  docker start nexus-postgres > /dev/null
  echo "✅ PostgreSQL Docker container started."
else
  echo "🚀 PostgreSQL Docker container 'nexus-postgres' not found. Creating a new one..."
  docker run --name nexus-postgres -e POSTGRES_DB=nexusdb -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 -d postgres:15-alpine > /dev/null
  echo "✅ PostgreSQL Docker database container created and started."
fi

# 1. Start Backend
echo "Building backend..."
cd "$PROJECT_ROOT/backend"
mvn clean package -DskipTests > /dev/null

if [ $? -ne 0 ]; then
  echo "❌ Error: Backend compilation failed!"
  exit 1
fi
echo "✅ Backend compiled successfully."

echo "Starting Spring Boot backend..."
java -jar target/nexus-backend-1.0.0.jar > "$PROJECT_ROOT/backend.log" 2>&1 &
BACKEND_PID=$!
echo "✅ Spring Boot started in background (PID: $BACKEND_PID). Logs at backend.log."

# 2. Start Frontend
echo "Starting React/Vite dashboard..."
cd "$PROJECT_ROOT/frontend"
npm run dev -- --port 5173 > "$PROJECT_ROOT/frontend.log" 2>&1 &
FRONTEND_PID=$!
echo "✅ React/Vite started in background (PID: $FRONTEND_PID). Logs at frontend.log."

echo "---------------------------------------------"
echo "Waiting for NEXUS backend to bind to port 8080..."
sleep 4

# Simple health check check
curl -s http://localhost:8080/actuator/health | grep -q "UP"
if [ $? -eq 0 ]; then
  echo "✅ NEXUS System is ONLINE and healthy."
else
  echo "⚠️ Warning: Backend has not responded to health checks yet. It may still be starting."
fi

echo "---------------------------------------------"
echo "🌐 Operator Dashboard: http://localhost:5173"
echo "🌐 Backend API Gateway: http://localhost:8080"
echo "---------------------------------------------"
echo "To stop the system, run: ./scripts/stop.sh"
echo "============================================="
