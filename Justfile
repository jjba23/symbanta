# ==============================================================================
# backend-Josep-Bigorra: Deliveries API
# ==============================================================================

# Default task: List available recipes with descriptions
default:
    @just --list

# ------------------------------------------------------------------------------
# Development
# ------------------------------------------------------------------------------

# Run the application locally
run:
    ./mvnw spring-boot:run

# Build the package (skip tests) and create artifact
build:
    ./mvnw clean package -DskipTests

# Perform full build, run tests, and create artifact
package:
    ./mvnw clean package

# Clean up target directories and build artifacts
clean:
    ./mvnw clean

# ------------------------------------------------------------------------------
# Code Quality & Testing (Kotlin)
# ------------------------------------------------------------------------------

# Run Kotlin code style checks (using ktlint)
lint:
    ./mvnw ktlint:check

# Auto-fix Kotlin code formatting issues
format:
    ./mvnw ktlint:format

# Execute unit and integration tests
test:
    ./mvnw test

# Run all quality checks (linting + test suite)
check: lint test

# ------------------------------------------------------------------------------
# Containers & Compose
# ------------------------------------------------------------------------------

# Build Docker image
docker-build tag="latest":
    docker build -t app:{{ tag }} .

# Spin up Docker Compose environment (with image rebuild)
up:
    docker compose up --build

# Tear down Docker Compose
down:
    docker compose down

# ------------------------------------------------------------------------------
# Local development helpers
# ------------------------------------------------------------------------------

API_HOST := "http://localhost:8080"

# Create a delivery with status IN_PROGRESS
create-delivery-in-progress:
    curl -i -X POST "{{ API_HOST }}/deliveries" \
      -H "Content-Type: application/json" \
      -d '{"vehicleId": "AHV-589", "address": "Example street 15A", "startedAt": "2026-07-28T12:45:34.678Z", "status": "IN_PROGRESS"}'

# Create a delivery with status DELIVERED
create-delivery-delivered:
    curl -i -X POST "{{ API_HOST }}/deliveries" \
      -H "Content-Type: application/json" \
      -d '{"vehicleId": "AHV-589", "address": "Example street 15A", "startedAt": "2026-07-28T10:00:00.000Z", "finishedAt": "2026-07-28T11:30:00.000Z", "status": "DELIVERED"}'

# TEST VALIDATION: Submit an invalid request (DELIVERED without finishedAt)
test-invalid-delivery:
    curl -i -X POST "{{ API_HOST }}/deliveries" \
      -H "Content-Type: application/json" \
      -d '{"vehicleId": "AHV-589", "address": "Example street 15A", "startedAt": "2026-07-28T12:45:34.678Z", "status": "DELIVERED"}'

# ------------------------------------------------------------------------------
# Invoices API
# ------------------------------------------------------------------------------

# Create invoice for delivery ID
create-invoice delivery_id="7167fc04-0625-49fc-98a9-8785a4a32b60":
    curl -i -X POST "{{ API_HOST }}/deliveries/invoice" \
      -H "Content-Type: application/json" \
      -d '{"deliveryIds": ["{{ delivery_id }}"]}'

# ------------------------------------------------------------------------------
# Summary API
# ------------------------------------------------------------------------------

# Fetch yesterday's business summary (Amsterdam time)
get-business-summary:
    curl -i -X GET "{{ API_HOST }}/deliveries/business-summary"
