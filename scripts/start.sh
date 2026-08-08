#!/bin/bash

# NEXUS Platform Startup Script
# Starts backend (8080) and frontend (5173) in background

PROJECT_ROOT="/Users/mack/Desktop/Code/Intershala/Nexus"

echo "============================================="
echo "        STARTING NEXUS PLATFORM"
echo "============================================="

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
