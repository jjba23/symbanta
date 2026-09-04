(function () {
    // Read config options from the <script> tag attributes, falling back to sensible defaults
    const scriptTag = document.currentScript || document.querySelector('script[src*="symbanta"]');
    const SYMBANTA_ENDPOINT = scriptTag?.getAttribute("data-endpoint") || "http://localhost:8080/v1/events/register";
    const DOMAIN_NAME = scriptTag?.getAttribute("data-domain") || window.location.hostname;
    const HEARTBEAT_INTERVAL_MS = parseInt(scriptTag?.getAttribute("data-interval"), 10) || 6000;

    // Get or create a session ID limited to current tab lifecycle
    let sessionId = sessionStorage.getItem("symbanta_session_id");
    if (!sessionId) {
        sessionId = crypto.randomUUID();
        sessionStorage.setItem("symbanta_session_id", sessionId);
    }

    // Construct EventPayload
    const createPayload = (eventName) => ({
        domain: DOMAIN_NAME,
        name: eventName,
        sessionId: sessionId,
        userAgent: navigator.userAgent,
        url: window.location.href,
        referrer: document.referrer || null,
        screenWidth: window.screen.width,
        screenHeight: window.screen.height,
        language: navigator.language
    });

    const sendEvent = (eventName) => {
        const payload = createPayload(eventName);

        // Transmit payload using Blob to preserve application/json header with sendBeacon
        const blob = new Blob([JSON.stringify(payload)], { type: "application/json" });
        const queued = navigator.sendBeacon(SYMBANTA_ENDPOINT, blob);

        // Fallback to fetch if sendBeacon is unavailable or fails
        if (!queued) {
            fetch(SYMBANTA_ENDPOINT, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload),
                keepalive: true
            }).catch(() => {});
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
})();
