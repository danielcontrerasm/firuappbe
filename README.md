# Pettracker

## Railway deploy

This service is ready to deploy to Railway with the existing `railway.json` and `Dockerfile`, with two deployment requirements:

1. Use a PostgreSQL service that includes `postgis`.
2. Set `JWT_SECRET` in Railway before the first deploy.

### Required Railway setup

1. Create the app service from this repository.
2. Attach a PostgreSQL service that supports PostGIS. The app runs `CREATE EXTENSION IF NOT EXISTS postgis;` at startup, so a plain PostgreSQL template is not enough for polygon geofences.
3. Set these variables on the app service:

```env
JWT_SECRET=generate-a-long-random-secret
```

Optional variables:

```env
JWT_EXPIRATION_MS=86400000
APP_CORS_ALLOWED_ORIGINS=http://localhost:3000,https://firuappfe-production.up.railway.app,https://api.firuapp.com.co
TWILIO_ACCOUNT_SID=
TWILIO_AUTH_TOKEN=
TWILIO_PHONE_NUMBER=
TWILIO_WHATSAPP_ENABLED=false
TWILIO_WHATSAPP_NUMBER=
SPRING_MAIL_HOST=
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=
SPRING_MAIL_PASSWORD=
SPRING_MAIL_SMTP_AUTH=true
SPRING_MAIL_SMTP_STARTTLS_ENABLE=true
NOTIFICATIONS_EMAIL_FROM=
GEOCODING_NOMINATIM_ENABLED=true
GEOCODING_NOMINATIM_USER_AGENT=pettracker/1.0
```

If `TWILIO_WHATSAPP_ENABLED=true` and `TWILIO_WHATSAPP_NUMBER` is set to a Twilio WhatsApp sender, phone alerts are sent to both SMS and WhatsApp.
Email notifications require valid `SPRING_MAIL_*` SMTP settings. SMS requires the Twilio variables above.

You do not need to set `SPRING_DATASOURCE_URL` manually on Railway. The app maps Railway's database URL variables into Spring datasource properties automatically.

### Railway behavior in this repo

- HTTP/Tomcat port is fixed at `8080`.
- GPS TCP listener uses port `5000` and detects V41/JT/T 808 (`7E`), bracket ASCII, and Topin (`7878`) per connection.
- JT/T 808 supports legacy (2011/2013) and 2019 headers, registration (`0100` → `8100`), and general replies (`8001`) for authentication (`0102`), heartbeats (`0002`), and single location reports (`0200`). Register the exact BCD terminal ID from the packet header as the pet terminal ID, including leading zeros (12 digits for legacy headers, 20 for 2019); it may differ from the printed IMEI. Unknown terminals receive a failure reply and their locations are not ingested. Admission uses the terminal ID; the registration token is not a secret credential.
- Device clock zones are configured separately under `gps.jt808.terminal-time-zones`, keyed by the exact terminal ID. The observed HW10 terminal `014166272782` reports UTC, so it defaults to `UTC`; set `GPS_HW10_TIME_ZONE` if its firmware is configured differently. Other V41/JT808 terminals retain the `APP_TIME_ZONE` interpretation by default. Incoming timestamps are converted to an instant, then saved in `APP_TIME_ZONE` (`America/Bogota` by default). This affects new reports after deployment; existing location timestamps are not rewritten.
- Search the logs for `JT808 time terminalId=014166272782` to compare `rawBcd`/`deviceTime` from the frame with the server's `configuredZone`, `appliedOffset`, `decodedUtc`, and `serverUtc` at decoding. The timestamp has no transmitted timezone; the configured zone and applied offset are server-side interpretations.
- HW10/BSJ location reports support nested records inside additional-info item `0xEB`. Each record starts with a big-endian WORD length covering its WORD ID and value, excluding the length itself. Following the [BSJ field mapping](https://flespi.com/protocols/bsj), `0x00A8` supplies one byte of battery percentage (0–100), and `0x002D` supplies a two-byte millivolt value (`0F17` → `3.863 V`). These populate the existing location database/API fields `batteryPercent` and `batteryVoltage`. Zero/all-ones voltage values and unsupported field widths are ignored. Unknown records remain available as diagnostic metadata; malformed nested lengths cannot read beyond their enclosing extension.
- `0x00D5` is decoded as the reported IMEI in position metadata (15 ASCII digits, optionally null-terminated, or padded eight-byte BCD). This diagnostic identity does not overwrite the pet's registered IMEI or terminal ID. Search `JT808 battery terminalId=014166272782` in the logs to see decoded battery values and the reported IMEI.
- The legacy V41 battery extension remains `E1 01 XX`: exactly one unsigned byte representing 0–100 percent, including 0. Invalid readings are ignored and reports without a valid battery reading keep `null`; percentages are not clamped or estimated from voltage. Other vendor Wi-Fi/LBS layouts, heartbeat battery payloads, batch uploads, encrypted messages, and subpackage reassembly remain unsupported.
- For Topin devices, register the 15-digit IMEI as the pet terminal ID. Unknown devices are disconnected at login. Valid, recent, non-historical fixes use the shared persistence and geofence pipeline; status packets supply battery percentage for subsequent fixes on that connection. Topin replies use the `TRACCAR_COMPATIBLE` profile.
- If Railway injects `DATABASE_URL`, `DATABASE_PRIVATE_URL`, or `DATABASE_PUBLIC_URL`, the app converts it to a JDBC URL automatically.
- Railway deployments now fail fast if `JWT_SECRET` is missing or still using the development fallback.

### Authentication and role access

- Public routes are limited to the static dashboard assets, `/api/auth/**`, `/api/public/**`, `/api/walker-applications`, `/ws/**`, and `/error`.
- `GET /api/me` is an authenticated endpoint that returns the current user's `id`, `name`, `email`, `phone`, `role`, and `walkerProfileId` (when the account has a walker profile).
- `/api/admin/**` and `/api/transit/**` require `ROLE_ADMIN`.
- Every other API route requires a valid JWT bearer token. Owner-scoped services restrict normal users to their own pets, locations, geofences, routes, walk requests, dog walks, and private search groups; lost-pet-wall groups are intentionally visible with redacted creator/member details. Admins can access the full owner/admin scope.
- The companion React frontend exposes the dog-walk marketplace at `/walks` and sends the stored bearer token to these APIs.

### Local development

Use `.env.example` as the variable template for local runs.

Demo data is disabled by default. Set `DEMO_GPS_ENABLED=true` for the existing `danico411@gmail.com` account (`DEMO_ACCOUNT_EMAIL` overrides the account). After a successful login, it seeds Bella, Peluche, and Rocky with recent routes; repeated logins reuse those pets and skip routes that are still recent. The simulator adds locations every five minutes and demo geofence alerts on its configured interval. Only generated `DEMO-*` pets owned by the configured account and without a tracker terminal ID are eligible. Demo alerts are stored for that account without sending owner or volunteer notifications. Demo timestamps use `APP_TIME_ZONE`.

Pet image uploads use `POST /api/pets` or `PUT /api/pets/{id}` with multipart parts named `pet` (`application/json`) and `image` (the file). For browser `FormData`, let the browser set the request's `Content-Type` and boundary. Images must be JPEG, PNG, WebP, or GIF. The default limit is 20MB per file and 21MB for the entire request, including the pet JSON and multipart headers. Configure these using `SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE` and `SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE`; image validation uses the same configured file limit. Oversized uploads return HTTP 413 with an error describing the limits. Restart/redeploy after changing the settings.

### Dog-walk API

The authenticated marketplace workflow is exposed through these endpoints:

- `GET /api/public/walkers` and `POST /api/walker-applications` for the public walker wall and applications.
- `GET /api/me` and `GET /api/walkers/me` for the signed-in account and its walker profile.
- `POST /api/walk-requests`, `GET /api/walk-requests`, and `GET /api/walk-requests/{id}` for owner/walker requests.
- `PUT /api/walk-requests/{id}/quote` for walker pricing/status proposals.
- `PUT /api/walk-requests/{id}/decision` for owner accept/reject/cancel decisions.
- `GET`/`POST /api/walk-requests/{id}/messages` for request chat.
- `POST /api/walk-requests/{id}/start` to create or resume an active dog walk.
- `GET /api/dog-walks`, `GET /api/dog-walks/{id}`, and `GET /api/dog-walks/{id}/positions` for active/history and route display.
- `POST /api/dog-walks/{id}/positions` for walker GPS updates and `POST /api/dog-walks/{id}/complete` to finish a walk.

The React `/walks` page consumes this API and includes owner requests, walker quoting/chat, live position submission, Leaflet route display, and admin walker management. Payments, ratings, availability time slots, and problem reports are not implemented yet.
