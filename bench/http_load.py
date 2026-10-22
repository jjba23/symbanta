#!/usr/bin/env python3
"""symbanta

Copyright © Josep Bigorra <jjbigorra@gmail.com>

symbanta is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

symbanta is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with symbanta.  If not, see <https://www.gnu.org/licenses/>.

Dependency-free HTTP load generator, a fallback for machines without k6.

    python3 bench/http_load.py ingest --concurrency 32 --duration 10
    python3 bench/http_load.py query --username admin --password password123 --duration 10

It reports achieved throughput and latency percentiles; use it together with the k6 scripts
(bench/k6) when k6 is available.
"""

from __future__ import annotations

import argparse
import http.client
import json
import statistics
import threading
import time
import uuid
from urllib.parse import urlencode, urlsplit

USER_AGENTS = [
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1",
    "Mozilla/5.0 (X11; Linux x86_64; rv:121.0) Gecko/20100101 Firefox/121.0",
]

STATS_ENDPOINTS = [
    "/v1/stats/summary",
    "/v1/stats/timeseries",
    "/v1/stats/routes",
    "/v1/stats/screen-sizes",
    "/v1/stats/user-agents",
    "/v1/stats/events",
    "/v1/stats/countries",
]


class Connection:
    """One keep-alive HTTP connection per worker thread."""

    def __init__(self, base_url: str):
        parts = urlsplit(base_url)
        self.host = parts.hostname or "localhost"
        self.port = parts.port or (443 if parts.scheme == "https" else 80)
        self.secure = parts.scheme == "https"
        self.connection: http.client.HTTPConnection | None = None

    def connect(self) -> http.client.HTTPConnection:
        if self.connection is None:
            factory = http.client.HTTPSConnection if self.secure else http.client.HTTPConnection
            self.connection = factory(self.host, self.port, timeout=10)
        return self.connection

    def reset(self) -> None:
        if self.connection is not None:
            self.connection.close()
            self.connection = None

    def request(self, method: str, path: str, body: bytes | None = None, headers: dict | None = None):
        started = time.perf_counter()
        connection = self.connect()
        try:
            connection.request(method, path, body=body, headers=headers or {})
            response = connection.getresponse()
            response.read()
        except Exception:
            self.reset()
            raise
        return response.status, time.perf_counter() - started


def percentile(values: list[float], fraction: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, int(round(fraction * (len(ordered) - 1))))
    return ordered[index]


def run_workers(
    worker,
    concurrency: int,
    duration: float,
) -> tuple[int, list[float], int]:
    stop_at = time.perf_counter() + duration
    latencies: list[float] = []
    errors = 0
    samples: list[str] = []
    lock = threading.Lock()

    def loop() -> None:
        nonlocal errors
        local_latencies: list[float] = []
        local_errors = 0
        local_samples: list[str] = []
        while time.perf_counter() < stop_at:
            try:
                local_latencies.append(worker())
            except Exception as exc:
                local_errors += 1
                if len(local_samples) < 3:
                    local_samples.append(f"{type(exc).__name__}: {exc}")
        with lock:
            latencies.extend(local_latencies)
            errors += local_errors
            samples.extend(local_samples)

    threads = [threading.Thread(target=loop, daemon=True) for _ in range(concurrency)]
    started = time.perf_counter()
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()
    elapsed = time.perf_counter() - started

    total = len(latencies) + errors
    print(f"requests      : {total}")
    print(f"errors        : {errors}")
    print(f"duration      : {elapsed:.2f}s")
    print(f"throughput    : {total / elapsed:,.0f} req/s")
    if latencies:
        milliseconds = [value * 1000 for value in latencies]
        print(f"latency p50   : {percentile(milliseconds, 0.50):>8,.1f} ms")
        print(f"latency p95   : {percentile(milliseconds, 0.95):>8,.1f} ms")
        print(f"latency p99   : {percentile(milliseconds, 0.99):>8,.1f} ms")
        print(f"latency mean  : {statistics.fmean(milliseconds):>8,.1f} ms")
    for sample in samples[:3]:
        print(f"sample error  : {sample}")
    return total, latencies, errors


def ingest_worker(base_url: str, domain: str, counter: dict):
    # http.client connections are not thread-safe: one keep-alive connection per worker thread.
    local = threading.local()

    def worker() -> float:
        if getattr(local, "connection", None) is None:
            local.connection = Connection(base_url)
        connection = local.connection

        counter["index"] += 1
        index = counter["index"]
        name = "signup-click" if index % 20 == 0 else ("heartbeat" if index % 5 == 0 else "page-view")
        payload = json.dumps(
            {
                "domain": domain,
                "name": name,
                "sessionId": f"load-{uuid.uuid4()}",
                "url": f"https://{domain}/bench/{index % 500}",
                "referrer": "https://search.example" if index % 3 == 0 else None,
                "screenWidth": 1920,
                "screenHeight": 1080,
                "language": "en-US",
                "userAgent": USER_AGENTS[index % len(USER_AGENTS)],
                "props": {"plan": "pro", "index": index},
            }
        ).encode()
        status, elapsed = connection.request(
            "POST",
            "/v1/events/register",
            body=payload,
            headers={
                "Content-Type": "application/json",
                "CF-IPCountry": "NL",
            },
        )
        if status != 202:
            raise RuntimeError(f"unexpected status {status}")
        return elapsed

    return worker


def query_worker(base_url: str, domain: str, cookie: str):
    local = threading.local()
    params = urlencode({"domain": domain, "period": "LAST_7_DAYS"})
    counter = {"index": 0}

    def worker() -> float:
        if getattr(local, "connection", None) is None:
            local.connection = Connection(base_url)
        connection = local.connection

        endpoint = STATS_ENDPOINTS[counter["index"] % len(STATS_ENDPOINTS)]
        counter["index"] += 1
        status, elapsed = connection.request(
            "GET",
            f"{endpoint}?{params}",
            headers={"Cookie": cookie},
        )
        if status != 200:
            raise RuntimeError(f"unexpected status {status}")
        return elapsed

    return worker


def login(base_url: str, username: str, password: str) -> str:
    parts = urlsplit(base_url)
    factory = http.client.HTTPSConnection if parts.scheme == "https" else http.client.HTTPConnection
    connection = factory(parts.hostname or "localhost", parts.port or 80, timeout=10)
    try:
        connection.request(
            "POST",
            "/auth/login",
            body=json.dumps({"username": username, "password": password}).encode(),
            headers={"Content-Type": "application/json"},
        )
        response = connection.getresponse()
        response.read()
        if response.status != 200:
            raise SystemExit(f"login failed with status {response.status}")
        return (response.getheader("Set-Cookie") or "").split(";", 1)[0]
    finally:
        connection.close()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=["ingest", "query"])
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--domain", default="localhost")
    parser.add_argument("--concurrency", type=int, default=16)
    parser.add_argument("--duration", type=float, default=10.0)
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password", default="password123")
    args = parser.parse_args()

    if args.mode == "ingest":
        worker = ingest_worker(args.base_url, args.domain, {"index": 0})
    else:
        cookie = login(args.base_url, args.username, args.password)
        worker = query_worker(args.base_url, args.domain, cookie)

    run_workers(worker, args.concurrency, args.duration)


if __name__ == "__main__":
    main()
