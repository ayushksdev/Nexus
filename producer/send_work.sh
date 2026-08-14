#!/bin/bash

# Simple job submission script for NEXUS

BACKEND_HOST=${BACKEND_HOST:-localhost}
PORT=${PORT:-8080}
JOB_ID=$1
JOB_TYPE=$2

if [ -z "$JOB_ID" ]; then
  JOB_ID="job-$(date +%s)"
fi

if [ -z "$JOB_TYPE" ]; then
  JOB_TYPE="SEND_EMAIL"
fi

echo "Submitting job to NEXUS..."
echo "ID: $JOB_ID"
echo "Type: $JOB_TYPE"
echo "URL: http://$BACKEND_HOST:$PORT/api/work"
echo "----------------------------------------"

curl -X POST http://$BACKEND_HOST:$PORT/api/work \
  -H "Content-Type: application/json" \
  -d "{
    \"id\": \"$JOB_ID\",
    \"type\": \"$JOB_TYPE\",
    \"payload\": {
      \"recipient\": \"reviewer@example.com\",
      \"subject\": \"NEXUS Test Work\",
      \"body\": \"This is a deterministic reliability verification job.\"
    },
    \"maxAttempts\": 5
  }"

echo ""
echo "----------------------------------------"
echo "Done."
