import { useEffect, useState } from "react";
import { api } from "./api";

const demoAccounts = [
  { label: "Admin", username: "admin", password: "admin123" },
  { label: "Owner", username: "owner", password: "owner123" },
  { label: "Volunteer", username: "volunteer", password: "volunteer123" }
];

const emptyLocation = { latitude: "", longitude: "", speed: "", accuracyMeters: "" };
const emptyGeofence = { centerLatitude: "", centerLongitude: "", radiusMeters: "" };
const emptySearchGroup = { title: "", notes: "" };

export default function App() {
  const [credentials, setCredentials] = useState(() => {
    const saved = localStorage.getItem("pettracker-auth");
    return saved ? JSON.parse(saved) : { username: "owner", password: "owner123" };
  });
  const [me, setMe] = useState(null);
  const [pets, setPets] = useState([]);
  const [selectedPetId, setSelectedPetId] = useState(null);
  const [petDetail, setPetDetail] = useState(null);
  const [locations, setLocations] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [searchGroups, setSearchGroups] = useState([]);
  const [locationForm, setLocationForm] = useState(emptyLocation);
  const [geofenceForm, setGeofenceForm] = useState(emptyGeofence);
  const [searchGroupForm, setSearchGroupForm] = useState(emptySearchGroup);
  const [status, setStatus] = useState("Connect to the backend with one of the seeded accounts.");
  const [loading, setLoading] = useState(false);

  async function loadSession(activeCredentials = credentials, preferredPetId = selectedPetId) {
    setLoading(true);
    try {
      const [meResponse, petsResponse] = await Promise.all([
        api.me(activeCredentials),
        api.listPets(activeCredentials)
      ]);
      setMe(meResponse);
      setPets(petsResponse);

      const petId = preferredPetId ?? petsResponse[0]?.id ?? null;
      setSelectedPetId(petId);

      if (petId) {
        await loadPet(activeCredentials, petId);
      } else {
        setPetDetail(null);
        setLocations([]);
        setAlerts([]);
        setSearchGroups([]);
      }

      localStorage.setItem("pettracker-auth", JSON.stringify(activeCredentials));
      setStatus(`Signed in as ${meResponse.fullName}.`);
    } catch (error) {
      setStatus(String(error.message));
    } finally {
      setLoading(false);
    }
  }

  async function loadPet(activeCredentials, petId) {
    const [pet, petLocations, petAlerts, petSearchGroups] = await Promise.all([
      api.getPet(activeCredentials, petId),
      api.getLocations(activeCredentials, petId),
      api.getAlerts(activeCredentials, petId),
      api.getSearchGroups(activeCredentials, petId)
    ]);
    setPetDetail(pet);
    setLocations(petLocations);
    setAlerts(petAlerts);
    setSearchGroups(petSearchGroups);
    setGeofenceForm(
      pet.geofence ?? { centerLatitude: "", centerLongitude: "", radiusMeters: "" }
    );
  }

  useEffect(() => {
    loadSession();
  }, []);

  async function handleLogin(event) {
    event.preventDefault();
    await loadSession(credentials, null);
  }

  async function handleSelectPet(petId) {
    setSelectedPetId(petId);
    await loadPet(credentials, petId);
  }

  async function submitLocation(event) {
    event.preventDefault();
    await api.addLocation(credentials, selectedPetId, {
      latitude: Number(locationForm.latitude),
      longitude: Number(locationForm.longitude),
      speed: locationForm.speed ? Number(locationForm.speed) : null,
      accuracyMeters: locationForm.accuracyMeters ? Number(locationForm.accuracyMeters) : null
    });
    setLocationForm(emptyLocation);
    await loadSession(credentials, selectedPetId);
  }

  async function submitGeofence(event) {
    event.preventDefault();
    await api.updateGeofence(credentials, selectedPetId, {
      centerLatitude: Number(geofenceForm.centerLatitude),
      centerLongitude: Number(geofenceForm.centerLongitude),
      radiusMeters: Number(geofenceForm.radiusMeters)
    });
    await loadSession(credentials, selectedPetId);
  }

  async function toggleLostStatus(lost) {
    await api.updateLostStatus(credentials, selectedPetId, lost);
    await loadSession(credentials, selectedPetId);
  }

  async function createSearchGroup(event) {
    event.preventDefault();
    await api.createSearchGroup(credentials, selectedPetId, searchGroupForm);
    setSearchGroupForm(emptySearchGroup);
    await loadSession(credentials, selectedPetId);
  }

  async function joinGroup(searchGroupId) {
    await api.joinSearchGroup(credentials, searchGroupId);
    await loadSession(credentials, selectedPetId);
  }

  const isOwnerOrAdmin = me?.roles.includes("OWNER") || me?.roles.includes("ADMIN");
  const isAdmin = me?.roles.includes("ADMIN");

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div>
          <p className="eyebrow">PetTracker</p>
          <h1>Pet location monitoring for owners, admins, and volunteers.</h1>
          <p className="muted">
            Owners only see their own pets. Admins can see all registered pets. Volunteers can join search groups for lost pets.
          </p>
        </div>

        <form className="panel form-grid" onSubmit={handleLogin}>
          <h2>Sign in</h2>
          <input
            value={credentials.username}
            onChange={(event) => setCredentials({ ...credentials, username: event.target.value })}
            placeholder="Username"
          />
          <input
            type="password"
            value={credentials.password}
            onChange={(event) => setCredentials({ ...credentials, password: event.target.value })}
            placeholder="Password"
          />
          <button type="submit" disabled={loading}>Load workspace</button>
          <div className="demo-list">
            {demoAccounts.map((account) => (
              <button
                type="button"
                key={account.username}
                className="secondary"
                onClick={() => setCredentials({ username: account.username, password: account.password })}
              >
                {account.label}
              </button>
            ))}
          </div>
          <p className="status">{status}</p>
        </form>

        <section className="panel">
          <h2>{isAdmin ? "All pets" : "My pets"}</h2>
          <div className="pet-list">
            {pets.map((pet) => (
              <button
                key={pet.id}
                className={`pet-item ${selectedPetId === pet.id ? "active" : ""}`}
                onClick={() => handleSelectPet(pet.id)}
              >
                <strong>{pet.name}</strong>
                <span>{pet.species || "Unknown species"}</span>
                <span>{pet.ownerName}</span>
              </button>
            ))}
          </div>
        </section>
      </aside>

      <main className="content">
        {petDetail ? (
          <>
            <section className="hero panel">
              <div>
                <p className="eyebrow">{petDetail.ownerName}</p>
                <h2>{petDetail.name}</h2>
                <p>{petDetail.description || `${petDetail.species || "Pet"} tracking workspace`}</p>
              </div>
              <div className="stats">
                <article>
                  <span>Lost status</span>
                  <strong>{petDetail.lost ? "Lost" : "Safe"}</strong>
                </article>
                <article>
                  <span>Latest position</span>
                  <strong>
                    {petDetail.latestLocation
                      ? `${petDetail.latestLocation.latitude.toFixed(5)}, ${petDetail.latestLocation.longitude.toFixed(5)}`
                      : "No position"}
                  </strong>
                </article>
                <article>
                  <span>Geofence</span>
                  <strong>{petDetail.geofence ? `${petDetail.geofence.radiusMeters} m` : "Not set"}</strong>
                </article>
              </div>
            </section>

            <section className="grid">
              <article className="panel">
                <h3>Location history</h3>
                <div className="history">
                  {locations.map((location) => (
                    <div key={location.id} className="row-item">
                      <div>
                        <strong>{location.latitude.toFixed(5)}, {location.longitude.toFixed(5)}</strong>
                        <p>{new Date(location.recordedAt).toLocaleString()}</p>
                      </div>
                      <span>{location.accuracyMeters ?? "-"} m accuracy</span>
                    </div>
                  ))}
                </div>
              </article>

              <article className="panel">
                <h3>Alerts</h3>
                <div className="history">
                  {alerts.map((alert) => (
                    <div key={alert.id} className="row-item">
                      <div>
                        <strong>{alert.type}</strong>
                        <p>{alert.message}</p>
                      </div>
                      <span>{new Date(alert.createdAt).toLocaleString()}</span>
                    </div>
                  ))}
                </div>
              </article>
            </section>

            <section className="grid">
              <form className="panel form-grid" onSubmit={submitLocation}>
                <h3>Add location</h3>
                <input
                  value={locationForm.latitude}
                  onChange={(event) => setLocationForm({ ...locationForm, latitude: event.target.value })}
                  placeholder="Latitude"
                />
                <input
                  value={locationForm.longitude}
                  onChange={(event) => setLocationForm({ ...locationForm, longitude: event.target.value })}
                  placeholder="Longitude"
                />
                <input
                  value={locationForm.speed}
                  onChange={(event) => setLocationForm({ ...locationForm, speed: event.target.value })}
                  placeholder="Speed"
                />
                <input
                  value={locationForm.accuracyMeters}
                  onChange={(event) => setLocationForm({ ...locationForm, accuracyMeters: event.target.value })}
                  placeholder="Accuracy meters"
                />
                <button type="submit" disabled={!isOwnerOrAdmin}>Save location</button>
              </form>

              <form className="panel form-grid" onSubmit={submitGeofence}>
                <h3>Safe area</h3>
                <input
                  value={geofenceForm.centerLatitude}
                  onChange={(event) => setGeofenceForm({ ...geofenceForm, centerLatitude: event.target.value })}
                  placeholder="Center latitude"
                />
                <input
                  value={geofenceForm.centerLongitude}
                  onChange={(event) => setGeofenceForm({ ...geofenceForm, centerLongitude: event.target.value })}
                  placeholder="Center longitude"
                />
                <input
                  value={geofenceForm.radiusMeters}
                  onChange={(event) => setGeofenceForm({ ...geofenceForm, radiusMeters: event.target.value })}
                  placeholder="Radius meters"
                />
                <button type="submit" disabled={!isOwnerOrAdmin}>Save geofence</button>
                <div className="demo-list">
                  <button type="button" className="secondary" onClick={() => toggleLostStatus(true)}>
                    Mark lost
                  </button>
                  <button type="button" className="secondary" onClick={() => toggleLostStatus(false)}>
                    Mark found
                  </button>
                </div>
              </form>
            </section>

            <section className="panel">
              <div className="section-header">
                <div>
                  <h3>Volunteer search groups</h3>
                  <p className="muted">Create a team when a pet is lost, and let volunteers join from the app.</p>
                </div>
              </div>
              {petDetail.lost && isOwnerOrAdmin ? (
                <form className="form-grid wide-form" onSubmit={createSearchGroup}>
                  <input
                    value={searchGroupForm.title}
                    onChange={(event) => setSearchGroupForm({ ...searchGroupForm, title: event.target.value })}
                    placeholder="Search operation title"
                  />
                  <input
                    value={searchGroupForm.notes}
                    onChange={(event) => setSearchGroupForm({ ...searchGroupForm, notes: event.target.value })}
                    placeholder="Meeting point or instructions"
                  />
                  <button type="submit">Create group</button>
                </form>
              ) : null}
              <div className="history">
                {searchGroups.map((group) => (
                  <div key={group.id} className="search-group">
                    <div>
                      <strong>{group.title}</strong>
                      <p>{group.notes || "No notes"}</p>
                      <p className="muted">
                        Volunteers: {group.volunteers.map((volunteer) => volunteer.userName).join(", ") || "None yet"}
                      </p>
                    </div>
                    <button type="button" onClick={() => joinGroup(group.id)}>
                      Join group
                    </button>
                  </div>
                ))}
              </div>
            </section>
          </>
        ) : (
          <section className="panel empty-state">
            <h2>No pet selected</h2>
            <p>Sign in and choose a pet to inspect location history, alerts, geofences, and search groups.</p>
          </section>
        )}
      </main>
    </div>
  );
}
