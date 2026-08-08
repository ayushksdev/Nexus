#!/bin/bash

# NEXUS Platform Shutdown Script
# Kills backend (8080), frontend (5173), and worker nodes (8081-8083)

echo "============================================="
echo "        STOPPING NEXUS PLATFORM"
echo "============================================="

# Function to kill process by port
kill_port() {
  PORT=$1
  NAME=$2
  PID=$(lsof -t -i:$PORT)
  if [ ! -z "$PID" ]; then
    echo "Stopping $NAME (PID: $PID) listening on port $PORT..."
    kill -15 $PID 2>/dev/null
    sleep 0.5
    # Force kill if still alive
    if ps -p $PID > /dev/null; then
      kill -9 $PID 2>/dev/null
    fi
    echo "✅ $NAME stopped."
  else
    echo "ℹ️ $NAME on port $PORT is not running."
  fi
}

# Kill frontend
kill_port 5173 "React Dashboard"

# Kill workers
kill_port 8081 "Worker-1"
kill_port 8082 "Worker-2"
kill_port 8083 "Worker-3"

# Kill backend
kill_port 8080 "Spring Boot Backend"

echo "---------------------------------------------"
echo "Cleanup completed."
echo "============================================="
