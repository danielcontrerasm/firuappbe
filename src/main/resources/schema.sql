CREATE EXTENSION IF NOT EXISTS postgis;

ALTER TABLE IF EXISTS location
    ADD COLUMN IF NOT EXISTS battery_percent integer,
    ADD COLUMN IF NOT EXISTS battery_voltage double precision;

ALTER TABLE IF EXISTS pet
    ADD COLUMN IF NOT EXISTS terminal_id varchar(255);

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_email_lower ON users (LOWER(email));
CREATE INDEX IF NOT EXISTS idx_pet_owner_id ON pet(owner_id);
CREATE INDEX IF NOT EXISTS idx_pet_imei ON pet(imei);
CREATE UNIQUE INDEX IF NOT EXISTS ux_pet_terminal_id ON pet(terminal_id) WHERE terminal_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_location_pet_timestamp ON location(pet_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_location_timestamp ON location(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_geofence_pet_id ON geofence(pet_id);
CREATE INDEX IF NOT EXISTS idx_alert_pet_created ON alert(pet_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_volunteer_user_active ON volunteer(user_id, active);
CREATE INDEX IF NOT EXISTS idx_volunteer_group_active ON volunteer(search_group_id, active);
CREATE INDEX IF NOT EXISTS idx_search_group_pet_id ON search_group(pet_id);
CREATE INDEX IF NOT EXISTS idx_dog_walker_profiles_status_active_created
    ON dog_walker_profiles(approval_status, active, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_dog_walker_profiles_user_id ON dog_walker_profiles(user_id);
CREATE INDEX IF NOT EXISTS idx_walk_requests_owner_created ON walk_requests(owner_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_walk_requests_walker_created ON walk_requests(walker_profile_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_walk_messages_request_created ON walk_messages(walk_request_id, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_dog_walks_owner_started ON dog_walks(owner_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_dog_walks_walker_started ON dog_walks(walker_profile_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_dog_walk_positions_walk_recorded ON dog_walk_positions(dog_walk_id, recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_transit_vehicle_owner_id ON transit_vehicle(owner_id);
CREATE INDEX IF NOT EXISTS idx_transit_location_vehicle_timestamp ON transit_location(vehicle_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_transit_scheduled_geofence_vehicle_enabled
    ON transit_scheduled_geofence(vehicle_id, enabled);
CREATE INDEX IF NOT EXISTS idx_transit_search_group_vehicle_id ON transit_search_group(vehicle_id);
CREATE INDEX IF NOT EXISTS idx_transit_volunteer_user_active ON transit_volunteer(user_id, active);
