# symbanta

# Copyright © Josep Bigorra <jjbigorra@gmail.com>
#
# symbanta is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# symbanta is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with symbanta.  If not, see <https://www.gnu.org/licenses/>.
#

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
# Benchmarks (issue #3)
# ------------------------------------------------------------------------------

# Run the JVM micro-benchmarks and ingestion pipeline benchmark (no database needed)
bench:
    ./mvnw -q -DskipTests compile exec:java -Dexec.mainClass=org.jointhefreeworld.symbanta.benchmark.BenchmarksKt -Dexec.classpathScope=runtime

# Run the end-to-end ingestion load test (requires k6 and a running server)
bench-http base="http://localhost:8080" rate="200" duration="30s":
    BASE_URL={{base}} RATE={{rate}} DURATION={{duration}} k6 run bench/k6/ingestion-load.js

# Run the dashboard query load test (requires k6, a running server and seeded credentials)
bench-query base="http://localhost:8080" duration="30s":
    BASE_URL={{base}} DURATION={{duration}} k6 run bench/k6/query-load.js

# Dependency-free HTTP load generator (fallback when k6 is unavailable)
bench-load-ingest base="http://localhost:8080" domain="localhost" concurrency="8" duration="10":
    python3 bench/http_load.py ingest --base-url {{base}} --domain {{domain}} --concurrency {{concurrency}} --duration {{duration}}

bench-load-query base="http://localhost:8080" domain="localhost" concurrency="8" duration="10":
    python3 bench/http_load.py query --base-url {{base}} --domain {{domain}} --concurrency {{concurrency}} --duration {{duration}}

# ------------------------------------------------------------------------------
# Containers & Compose
# ------------------------------------------------------------------------------

# Build Podman image
podman-build tag="latest":
    podman build -t app:{{ tag }} .

# Spin up Podman Compose environment (with image rebuild)
up:
    podman compose up --build

# Tear down Podman Compose
down:
    podman compose down
    podman volume rm $(podman volume ls -q)

# ------------------------------------------------------------------------------
# Dev Helpers and Local Tests
# ------------------------------------------------------------------------------

serve-examples:
    darkhttpd ./examples --port 3001

dev-get-summary:
    curl --verbose "http://localhost:8080/v1/stats/summary?domain=jointhefreeworld.org"
