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
GEOCODING_NOMINATIM_ENABLED=true
GEOCODING_NOMINATIM_USER_AGENT=pettracker/1.0
```

If `TWILIO_WHATSAPP_ENABLED=true` and `TWILIO_WHATSAPP_NUMBER` is set to a Twilio WhatsApp sender, phone alerts are sent to both SMS and WhatsApp.

You do not need to set `SPRING_DATASOURCE_URL` manually on Railway. The app maps Railway's database URL variables into Spring datasource properties automatically.

### Railway behavior in this repo

- HTTP port uses Railway's injected `PORT`.
- If Railway injects `DATABASE_URL`, `DATABASE_PRIVATE_URL`, or `DATABASE_PUBLIC_URL`, the app converts it to a JDBC URL automatically.
- Railway deployments now fail fast if `JWT_SECRET` is missing or still using the development fallback.

### Local development

Use `.env.example` as the variable template for local runs.
