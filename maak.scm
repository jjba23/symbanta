;;; symbanta

;; Copyright © Josep Bigorra <jjbigorra@gmail.com>

;; symbanta is free software: you can redistribute it and/or modify
;; it under the terms of the GNU Affero General Public License as published by
;; the Free Software Foundation, either version 3 of the License, or
;; (at your option) any later version.

;; symbanta is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;; GNU Affero General Public License for more details.

;; You should have received a copy of the GNU Affero General Public License
;; along with symbanta.  If not, see <https://www.gnu.org/licenses/>.

(define-module (maak)
  #:use-module (maak maak)
  #:declarative? #t)

;; ====== Configuration ======

(define http-port
  8080)

;; The flake is a flake, so Nix has to have its experimental features turned on
;; even on a machine whose `nix.conf' does not enable them.  The flag is a
;; global Nix option that `nix-develop' has no room for, so the setting travels
;; as the `NIX_CONFIG' the command runs with.  The value keeps its inner quotes
;; because the helper ends in a shell line.
(define nix-flakes-env
  '("NIX_CONFIG='experimental-features = nix-command flakes'"))

;; ====== Helpers ======

(define (mvn goal)
  "Run a Maven GOAL inside the Nix development environment."
  (nix-develop (list "bash" "-c" (~ "'./mvnw ~a'" goal))
               #:env nix-flakes-env))

(define (run-server)
  "Run the Spring Boot development server."
  (mvn "spring-boot:run"))

(define (test)
  "Test the project."
  (mvn "test"))

;; ====== Maak tasks ======

(define (default)
  "Default task to be run, list available tasks."
  (list-tasks))

(define (fmt)
  "Format Kotlin source code."
  (mvn "ktlint:format"))

(define (lint)
  "Lint Kotlin source code."
  (fmt)
  (mvn "ktlint:check"))

(define (bench)
  "Run the JVM benchmarks (domain services and in-memory ingestion pipeline)."
  (run (list "./mvnw" "-q" "-DskipTests" "compile" "exec:java"
             "-Dexec.mainClass=org.jointhefreeworld.symbanta.benchmark.BenchmarksKt"
             "-Dexec.classpathScope=runtime")))

(define (bench-http)
  "Run the dependency-free HTTP load generator against a running server."
  (run (list "python3" "bench/http_load.py" "ingest"
             "--concurrency" "8" "--duration" "10")))

(define (kill-server)
  "Kill any running HTTP server in the same port."
  (run (list "fuser" "-k" (~ "~a/tcp" http-port))
       #:allow-failure? #t)
  (run (list "sleep" "2")))

(define (server)
  "Start the symbanta server with the default configuration."
  (kill-server)
  (run-server))

(define (production-server)
  "Start the symbanta server with the production configuration."
  (kill-server)
  (run-server))
