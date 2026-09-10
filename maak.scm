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

;; ====== Maak constants ======

;; The TCP port on which the web server will listen.
(define http-port
  8080)

;; ====== Maak tasks ======

(define (default)
  "Default task to be run, list available tasks."
  ($ '("maak --list")))

(define (fmt)
  "Format Scheme source code files according to the Guix style guide."
  ($ '("nix --extra-experimental-features 'nix-command flakes'"
                                 "develop -c" "bash -c './mvnw ktlint:format'")))

(define (kill-server)
  "Kill any running HTTP server in the same port."
  ($ (list (~ "fuser -k ~a/tcp || true" http-port)))
  ($ '("sleep 2")))

(define (server)
  "Target to start the symbanta server with the default configuration."
  (kill-server)
  ($ '("nix --extra-experimental-features 'nix-command flakes'"
                                 "develop -c"
                                 "bash -c './mvnw spring-boot:run'")))

(define (production-server)
  "Target to start the symbanta server with the production configuration."
  (kill-server)
  ($ '("nix --extra-experimental-features 'nix-command flakes'"
                                 "develop -c"
                                 "bash -c './mvnw spring-boot:run'")))
