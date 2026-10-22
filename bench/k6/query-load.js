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

// End-to-end dashboard query load test (issue #3).
//
// The statistics endpoints are guarded by the session cookie, so log in first:
//
//   BASE_URL=http://localhost:8080 DOMAIN=localhost USERNAME=admin PASSWORD=... \
//     VUS=10 DURATION=30s k6 run bench/k6/query-load.js

import http from "k6/http";
import {check} from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const DOMAIN = __ENV.DOMAIN || "localhost";
const USERNAME = __ENV.USERNAME || "admin";
const PASSWORD = __ENV.PASSWORD || "password123";

export const options = {
    vus: Number(__ENV.VUS || 10),
    duration: __ENV.DURATION || "30s",
    thresholds: {
        "http_req_failed": ["rate<0.01"],
        // Aggregates over a week of events should stay well below this on modest hardware.
        "http_req_duration": ["p(95)<500", "p(99)<1000"],
    },
};

const PARAMS = new URLSearchParams({domain: DOMAIN, period: "LAST_7_DAYS"}).toString();

export function setup() {
    const response = http.post(
        `${BASE_URL}/auth/login`,
        JSON.stringify({username: USERNAME, password: PASSWORD}),
        {headers: {"Content-Type": "application/json"}},
    );
    check(response, {"logged in": (res) => res.status === 200});

    // k6 keeps one cookie jar per VU, so the session cookie is handed to the test VUs explicitly.
    const sessions = response.cookies.JSESSIONID;
    const sessionId = sessions && sessions.length > 0 ? sessions[0].value : "";
    return {cookie: sessionId ? `JSESSIONID=${sessionId}` : ""};
}

const ENDPOINTS = [
    "/v1/stats/summary",
    "/v1/stats/timeseries",
    "/v1/stats/routes",
    "/v1/stats/screen-sizes",
    "/v1/stats/user-agents",
    "/v1/stats/events",
    "/v1/stats/countries",
];

export default function (data) {
    for (const endpoint of ENDPOINTS) {
        const response = http.get(`${BASE_URL}${endpoint}?${PARAMS}`, {
            headers: {Cookie: data.cookie},
            tags: {endpoint: endpoint},
        });
        check(response, {"status is 200": (res) => res.status === 200});
    }
}
