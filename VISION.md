# Vision

Symbanta is a web analytics core for people who want to know what happened on their site
without building a profile of who was there. It ingests events from a small browser snippet,
writes them to PostgreSQL as an append-only log, derives session-keyed rollups from that log,
and answers a fixed set of statistical questions over the result. This document describes
where that core is heading: which properties of the current design are worth defending, which
gaps are already visible in the code, and what a few years of honest work would make true.

## Where symbanta is today

The application is Kotlin on Spring Boot, organised as a hexagonal (ports and adapters)
codebase: every feature owns its domain, inbound ports, outbound ports, service and adapters,
and `config/WiringConfig` is the only place where a port is bound to an implementation. The
domain and service layers import no framework. An OpenAPI 3.0 document drives the generated
DTOs and controller signatures, Flyway owns the schema, and Testcontainers backs the
persistence specs.

Six things exist and work:

- **Ingestion.** `POST /v1/events/register` accepts any event name, validates it cheaply
  (`EventNames.isValid`), enqueues it and answers `202 Accepted` immediately. A bounded
  coroutines queue with a fixed worker pool coalesces whatever is queued into one batched
  JDBC write per flush. When the burst exceeds the queue it drops, counts the drop, and warns
  behind a rate limit instead of exhausting the pool or the heap.
- **Storage.** One `events` table with a `jsonb` properties column, an allow-listed subset of
  request headers, the referrer, and a coarse country resolved from CDN headers. The raw IP is
  used for that lookup and never stored.
- **Reads.** Seven statistics views — summary, time series, routes, screen sizes, user agents,
  events, countries — behind one `AnalyticsQueryPort` answered by two adapters.
- **Rollups.** `stats_session_bucket` and `stats_dimension_bucket` hold the same facts one hour
  and one day at a time, keeping the session id rather than reducing it to a count, so visitors
  stay distinct and active time across buckets can be stitched back with `LAG`. A refresher
  rebuilds whole hours, rebuilds the hour in progress on every tick, and advances a coverage
  watermark in `stats_rollup_state`. `RollupWindow` slices a report window into whole days,
  whole hours, and the event-log minutes on either side.
- **Caching.** `CachingStatisticsQueryService` keys a computed answer by view, property and
  period, reuses it for at most `application.stats.cache-ttl`, collapses concurrent first
  reads, and publishes hits, misses and entries as meters.
- **Observability.** Backlog, enqueued, processed, failed, dropped, batch size and flush time
  are Micrometer meters exposed at `/actuator/prometheus`, so overload is visible from outside
  the JVM. The documented alerts are a drop counter above zero and a backlog that never drains.

The load-bearing decisions, in the order they constrain the future:

1. **The event log is the only source of truth.** Rollups are derived and rebuilt rather than
   patched, so an event that commits after its hour was rolled up is picked up by the next
   rebuild. Anything that makes the log lossy makes every downstream number a guess.
2. **The session id is the identity.** "Visitors" means distinct sessions, and the snippet keeps
   that id in `sessionStorage`, which is per-tab and dies with the tab. There is deliberately
   nothing durable to correlate across sites.
3. **Reporting semantics live in the domain, not in SQL.** `ReportingPeriodResolver` turns a
   period into a half-open `DateRange` against an injected clock, and `DateRange` is what every
   aggregate is asked to honour.
4. **The reporting time zone is one constant.** `ReportingPeriodResolver.TIMEZONE` is
   `Europe/Amsterdam`, and bucket boundaries are derived from it independently of the database
   session time zone.
5. **Dropping is allowed; blocking is not.** The write path may lose events at the edge and says
   so in a meter. It may not slow the request path down to avoid it.
6. **The API is the product boundary.** The dashboard consumes the OpenAPI document and nothing
   else, so the shape of the contract is the shape of the integration.

What is honestly absent: there is no property lifecycle API (properties are seeded from
configuration or raw SQL, and CORS is registered for a `POST` that no controller serves), no
retention or purge, no export, no delete, one reporting time zone for the whole deployment,
hour and day granularity only, no goals, funnels, segments or annotations, and `/admin/**` is
reserved for `ROLE_ADMIN` by `SecurityConfig` with nothing behind it. Bots are recognised by
`UserAgentParser` and reported as a label; they are not filtered, and no total excludes them.

## The vision

1. **A deployment can answer questions about its entire history, and the cost of the answer does
   not grow with the age of the deployment.** Today `MAX_TIME_SERIES_POINTS` caps a chart at 500
   buckets, which turns `ALL_TIME` into roughly the last sixteen months. A weekly and monthly
   rollup tier, with the exactness guarantees the hourly and daily tiers have now, makes "since
   launch" an ordinary question.
2. **Event data belongs to the operator in practice, not only in principle.** Retention windows,
   export and deletion are API operations with the same contract discipline as the reads, and a
   purge can never leave a rollup bucket claiming coverage it no longer has.
3. **Instrumentation is a schema, not a snippet.** The event-type registry grows from a list of
   names into a declaration of what a named event carries, so a deployment can validate its own
   `props` and a client can render them without guessing at the payload.
4. **The privacy claims are tested.** "No IP is stored" and "no identifying header is persisted"
   become assertions in the suite rather than paragraphs in a README, including a spec that
   feeds a request full of identifying headers and checks that none of them reached the table.
5. **Storage is replaceable by measurement.** The two outbound port contracts
   (`AnalyticsQueryPortContract`, `EventStorePortContract`) and the differential spec already pin
   two adapters to identical semantics, so a third store is a candidate that has to pass them and
   win on a benchmark, not an opinion about what would be faster.

## Features that are in scope and not built yet

- **Weekly and monthly rollups.** `TimeGranularity` knows `HOUR` and `DAY`, and the read path
  slices windows by those two. The hard part is a watermark that advances at three granularities
  without ever letting a day row disagree with the hour rows beneath it, plus a differential spec
  that stays honest across all three.
- **Per-property reporting time zones.** The constant lives in one place, which is why this is in
  scope at all. The hard part is the rollup tables: day rows are derived from hour rows in the
  reporting zone today, so a second zone per property changes what a bucket *is*.
- **Property lifecycle.** A `POST /v1/properties`, and something that retires one.
  `PropertyRepositoryPort` and the CORS entry are already there. The hard part is cascade
  semantics: whether deleting a property deletes its events and its rollups, and what the
  watermark means afterwards.
- **Retention and purge.** The hard part is doing it without breaking the coverage watermark, and
  defining what happens when a purged hour is rebuilt from a log that no longer holds it.
- **A non-CDN geo adapter.** `GeoLocationPort` has exactly one implementation, and it reads proxy
  headers. The hard part is a local MaxMind or DB-IP database that resolves identically in tests
  and production without putting a licence-bound file in the repository.
- **Ingestion attestation.** `POST /v1/events/register` is `permitAll` with
  `allowedOriginPatterns = ["*"]`, so any page can write to any registered property. The hard
  part is a per-property write key or origin allow-list that does not become a third-party cookie
  in disguise.
- **Event-type schemas.** Registration is idempotent and `props` is free-form `jsonb`. The hard
  part is validating a custom payload at the edge when the request path is budgeted at roughly
  750 ns per event.
- **The `/admin/**` surface.** The security configuration already demands `ROLE_ADMIN` for it.
  The hard part is designing operator operations — rebuild a range of rollups, inspect the queue,
  replay a rejected batch — so that every one of them is idempotent and safe to run twice.

## New features that would be worth having

- **Conversion funnels over the event registry.** A funnel is an ordered list of event names, which
  the registry already provides; session-keyed dimension buckets make a sequence a query.
- **Machine traffic as a first-class breakdown.** `UserAgentParser` already names ChatGPT, Claude,
  Common Crawl and assorted crawlers, so separating humans from machines is one query away.
- **Annotations.** A timestamped marker ("the release went out here", "the CDN was down") that the
  time series can draw. Small surface, large explanatory power.
- **Saved views.** The cache key is already `(view, property, period)` — the identity of a saved
  query — so the feature has a shape to grow from rather than a schema to invent.
- **A text report endpoint.** A small HTML or plain-text summary of the same numbers, for an email
  digest or a status page: the counterweight to a single-page dashboard, and one adapter.
- **A per-property kill switch and ingest limit**, with the queue meters showing what was refused.

## Contributions that would move the needle

1. **Retention and purge.** The smallest honest step towards owning the data: one migration, one
   scheduled job and the watermark, and every other privacy claim becomes concrete.
2. **Property lifecycle endpoints.** A small controller, a port method and two specs. It is the
   difference between a single-tenant deployment and one a co-op can run.
3. **The weekly and monthly rollup tier.** Higher effort, highest payoff: it removes the
   500-bucket ceiling and makes the rollup pipeline reviewable by anyone who reads `RollupWindow`.
4. **A geo adapter with a local database.** Contained by the port, testable with the existing
   fixture pattern, and it removes a dependency on sitting behind a particular CDN.
5. **Ingestion attestation.** Deliberate design work with an obvious security shape; the decision
   record is worth more than the code.
6. **Extending `bench/http_load.py` to sweep with rollups on and off**, which is the measurement
   that says whether a new rollup tier is affordable.
7. **A page explaining what each table and column means.** Onboarding cost for a self-hoster is
   mostly this, and it is the cheapest gift to the next maintainer.

## Compared to related projects

| Project | What it is | Where symbanta differs |
| --- | --- | --- |
| Plausible Analytics | Elixir/Phoenix, cookie-free web analytics, self-hostable, with goals, funnels and custom-event properties | Symbanta has no goals or funnels, and counts visitors as per-tab sessions rather than with a privacy-preserving daily hash. It is stronger on rollup exactness and on showing ingestion health as meters. |
| Umami | Node/Next.js analytics with a simple event model, MySQL or PostgreSQL, popular on shared hosting | Umami is lighter to deploy and has a broader widget vocabulary. Symbanta is heavier to run but keeps a strict domain boundary and contract-tested query adapters. |
| Matomo | PHP, full-featured analytics with a plugin marketplace, deep configuration and consent tooling | Matomo is a Swiss army knife and behaves like one. Symbanta offers one small vocabulary of event names and refuses to profile individuals at all. |
| GoatCounter | Go, single binary, SQLite, text-first reports, minimal instrumentation | GoatCounter is easier to host and more honest about being a counter. Symbanta takes custom events, console capture and high-volume ingestion seriously. |
| Fathom Analytics | Hosted, closed-source analytics service with a strong privacy story | Symbanta is entirely self-hostable and MIT licensed; nothing in the data path leaves the operator's machine. |
| PostHog | Product analytics: events, funnels, session replay, feature flags, experiments | PostHog measures users. Symbanta measures occurrences, and its schema cannot hold a durable identity to replay. |
| Counterscale | TypeScript analytics on Cloudflare Workers and Analytics Engine | Counterscale exploits an edge runtime's storage. Symbanta runs on an ordinary PostgreSQL box and treats the write path's failure modes explicitly. |

What the others do that symbanta could still add: campaign and referrer breakdowns with a settled
UTM story, goal and funnel definitions, public read-only share links, email digests, team accounts
with roles and invitations, data export as a documented format, retention settings, and a bot
filter before ingestion rather than a label after it. All of it is compatible with the privacy
story; none of it requires an identity.

Where symbanta is ahead and should stay ahead: the port/adapter discipline is real, not
aspirational — both analytics adapters are pinned by the same contract and compared by a
differential spec over randomised sessions, tabs spanning local midnight and windows that begin
mid-hour. Windows that do not start on the hour are exact, because `RollupWindow` reads four
contiguous slices instead of trusting one aggregate. The write path accounts for every event it
refuses. Console capture turns an instrumented application's own logging into a diagnostics
channel inside the same system that counts its page views. And replacing PostgreSQL or the
in-process queue is a change confined to `WiringConfig`.

## In the long run

The multi-year bets are about what the project refuses to become as much as what it adds. The
first is that **identity never enters the schema**: if a future feature needs to recognise the
same person across sessions, it does not ship, and the session-keyed rollups are the physical
guarantee of that. The second is that **the event log stays the source of truth** while the read
path gets progressively cheaper — more granularities, more cached slices, eventually a columnar
store — because a system that cannot recompute its own numbers is a system that cannot be
audited. The third is that **the registry becomes the language**: once events declare what they
carry, a deployment can ask domain questions ("how many checkouts started but did not finish?")
in the same system that counts page views. The fourth is **operator sovereignty as a default**:
retention, export and purge present and documented from the first release that stores an event.

## What this document is for

It exists so a contributor can tell which parts of the design are deliberate, which gaps are
known, and what kind of change the project would welcome. Proposals that make privacy a
configuration option, that add a durable visitor identity, or that bind the read path to a single
store are out of scope whatever their merit; proposals that make the rollups more exact, the
write path more observable, or the operator more in control are the ones this project wants.

*This document describes intent, not commitments; the code, the release notes and the issue
tracker say what is actually true today.*
