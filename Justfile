# Default task: List available recipes with descriptions
default:
    @just --list

# ------------------------------------------------------------------------------
# Development
# ------------------------------------------------------------------------------

# Compile project sources
compile:
    ./mvnw compile

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
    docker volume rm $(docker volume ls -q)

# ------------------------------------------------------------------------------
# Dev Helpers and Local Tests
# ------------------------------------------------------------------------------

serve-examples:
    darkhttpd ./examples --port 3001

dev-get-summary:
    curl --verbose "http://localhost:8080/v1/stats/summary?domain=jointhefreeworld.org"
