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
- JT/T 808 supports legacy (2011/2013) and 2019 headers, registration (`0100` → `8100`), and general replies (`8001`) for authentication (`0102`), heartbeats (`0002`), and single location reports (`0200`). Register the exact BCD terminal ID from the packet header as the pet terminal ID, including leading zeros (12 digits for legacy headers, 20 for 2019); it may differ from the printed IMEI. Unknown terminals receive a failure reply and their locations are not ingested. Admission uses the terminal ID; the registration token is not a secret credential. Set the device clock to the configured app time zone (`APP_TIME_ZONE`, default `America/Bogota`).
- HW10 firmware compatibility still requires a manufacturer protocol document or captured packets. Vendor battery/Wi-Fi/LBS extensions, batch location uploads, encrypted messages, and subpackage reassembly are not covered by the standard single-location flow. Existing V41 battery fields remain supported.
- The supported V41 battery extension is `E1 01 XX`: exactly one unsigned byte representing 0–100 percent, including 0. Missing, truncated, multi-byte, or out-of-range values leave battery percentage unknown (`null`); values are not clamped or converted from voltage. This format is not yet verified for HW10 firmware. JT808 battery voltage and heartbeat battery payloads are not decoded.
- For Topin devices, register the 15-digit IMEI as the pet terminal ID. Unknown devices are disconnected at login. Valid, recent, non-historical fixes use the shared persistence and geofence pipeline; status packets supply battery percentage for subsequent fixes on that connection. Topin replies use the `TRACCAR_COMPATIBLE` profile.
- If Railway injects `DATABASE_URL`, `DATABASE_PRIVATE_URL`, or `DATABASE_PUBLIC_URL`, the app converts it to a JDBC URL automatically.
- Railway deployments now fail fast if `JWT_SECRET` is missing or still using the development fallback.

### Local development

Use `.env.example` as the variable template for local runs.

Pet image uploads use `POST /api/pets` or `PUT /api/pets/{id}` with multipart parts named `pet` (`application/json`) and `image` (the file). For browser `FormData`, let the browser set the request's `Content-Type` and boundary. Images must be JPEG, PNG, WebP, or GIF. The default limit is 20MB per file and 21MB for the entire request, including the pet JSON and multipart headers. Configure these using `SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE` and `SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE`; image validation uses the same configured file limit. Oversized uploads return HTTP 413 with an error describing the limits. Restart/redeploy after changing the settings.
