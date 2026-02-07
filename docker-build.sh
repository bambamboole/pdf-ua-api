#!/bin/bash
# Docker build script for PDF API

set -e

IMAGE_NAME="pdf-api"
TAG="${1:-latest}"
FULL_IMAGE="$IMAGE_NAME:$TAG"

echo "Building Docker image: $FULL_IMAGE"
echo "=========================================="

# Build the image
docker build -t "$FULL_IMAGE" .

echo ""
echo "✅ Image built successfully: $FULL_IMAGE"
echo ""
echo "To run the container:"
echo "  docker run -p 8080:8080 $FULL_IMAGE"
echo ""
echo "Or use docker-compose:"
echo "  docker-compose up"
echo ""
echo "To test the API:"
echo "  curl http://localhost:8080/health"
