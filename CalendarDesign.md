# Calendar System Design

## Clarifying Questions to Ask

- Can a user have multiple calendars, or exactly one personal calendar?
- Can a calendar have multiple owners / be shared with other users?
- Do we need cross-organization (cross-tenant) invitations, or are invites within a tenant only?
- What is the recurrence complexity we must support (simple daily/weekly vs. full RRULE)?
- Is this read-heavy (viewing) or write-heavy (editing)? *(Assume read-heavy — most traffic is calendar views and free/busy lookups.)*

> **Conventions used below:** database fields are `snake_case`; JSON API payloads are `camelCase`. Timestamps are stored in **UTC** (`*_ts` / `*_utc`); the originating timezone is stored separately so recurring events survive DST changes.

---

## Key Design Decisions

These resolve ambiguities up front so the rest of the design is consistent.

1. **Multi-calendar, shareable model (chosen).** A user can own multiple calendars, and a calendar can be shared with other users via roles. This is why the APIs are calendar-scoped (`POST /v1/calendars/{id}/events`).
   - `Calendar.owner_user_id` is therefore **not unique** (a user owns many).
   - Sharing/membership lives in a separate `CalendarMember` table.
   - *MVP simplification:* if we constrain to one personal calendar per user (1:1), make `owner_user_id` unique and drop `CalendarMember`. The APIs degrade gracefully.

2. **Availability > Consistency (NFR1).** A user must always be able to see their calendar. We accept eventual consistency for cross-device sync (3–5s) and read-your-writes on the writing device.

3. **Series master + exceptions for recurrence.** Never materialize infinite future occurrences. Store one master row with an `RRULE`, store per-occurrence overrides as exceptions, and expand a rolling window on read.

4. **Optimistic concurrency via `version`.** Edits use `If-Match: <version>`; a stale version returns `409 Conflict`. No long-held locks.

---

## Functional Requirements

- **FR1** — Event Management (create / edit / delete)
- **FR2** — Invitations and RSVP
- **FR3** — Calendar viewing (Weekly by default)
- **FR4** — Free/Busy and Availability Checking

### FR1 — Event Management (Create / Modify / Delete)

| Action | Endpoint |
|---|---|
| Create event | `POST /v1/calendars/{calendar_id}/events` |
| Get event | `GET /v1/events/{event_id}` |
| Update event | `PATCH /v1/events/{event_id}?scope=single\|this_and_future\|series` |
| Delete event | `DELETE /v1/events/{event_id}?scope=single\|this_and_future\|series` |

### FR2 — Invitations and RSVP

| Action | Endpoint |
|---|---|
| Add or update invitees (organizer) | `POST /v1/events/{event_id}/attendees` |
| List invitees and RSVP status (organizer) | `GET /v1/events/{event_id}/attendees` |
| Update own RSVP (guest) | `PUT /v1/events/{event_id}/attendees/me/response` |

### FR3 — Calendar Viewing (Weekly by default)

| Action | Endpoint |
|---|---|
| List events in a time range | `GET /v1/users/me/events?start_ts={start}&end_ts={end}&view=day\|week\|month` |
| Get single event | `GET /v1/events/{event_id}` |

### FR4 — Free/Busy and Availability Checking

| Action | Endpoint |
|---|---|
| Free/busy for multiple users | `POST /v1/freebusy/query` |

---

## Non-Functional Requirements

| #        | Requirement                 | Target                                                                                                                                                                                                                                                                          |
|----------|-----------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **NFR1** | Availability >> Consistency | Calendar reads always served, even degraded                                                                                                                                                                                                                                     |
| **NFR2** |  Consistency | Use different consistency guarantee for different data - <br/>String consistency required for event creation and modification; calendar permissions; ownership changes<br/> Eventual consistency acceptable for User Timeline view, notifications, free/busy materialized views |
| **NFR3** | Durability | Confirmed events must not lost                                                                                                                                                                                                                                                  |
| **NFR4** | High scalability            | Hundreds  of millions of users , billions of events and occurrences                                                                                                                                                                                                             |
| **NFR5** | Low latency                 | Get event < 200ms p95; free/busy check < 500ms p95                                                                                                                                                                                                                              |

---

## Capacity Estimates

**Assumptions**

| Assumption | Value |
|---|---|
| Total users | 100M |
| Daily schedulers | 1/10th of users → 10M schedule meetings daily |
| Meetings per scheduler/day | 5 → **50M meetings/day** system-wide |
| Average event size | 1 KB |
| Average recipients per event | 10 |

**Events Table Storage**

- **Daily:** 50M meetings/day × 1 KB = **50 GB/day**
- **10 years:** 50 GB × 30 days × 12 months × 10 years = **~180 TB**

> We'll need additional storage for other tables (users, reminders, attendees, etc.), which can be estimated separately.

**Read/Write Ratio**

Reads vastly outnumber writes: writes happen only on event create/update, but *every* recipient reads the event. Assume a **90:10 read-write ratio**.

- Daily reads: **90M/day**
- Daily writes: **10M/day**

**Throughput**

- Reads/sec: 90,000,000 / (24 × 3600) ≈ 1,041 → **~1K reads/sec**
- Writes/sec: 10,000,000 / (24 × 3600) ≈ 115 → **~100 writes/sec**

These numbers give a solid foundation: ~180 TB over 10 years, read-heavy, but modest sub-1K QPS.

---

## Database Selection

Choosing the right database is crucial for our estimated **180 TB over 10 years**.

**Option 1 — Relational (e.g., PostgreSQL)**

180 TB demands **sharding**, since a single PostgreSQL node tops out around 32 TB. **Yearly sharding** (~18 TB per shard) is a good fit — it keeps efficient range queries and easily handles our sub-1K QPS without throughput issues.

**Option 2 — NoSQL (e.g., Cassandra / DynamoDB)**

NoSQL offers inherent horizontal scalability; the key is partition + sort key design:

- **Events table:** partition by `event_id`.
- **Recipients table:** partition by `user_id`, with `event_start_ts` as the sort key. This allows single-node queries for a user's events and efficient range queries (e.g., upcoming events).

**Decision:** Given the scale and the modest read/write throughput (~1K reads/sec, ~100 writes/sec), we go with **PostgreSQL** (with yearly sharding) to keep things simple.

---

## Entities

### User

Represents an account in the system. The acting user is inferred from auth on every API.

| Field | Description |
|---|---|
| `user_id` | Primary key |
| `tenant_id` | Organization / workspace the user belongs to |
| `email` | Login / contact email |
| `name` | Display name |
| `default_timezone` | Default timezone for events and views |
| `locale` | Display locale |

### Calendar

A calendar owned by a user. APIs are calendar-scoped.

| Field | Description |
|---|---|
| `calendar_id` | Primary key |
| `tenant_id` | Org/workspace this calendar belongs to |
| `owner_user_id` | FK → `User.user_id` (**not** unique — a user can own many) |
| `name` | |
| `default_timezone` | |
| `status` | `active` \| `archived` \| `deleted` \| `suspended` |
| `version` | Optimistic concurrency |
| `created_at`, `updated_at` | |

### CalendarMember

Membership / sharing of a calendar with users other than the owner.

| Field | Description |
|---|---|
| `calendar_id` | FK → `Calendar.calendar_id` (composite PK with `user_id`) |
| `user_id` | FK → `User.user_id` |
| `role` | `OWNER` \| `EDITOR` \| `VIEWER` \| `FREE_BUSY_ONLY` |
| `version` | |
| `created_at` | |

> Owner is stored on **Calendar**; everyone else lives in **CalendarMember**. `role = FREE_BUSY_ONLY` supports sharing availability without exposing event details.

### Event

Backs all event CRUD and calendar views. A row is either a single event **or** a recurring series master (when `recurrence_rule` is set).

| Field | Description |
|---|---|
| `event_id` | Primary key |
| `calendar_id` | FK → `Calendar.calendar_id` |
| `organizer_user_id` | FK → `User.user_id` |
| `title`, `description`, `location` | |
| `start_ts` | Start timestamp in **UTC** |
| `end_ts` | End timestamp in **UTC** |
| `is_all_day` | |
| `start_local_datetime` | Wall-clock start (for recurrence + DST correctness) |
| `end_local_datetime` | Wall-clock end |
| `timezone_id` | IANA tz (e.g. `Asia/Kolkata`) the event recurs in |
| `recurrence_rule` | Nullable `RRULE`; when set, this row is a series master |
| `recurrence_end_ts` | Nullable; when the series stops repeating |
| `status` | `confirmed` \| `tentative` \| `cancelled` |
| `version` | Integer for optimistic concurrency on PATCH/DELETE |
| `created_at`, `updated_at` | |

> **Why store both UTC and local time?** A recurring 3pm meeting in `Asia/Kolkata` must stay at 3pm *local* even across daylight-saving transitions. Storing only UTC would silently shift it by an hour. So we store the wall-clock time + `timezone_id` and compute UTC per occurrence at expansion time.

### EventException

Per-occurrence override or cancellation of a recurring series (the "exceptions" half of master+exceptions).

| Field               | Description                                                                                                                  |
|---------------------|------------------------------------------------------------------------------------------------------------------------------|
| `exception_id`      | Primary key                                                                                                                  |
| `event_id`          | the recurring series this exception belongs to                                                                               |
| `original_start_ts` | The expected start time of the occurrence according to series rule. This is how we identify which instance is being changed. |
| `override_start_ts` | New start time for this occurrence                                                                                           |
| `override_end_ts`   | New end time for this occurrence                                                                                             |
| `override_title`    | New start time for this occurrence                                                                                           |
| `override_location` | New start time for this occurrence                                                                                           |
| `status`            | active or cancelled. if cancelled this occurence is skipped entirely.                                                        |
| `created_at`        |                                                                                                                              |

### EventAttendee

Each invitee (including the organizer, optionally) and their RSVP state. Backs all FR2 APIs.

| Field | Description |
|---|---|
| `attendee_id` | Primary key |
| `event_id` | FK → `Event.event_id` |
| `user_id` | FK → `User.user_id` (nullable for external guests) |
| `guest_email` | Email for guests without an account |
| `rsvp_status` | `ACCEPTED` \| `DECLINED` \| `TENTATIVE` \| `PENDING` |
| `is_optional` | Optional attendee flag |
| `is_organizer` | True if this row is the organizer-as-attendee |
| `response_ts` | Timestamp of the last RSVP update |

> The organizer is also denormalized onto `Event.organizer_user_id` for fast lookup; all invitees (organizer included, if desired) live here so FR2 can evolve without bloating the Event row.

### Reminder

| Field | Description |
|---|---|
| `reminder_id` | Primary key |
| `event_id` | FK → `Event.event_id` |
| `user_id` | Whose reminder this is (per-attendee reminders) |
| `lead_time_seconds` | How far before start to fire |
| `channel` | `PUSH` \| `EMAIL` \| `SMS` |

### NotificationJob

Scheduled delivery of reminders / invites, decoupled from the request path.

| Field | Description |
|---|---|
| `job_id` | Primary key |
| `tenant_id` | |
| `type` | `REMINDER` \| `INVITE` \| `RSVP_UPDATE` |
| `execute_at` | When to fire |
| `payload` | Delivery details |
| `status` | `PENDING` \| `SENT` \| `FAILED` |

### FreeBusyBlock (Optional / derived)

In the simplest design, FR4 computes free/busy directly from **Event + EventAttendee** by expanding events in the requested window. **At scale**, we precompute busy intervals per user into this derived store so availability queries stay fast (NFR4: < 500ms).

| Field | Description |
|---|---|
| `user_id` | FK → `User.user_id` |
| `start_ts` | Start of busy interval (UTC) |
| `end_ts` | End of busy interval (UTC) |
| `status` | `BUSY` \| `TENTATIVE` \| `OUT_OF_OFFICE` |
| `source_event_id` | Optional reference for debugging |

### ChangeLog

| Field         | Description                                                         |
|---------------|---------------------------------------------------------------------|
| `change_id`   | Monotonically increasing sequence (bigint); acts as the global cursor |
| `user_id`     | User affected by this change. We write one row per affected user.   |
| `event_id`    | Event whose state changed                                           |
| `change_type` | CREATED, UPDATED, DELETED, RSVP_UPDATED                             |
| `changed_at`  | Timestamp of the change                                             |
| `source`      | WEB MOBILE                                                          |


> This is a **read model**: rebuilt asynchronously when events change. It trades freshness (NFR1 favors availability) for fast, denormalized availability reads.

---

## API Design

### Create Calendar

```http
POST /v1/calendars
```
```json
{
  "name": "Work",
  "timeZone": "Asia/Kolkata"
}
```

### Share Calendar

```http
PUT /v1/calendars/{calendarId}/members/{userId}
```
```json
{
  "role": "EDITOR"
}
```

### Create Event

```http
POST /v1/calendars/{calendarId}/events
Idempotency-Key: abc-123
```
```json
{
  "title": "System Design Interview",
  "start": { "localDateTime": "2026-06-20T15:00:00", "timeZone": "Asia/Kolkata" },
  "end":   { "localDateTime": "2026-06-20T16:00:00", "timeZone": "Asia/Kolkata" },
  "attendeeIds": ["U2", "U3"],
  "reminders": [
    { "channel": "PUSH", "leadTimeSeconds": 600 }
  ]
}
```

> **Idempotency-Key** makes retried POSTs safe: the server stores the key → response mapping, so a client retry after a network blip never creates a duplicate event.

### Update Event

```http
PATCH /v1/events/{eventId}?scope=single|this_and_future|series
If-Match: 12
```
> `If-Match` carries the event `version` for optimistic concurrency control. A version mismatch → `409 Conflict`.

### RSVP

```http
PUT /v1/events/{eventId}/attendees/me/response
```
```json
{
  "response": "ACCEPTED"
}
```

### Free/Busy

```http
POST /v1/freebusy/query
```
```json
{
  "userIds": ["U1", "U2", "U3"],
  "start": "2026-06-20T03:30:00Z",
  "end":   "2026-06-20T12:30:00Z"
}
```

---

## Core APIs (summary)

- `POST /v1/calendars`
- `PUT  /v1/calendars/{id}/members/{userId}` — share
- `GET  /v1/users/me/calendars`
- `POST /v1/calendars/{id}/events`
- `PATCH /v1/events/{eventId}` *(with `scope`)*
- `DELETE /v1/events/{eventId}` *(with `scope`)*
- `PUT  /v1/events/{eventId}/attendees/me/response` — RSVP
- `GET  /v1/users/me/events?start=&end=&view=day|week|month`
- `POST /v1/freebusy/query`
- `POST /v1/events/{eventId}/reminders`

**Recurring-edit scope** (on PATCH/DELETE):

| `scope` | Effect |
|---|---|
| `single` | Edit only the targeted occurrence → writes an `EventException` |
| `this_and_future` | Split the series: end the old master, create a new master from this point |
| `series` (all) | Edit the master row directly |

---

## Recurrence Handling (deep-dive)

The hardest part of a calendar. Strategy: **master + exceptions + rolling materialization**.

- **Store:** one `Event` master with an `RRULE` (e.g. `FREQ=WEEKLY;BYDAY=MO`), plus `EventException` rows for occurrences that were edited or cancelled.
- **Never** persist infinite future occurrences — it's unbounded storage and every series edit would rewrite millions of rows.
- **Read path:** to render a window (`GET ...?start&end`), expand the master's `RRULE` within `[start, end]`, then apply matching exceptions (override/cancel). Bound expansion by the window so it's cheap.
- **DST correctness:** expand using `start_local_datetime` + `timezone_id`, then convert each occurrence to UTC. This keeps "every Monday 9am local" truly 9am local across DST.
- **Optional optimization:** a background materializer can pre-expand a rolling window (e.g. next 90 days) into an instances table / cache for very hot calendars.

---

## Real-Time Multi-Device Sync (NFR2)

Target 3–5s p95 across a user's devices:

- On write, publish a change event (e.g. to a per-user channel) so other connected devices get a push and pull the delta.
- Devices keep a **sync token / cursor**; `GET changes?since=<token>` returns incremental changes (supports offline → reconnect).
- The writing device gets **read-your-writes**; other devices converge within the sync SLA, consistent with NFR1 (availability over strict consistency).

---

## Storage, Sharding & Indexing

- **Shard by `calendar_id`** (or `tenant_id` for org isolation) — most queries are calendar-scoped, keeping a calendar's data co-located.
- **Key index for views:** `(calendar_id, start_ts)` to serve `GET events?start&end` as a range scan.
- **Free/busy:** index `FreeBusyBlock(user_id, start_ts)`; or compute from `EventAttendee(user_id)` + `Event` join when not precomputed.
- **Read scaling:** read replicas + cache hot calendar windows (the current week is the common view).
- **Hot path:** event read < 20ms (NFR4) → served from cache / primary-key lookup, not expansion.

---

## Summary Data Model

| Entity | Key Fields |
|---|---|
| **User** | `user_id`, `tenant_id`, `email`, `name`, `default_timezone`, `locale` |
| **Calendar** | `calendar_id`, `tenant_id`, `owner_user_id`, `name`, `default_timezone`, `status`, `version` |
| **CalendarMember** | `calendar_id`, `user_id`, `role`, `version` |
| **Event** | `event_id`, `calendar_id`, `organizer_user_id`, `title`, `start_ts`, `end_ts`, `start_local_datetime`, `timezone_id`, `recurrence_rule`, `recurrence_end_ts`, `status`, `version` |
| **EventException** | `exception_id`, `parent_event_id`, `occurrence_local_start`, `overridden_fields`, `is_cancelled` |
| **EventAttendee** | `attendee_id`, `event_id`, `user_id`/`guest_email`, `rsvp_status`, `is_optional`, `is_organizer`, `response_ts` |
| **Reminder** | `reminder_id`, `event_id`, `user_id`, `lead_time_seconds`, `channel` |
| **NotificationJob** | `job_id`, `tenant_id`, `type`, `execute_at`, `payload`, `status` |
| **FreeBusyBlock** *(derived)* | `user_id`, `start_ts`, `end_ts`, `status`, `source_event_id` |

## Push invalidation + delta sync

- When the Calendar service commits a change that affects some users it appends rows to Changelog for 
  each affected user_id -> message bus
- Online devices maintain a push notification
- When that service receives the message it sends a tiny invalidation to all devices for that users
- Each device calls GET /v1/sync?cursor=last_seen_Cursor to pull the actual deltas.
---