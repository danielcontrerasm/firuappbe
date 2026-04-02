const API_URL = "http://localhost:8080/api";

function authHeader(credentials) {
  return {
    Authorization: `Basic ${btoa(`${credentials.username}:${credentials.password}`)}`,
    "Content-Type": "application/json"
  };
}

async function request(path, credentials, options = {}) {
  const response = await fetch(`${API_URL}${path}`, {
    ...options,
    headers: {
      ...authHeader(credentials),
      ...(options.headers ?? {})
    }
  });

  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || "Request failed");
  }

  if (response.status === 204) {
    return null;
  }

  return response.json();
}

export const api = {
  me(credentials) {
    return request("/auth/me", credentials);
  },
  listPets(credentials) {
    return request("/pets", credentials);
  },
  getPet(credentials, petId) {
    return request(`/pets/${petId}`, credentials);
  },
  getLocations(credentials, petId) {
    return request(`/pets/${petId}/locations`, credentials);
  },
  addLocation(credentials, petId, payload) {
    return request(`/pets/${petId}/locations`, credentials, {
      method: "POST",
      body: JSON.stringify(payload)
    });
  },
  updateGeofence(credentials, petId, payload) {
    return request(`/pets/${petId}/geofence`, credentials, {
      method: "PUT",
      body: JSON.stringify(payload)
    });
  },
  updateLostStatus(credentials, petId, lost) {
    return request(`/pets/${petId}/lost`, credentials, {
      method: "PUT",
      body: JSON.stringify({ lost })
    });
  },
  getAlerts(credentials, petId) {
    return request(`/pets/${petId}/alerts`, credentials);
  },
  getSearchGroups(credentials, petId) {
    return request(`/pets/${petId}/search-groups`, credentials);
  },
  createSearchGroup(credentials, petId, payload) {
    return request(`/pets/${petId}/search-groups`, credentials, {
      method: "POST",
      body: JSON.stringify(payload)
    });
  },
  joinSearchGroup(credentials, searchGroupId) {
    return request(`/search-groups/${searchGroupId}/join`, credentials, {
      method: "POST"
    });
  }
};
