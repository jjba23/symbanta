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

;; ====== Helpers ======

(define (mvn goal)
  "Run a Maven GOAL inside the Nix development environment."
  ($ (list "nix --extra-experimental-features 'nix-command flakes' develop -c"
      (~ "bash -c './mvnw ~a'" goal))))

(define (run-server)
  "Run the Spring Boot development server."
  (mvn "spring-boot:run"))

(define (test)
  "Test the project."
  (mvn "test"))

;; ====== Maak tasks ======

(define (default)
  "Default task to be run, list available tasks."
  ($ '("maak --list")))

(define (fmt)
  "Format Kotlin source code."
  (mvn "ktlint:format"))

(define (lint)
  "Lint Kotlin source code."
  (fmt)
  (mvn "ktlint:check"))

(define (bench)
  "Run the JVM benchmarks (domain services and in-memory ingestion pipeline)."
  ($ '("./mvnw -q -DskipTests compile exec:java -Dexec.mainClass=org.jointhefreeworld.symbanta.benchmark.BenchmarksKt -Dexec.classpathScope=runtime")))

(define (bench-http)
  "Run the dependency-free HTTP load generator against a running server."
  ($ '("python3 bench/http_load.py ingest --concurrency 8 --duration 10")))

(define (kill-server)
  "Kill any running HTTP server in the same port."
  ($ (list (~ "fuser -k ~a/tcp || true" http-port)))
  ($ '("sleep 2")))

(define (server)
  "Start the symbanta server with the default configuration."
  (kill-server)
  (run-server))

(define (production-server)
  "Start the symbanta server with the production configuration."
  (kill-server)
  (run-server))
