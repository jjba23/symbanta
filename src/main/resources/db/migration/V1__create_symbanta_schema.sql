CREATE TABLE properties (
    id UUID NOT NULL PRIMARY KEY,
    domain TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

COMMENT ON TABLE properties IS 'Stores registered domains and properties managed by the Symbanta platform.';
COMMENT ON COLUMN properties.id IS 'Primary key: unique identity generator for the registered domain.';
COMMENT ON COLUMN properties.domain IS 'Unique registered domain name (e.g., example.com).';
COMMENT ON COLUMN properties.created_at IS 'Timestamp (with time zone) when the domain record was created.';
COMMENT ON COLUMN properties.updated_at IS 'Timestamp (with time zone) when the domain record was last updated.';

CREATE TABLE events (
    id UUID NOT NULL PRIMARY KEY,
    property_id UUID NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    session_id TEXT,
    event_name TEXT NOT NULL,
    url TEXT,
    referrer TEXT,
    screen_width INTEGER,
    screen_height INTEGER,
    language VARCHAR(32),
    user_agent TEXT,
    props JSONB,
    created_at TIMESTAMPTZ NOT NULL
);

COMMENT ON TABLE events IS 'Ingested privacy-friendly analytics events and pageviews without persistent cookies or PII.';
COMMENT ON COLUMN events.id IS 'Primary key: unique identity for the logged event.';
COMMENT ON COLUMN events.property_id IS 'Foreign key referencing the associated property domain in the properties table.';
COMMENT ON COLUMN events.session_id IS 'Identifier to help distinguish between different sessions and tabs and instances.';
COMMENT ON COLUMN events.event_name IS 'Name of the event (e.g., page-view, click, signup).';
COMMENT ON COLUMN events.url IS 'Full URI where the event occurred.';
COMMENT ON COLUMN events.referrer IS 'HTTP referrer URI originating the navigation/event.';
COMMENT ON COLUMN events.screen_width IS 'Viewport or screen width in pixels.';
COMMENT ON COLUMN events.screen_height IS 'Viewport or screen height in pixels.';
COMMENT ON COLUMN events.language IS 'Preferred browser language header (e.g., en-US).';
COMMENT ON COLUMN events.user_agent IS 'User agent string identifying the browser';
COMMENT ON COLUMN events.props IS 'JSON map storing custom non-PII metadata properties passed with the payload.';
COMMENT ON COLUMN events.created_at IS 'Timestamp (with time zone) when the event was recorded by the server.';


CREATE INDEX idx_events_property_created ON events (property_id, created_at DESC);
CREATE INDEX idx_events_session_created ON events (session_id, created_at DESC);
CREATE INDEX idx_events_event_name ON events (event_name);
CREATE INDEX idx_events_props ON events USING gin (props);
