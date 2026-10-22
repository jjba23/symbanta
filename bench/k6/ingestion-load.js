// symbanta
//
// Copyright © Josep Bigorra <jjbigorra@gmail.com>
//
// symbanta is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// symbanta is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with symbanta.  If not, see <https://www.gnu.org/licenses/>.

// End-to-end ingestion load test (issue #3).
//
//   BASE_URL=http://localhost:8080 DOMAIN=localhost RATE=200 DURATION=30s \
//     k6 run bench/k6/ingestion-load.js
//
// Measures the fire-and-forget ingestion path: HTTP accept → background write. Because the API
// acknowledges immediately, `http_req_duration` describes the acceptance latency; the durable
// write throughput is visible as database growth (see the README section on benchmarks).

import http from "k6/http";
import {check} from "k6";
import {Counter} from "k6/metrics";

const accepted = new Counter("symbanta_events_accepted");

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const DOMAIN = __ENV.DOMAIN || "localhost";
const RATE = Number(__ENV.RATE || 200);
const DURATION = __ENV.DURATION || "30s";

export const options = {
    scenarios: {
        ingest: {
            executor: "constant-arrival-rate",
            rate: RATE,
            timeUnit: "1s",
            duration: DURATION,
            preAllocatedVUs: Math.max(10, Math.ceil(RATE / 20)),
            maxVUs: Math.max(50, RATE * 2),
        },
    },
    thresholds: {
        // The API answers 202 immediately; anything slower than 100 ms p95 is a regression.
        "http_req_failed": ["rate<0.01"],
        "http_req_duration": ["p(95)<100", "p(99)<250"],
    },
};

const USER_AGENTS = [
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1",
    "Mozilla/5.0 (X11; Linux x86_64; rv:121.0) Gecko/20100101 Firefox/121.0",
];

export default function () {
    const index = __ITER;
    const name = index % 20 === 0 ? "signup-click" : (index % 5 === 0 ? "heartbeat" : "page-view");

    const payload = {
        domain: DOMAIN,
        name: name,
        sessionId: `bench-session-${__VU}`,
        url: `https://${DOMAIN}/bench/article/${index % 500}`,
        referrer: index % 3 === 0 ? "https://search.example" : null,
        screenWidth: 1920,
        screenHeight: 1080,
        language: "en-US",
        userAgent: USER_AGENTS[index % USER_AGENTS.length],
        props: {plan: "pro", index: index},
    };

    const response = http.post(`${BASE_URL}/v1/events/register`, JSON.stringify(payload), {
        headers: {
            "Content-Type": "application/json",
            // Simulates a CDN in front of the API, which is how country data is resolved.
            "CF-IPCountry": "NL",
        },
        tags: {endpoint: "ingest"},
    });

    if (check(response, {"status is 202": (res) => res.status === 202})) {
        accepted.add(1);
    }
}
