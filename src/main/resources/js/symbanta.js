/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

// @ts-check
"use strict";

(function () {
    /**
     * @typedef {Object} EventPayload
     * @property {string} domain
     * @property {string} name
     * @property {string} sessionId
     * @property {string} userAgent
     * @property {string} url
     * @property {string | null} referrer
     * @property {number} screenWidth
     * @property {number} screenHeight
     * @property {string} language
     * @property {Record<string, unknown>} [props]
     */

    // Ensure strict casting of the script element
    /** @type {HTMLScriptElement | null} */
    const scriptTag = (document.currentScript instanceof HTMLScriptElement ? document.currentScript : null)
        || document.querySelector('script[src*="symbanta"]');

    /** Reads a boolean data attribute, treating presence without value as true. */
    const flag = (name) => {
        const raw = scriptTag?.getAttribute(name);
        return raw !== null && raw !== undefined && raw !== "false";
    };

    // Read config options from the <script> tag attributes
    const SYMBANTA_ENDPOINT = scriptTag?.getAttribute("data-endpoint") || "http://localhost:8080/v1/events/register";
    const DOMAIN_NAME = scriptTag?.getAttribute("data-domain") || window.location.hostname;

    // Safer integer parsing to avoid NaN bypassing the default fallback
    const rawInterval = scriptTag?.getAttribute("data-interval");
    const parsedInterval = rawInterval ? parseInt(rawInterval, 10) : NaN;
    const HEARTBEAT_INTERVAL_MS = Number.isNaN(parsedInterval) || parsedInterval <= 0 ? 4000 : parsedInterval;

    const TRACK_CLICKS = flag("data-track-clicks");
    const TRACK_HOVERS = flag("data-track-hovers");
    const CAPTURE_CONSOLE = flag("data-capture-console");

    /**
     * crypto.randomUUID() is only available in secure contexts (HTTPS).
     * This provides a safe fallback if it is missing.
     * @returns {string}
     */
    const generateUUID = () => {
        if (typeof crypto !== "undefined" && crypto.randomUUID) {
            return crypto.randomUUID();
        }
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
            const r = Math.random() * 16 | 0;
            const v = c === 'x' ? r : (r & 0x3 | 0x8);
            return v.toString(16);
        });
    };

    // Get or create a session ID limited to current tab lifecycle
    let sessionId = sessionStorage.getItem("symbanta_session_id");
    if (!sessionId) {
        sessionId = generateUUID();
        sessionStorage.setItem("symbanta_session_id", sessionId);
    }

    /**
     * Constructs the payload object
     * @param {string} eventName
     * @param {Record<string, unknown>} [props]
     * @returns {EventPayload}
     */
    const createPayload = (eventName, props) => ({
        domain: DOMAIN_NAME,
        name: eventName,
        sessionId: /** @type {string} */ (sessionId),
        userAgent: navigator.userAgent,
        url: window.location.href,
        referrer: document.referrer || null,
        screenWidth: window.screen.width,
        screenHeight: window.screen.height,
        language: navigator.language,
        ...(props ? {props} : {})
    });

    // Guards the console wrapper against re-entering itself while reporting
    let reportingConsoleError = false;

    /**
     * Transmits the event to the endpoint. Never throws into the host application.
     * @param {string} eventName
     * @param {Record<string, unknown>} [props]
     */
    const sendEvent = (eventName, props) => {
        try {
            const payload = createPayload(eventName, props);
            const body = JSON.stringify(payload);
            const blob = new Blob([body], {type: "application/json"});

            let queued = false;

            // Feature detect sendBeacon (not available in some older browsers or strict webviews)
            if (typeof navigator.sendBeacon === "function") {
                queued = navigator.sendBeacon(SYMBANTA_ENDPOINT, blob);
            }

            // Fallback to fetch if sendBeacon is unavailable or fails
            if (!queued && typeof fetch === "function") {
                fetch(SYMBANTA_ENDPOINT, {
                    method: "POST",
                    headers: {"Content-Type": "application/json"},
                    body: body,
                    keepalive: true
                }).catch(() => { /* Fail silently */
                });
            }
        } catch (error) {
            // Prevent analytics errors from breaking the host page
            if (!reportingConsoleError) {
                reportingConsoleError = true;
                console.warn("Symbanta analytics error:", error);
                reportingConsoleError = false;
            }
        }
    };

    /**
     * Public API: record a custom event for this application.
     * @param {string} eventName
     * @param {Record<string, unknown>} [props]
     */
    const track = (eventName, props) => {
        if (typeof eventName !== "string" || eventName.trim() === "") {
            return;
        }
        sendEvent(eventName.trim(), props);
    };

    /** @type {any} */ (window).symbanta = {track: track};

    /** Reads `data-symbanta-props` JSON from an element, ignoring invalid JSON. */
    const elementProps = (element) => {
        const raw = element.getAttribute("data-symbanta-props");
        if (!raw) {
            return {};
        }
        try {
            const parsed = JSON.parse(raw);
            return parsed && typeof parsed === "object" ? parsed : {};
        } catch (error) {
            return {};
        }
    };

    /** Truncates free-form text so a runaway log cannot blow up the payload. */
    const truncate = (value) => (value.length > 2000 ? value.slice(0, 2000) + "…" : value);

    /** Formats a console argument into something serialisable and small. */
    const formatArgument = (argument) => {
        if (typeof argument === "string") {
            return argument;
        }
        if (argument instanceof Error) {
            return argument.message;
        }
        try {
            const serialised = JSON.stringify(argument);
            return serialised === undefined ? String(argument) : serialised;
        } catch (error) {
            return String(argument);
        }
    };

    /** Reports captured console output as a `console` event with level/message/stack. */
    const reportConsole = (level, args) => {
        // Never report the analytics error itself, or a failing beacon would recurse.
        if (reportingConsoleError) {
            return;
        }
        try {
            const message = truncate(args.map(formatArgument).join(" "));
            const error = args.find((argument) => argument instanceof Error);
            const props = {level: level, message: message};
            if (error && error.stack) {
                props.stack = truncate(String(error.stack));
            }
            sendEvent("console", props);
        } catch (ignored) {
            // Console capture must never break the host page
        }
    };

    // Initial page view
    sendEvent("page-view");

    // Periodic heartbeat (only when tab is visible)
    setInterval(() => {
        if (document.visibilityState === "visible") {
            sendEvent("heartbeat");
        }
    }, HEARTBEAT_INTERVAL_MS);

    // Final heartbeat when tab/page is hidden or closed
    document.addEventListener("visibilitychange", () => {
        if (document.visibilityState === "hidden") {
            sendEvent("visibility-change");
        }
    });

    // #4: automatic click tracking, including declarative custom events.
    if (TRACK_CLICKS) {
        document.addEventListener("click", (event) => {
            const target = /** @type {Element | null} */ (event.target);
            if (!target || typeof target.closest !== "function") {
                return;
            }

            const custom = target.closest("[data-symbanta-event]");
            if (custom) {
                const name = custom.getAttribute("data-symbanta-event");
                if (name) {
                    track(name, elementProps(custom));
                }
                return;
            }

            const clickable = target.closest("a, button");
            if (clickable) {
                track("click", {
                    tag: clickable.tagName.toLowerCase(),
                    href: clickable.getAttribute("href") || null
                });
            }
        }, {capture: true});
    }

    // #4: hover tracking, throttled to one event per element per page view.
    if (TRACK_HOVERS) {
        const hovered = new Set();
        document.addEventListener("mouseover", (event) => {
            const target = /** @type {Element | null} */ (event.target);
            if (!target || typeof target.closest !== "function") {
                return;
            }
            const hoverable = target.closest("[data-symbanta-event], a, button");
            if (!hoverable || hovered.has(hoverable)) {
                return;
            }
            hovered.add(hoverable);
            track("hover", {
                tag: hoverable.tagName.toLowerCase(),
                name: hoverable.getAttribute("data-symbanta-event") || null
            });
        }, {capture: true});
    }

    // #6: forward console.log/console.error (and friends) to symbanta, opt-in.
    if (CAPTURE_CONSOLE) {
        ["debug", "info", "log", "warn", "error"].forEach((level) => {
            const original = console[level];
            if (typeof original !== "function") {
                return;
            }
            console[level] = function (...args) {
                try {
                    reportConsole(level, args);
                } finally {
                    original.apply(console, args);
                }
            };
        });
    }
})();
