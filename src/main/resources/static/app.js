const state = {
    token: localStorage.getItem("pettrackerToken") || "",
    me: null,
    walkers: [],
    pets: [],
    requests: [],
    dogWalks: [],
    selectedRequest: null,
    selectedWalk: null,
    map: null,
    mapLine: null,
    mapMarkers: []
};

const el = {
    sessionStatus: document.getElementById("sessionStatus"),
    logoutBtn: document.getElementById("logoutBtn"),
    walkersWall: document.getElementById("walkersWall"),
    toast: document.getElementById("toast"),
    dashboardBand: document.getElementById("dashboardBand"),
    ownerPanel: document.getElementById("ownerPanel"),
    walkerPanel: document.getElementById("walkerPanel"),
    adminPanel: document.getElementById("adminPanel"),
    conversationPanel: document.getElementById("conversationPanel"),
    mapPanel: document.getElementById("mapPanel"),
    ownerRequests: document.getElementById("ownerRequests"),
    walkerRequests: document.getElementById("walkerRequests"),
    walkerDogWalks: document.getElementById("walkerDogWalks"),
    walkerProfileCard: document.getElementById("walkerProfileCard"),
    petsList: document.getElementById("petsList"),
    requestDetail: document.getElementById("requestDetail"),
    requestActions: document.getElementById("requestActions"),
    messagesList: document.getElementById("messagesList"),
    conversationTitle: document.getElementById("conversationTitle"),
    mapTitle: document.getElementById("mapTitle"),
    mapMeta: document.getElementById("mapMeta"),
    sendMyPositionBtn: document.getElementById("sendMyPositionBtn"),
    completeWalkBtn: document.getElementById("completeWalkBtn"),
    adminWalkers: document.getElementById("adminWalkers")
};

document.getElementById("ownerRegisterForm").addEventListener("submit", registerOwner);
document.getElementById("loginForm").addEventListener("submit", login);
document.getElementById("walkerApplicationForm").addEventListener("submit", applyWalker);
document.getElementById("petForm").addEventListener("submit", createPet);
document.getElementById("messageForm").addEventListener("submit", sendMessage);
document.getElementById("adminCreateWalkerForm").addEventListener("submit", createWalkerByAdmin);
document.getElementById("logoutBtn").addEventListener("click", logout);
document.getElementById("refreshWalkersBtn").addEventListener("click", loadPublicWalkers);
document.getElementById("refreshDashboardBtn").addEventListener("click", refreshDashboard);
document.getElementById("refreshMapBtn").addEventListener("click", refreshSelectedWalkMap);
document.getElementById("sendMyPositionBtn").addEventListener("click", sendCurrentPosition);
document.getElementById("completeWalkBtn").addEventListener("click", completeSelectedWalk);

bootstrap();
setInterval(() => {
    if (state.token) {
        refreshSelectedRequestMessages();
        refreshSelectedWalkMap();
    }
}, 15000);

async function bootstrap() {
    await loadPublicWalkers();
    if (state.token) {
        try {
            await loadSession();
        } catch (error) {
            console.error(error);
            logout();
        }
    } else {
        renderSession();
    }
}

async function api(path, options = {}) {
    const headers = new Headers(options.headers || {});
    if (!headers.has("Content-Type") && options.body && !(options.body instanceof FormData)) {
        headers.set("Content-Type", "application/json");
    }
    if (state.token) {
        headers.set("Authorization", `Bearer ${state.token}`);
    }

    const response = await fetch(path, { ...options, headers });
    if (response.status === 204) {
        return null;
    }
    const text = await response.text();
    const data = text ? JSON.parse(text) : null;
    if (!response.ok) {
        throw new Error(data?.message || data?.error || text || "Request failed");
    }
    return data;
}

function setToken(token) {
    state.token = token;
    localStorage.setItem("pettrackerToken", token);
}

function logout() {
    state.token = "";
    state.me = null;
    state.pets = [];
    state.requests = [];
    state.dogWalks = [];
    state.selectedRequest = null;
    state.selectedWalk = null;
    localStorage.removeItem("pettrackerToken");
    renderSession();
    renderDashboard();
    renderRequestDetail();
    renderDogWalkMap([]);
    toast("Sesión cerrada.");
}

async function registerOwner(event) {
    event.preventDefault();
    const payload = formToObject(event.currentTarget);
    const response = await api("/api/auth/register", {
        method: "POST",
        body: JSON.stringify(payload)
    });
    setToken(response.token);
    event.currentTarget.reset();
    toast("Cuenta creada. Ya puedes gestionar mascotas y solicitudes.");
    await loadSession();
}

async function login(event) {
    event.preventDefault();
    const payload = formToObject(event.currentTarget);
    const response = await api("/api/auth/login", {
        method: "POST",
        body: JSON.stringify(payload)
    });
    setToken(response.token);
    event.currentTarget.reset();
    toast("Sesión iniciada.");
    await loadSession();
}

async function applyWalker(event) {
    event.preventDefault();
    const payload = normalizeNumbers(formToObject(event.currentTarget), ["experienceYears", "basePrice"]);
    await api("/api/walker-applications", {
        method: "POST",
        body: JSON.stringify(payload)
    });
    event.currentTarget.reset();
    toast("Postulación enviada. Un administrador debe aprobar tu perfil.");
    await loadPublicWalkers();
}

async function loadSession() {
    state.me = await api("/api/me");
    await refreshDashboard();
    renderSession();
    renderDashboard();
}

async function refreshDashboard() {
    if (!state.token) {
        return;
    }
    const loads = [
        loadPublicWalkers(),
        loadRequests(),
        loadDogWalks()
    ];

    if (state.me && state.me.role !== "WALKER") {
        loads.push(loadPets());
    }
    if (state.me && state.me.role === "WALKER") {
        loads.push(loadMyWalkerProfile());
    }
    if (state.me && state.me.role === "ADMIN") {
        loads.push(loadAdminWalkers());
    }

    await Promise.all(loads);
    renderDashboard();
    if (state.selectedRequest) {
        await selectRequest(state.selectedRequest.id);
    }
    if (state.selectedWalk) {
        await selectDogWalk(state.selectedWalk.id);
    }
}

async function loadPublicWalkers() {
    state.walkers = await api("/api/public/walkers");
    renderWalkers();
}

async function loadPets() {
    state.pets = await api("/api/pets");
}

async function loadRequests() {
    state.requests = await api("/api/walk-requests");
}

async function loadDogWalks() {
    state.dogWalks = await api("/api/dog-walks");
}

async function loadMyWalkerProfile() {
    state.myWalkerProfile = await api("/api/walkers/me");
}

async function loadAdminWalkers() {
    state.adminWalkers = await api("/api/admin/walkers");
}

function renderSession() {
    if (!state.me) {
        el.sessionStatus.textContent = "No has iniciado sesión.";
        el.logoutBtn.hidden = true;
        return;
    }
    el.sessionStatus.innerHTML = `
        <strong>${escapeHtml(state.me.name)}</strong><br>
        ${escapeHtml(state.me.email)}<br>
        Rol: ${state.me.role}
    `;
    el.logoutBtn.hidden = false;
}

function renderWalkers() {
    if (!state.walkers.length) {
        el.walkersWall.innerHTML = `<div class="empty-state">No hay paseadores aprobados todavía.</div>`;
        return;
    }

    el.walkersWall.innerHTML = state.walkers.map((walker) => `
        <article class="walker-card">
            <header>
                <div>
                    <strong>${escapeHtml(walker.name)}</strong>
                    <div class="muted">${escapeHtml(walker.neighborhood || "Zona sin definir")}</div>
                </div>
                <span class="tag ok">$${formatMoney(walker.basePrice)} base</span>
            </header>
            <div>${escapeHtml(walker.bio || "Sin biografía")}</div>
            <div class="tag-row">
                <span class="tag">${escapeHtml((walker.experienceYears ?? 0) + " años")}</span>
                <span class="tag">${escapeHtml(walker.services || "Paseos")}</span>
                <span class="tag">${escapeHtml(walker.availability || "Disponibilidad por acordar")}</span>
            </div>
            <div class="muted">${escapeHtml(walker.priceNotes || "Tarifa final a convenir por chat.")}</div>
            ${renderRequestFormForWalker(walker)}
        </article>
    `).join("");

    el.walkersWall.querySelectorAll("form[data-walker-form]").forEach((form) => {
        form.addEventListener("submit", submitWalkRequest);
    });
}

function renderRequestFormForWalker(walker) {
    if (!state.me || (state.me.role !== "USER" && state.me.role !== "ADMIN")) {
        return `<div class="empty-state">Inicia sesión como dueño para solicitar un paseo o abrir el chat.</div>`;
    }

    const petOptions = state.pets.length
        ? state.pets.map((pet) => `<option value="${pet.id}">${escapeHtml(pet.name)} (${escapeHtml(pet.type)})</option>`).join("")
        : `<option value="">Sin mascotas registradas</option>`;

    return `
        <form class="stack-form compact" data-walker-form="${walker.id}">
            <select name="petId">${petOptions}</select>
            <input name="requestedStart" type="datetime-local">
            <input name="durationMinutes" type="number" min="15" step="15" placeholder="Duración en minutos">
            <input name="ownerBudget" type="number" min="0" step="0.01" placeholder="Presupuesto propuesto">
            <input name="serviceAddress" placeholder="Dirección o punto de encuentro">
            <textarea name="notes" rows="3" placeholder="Mensaje inicial para convenir detalles"></textarea>
            <button type="submit">Solicitar paseo / abrir chat</button>
        </form>
    `;
}

async function submitWalkRequest(event) {
    event.preventDefault();
    const walkerProfileId = Number(event.currentTarget.dataset.walkerForm);
    const payload = normalizeNumbers(formToObject(event.currentTarget), ["petId", "durationMinutes", "ownerBudget"]);
    payload.walkerProfileId = walkerProfileId;
    if (!payload.petId) {
        delete payload.petId;
    }
    const request = await api("/api/walk-requests", {
        method: "POST",
        body: JSON.stringify(payload)
    });
    event.currentTarget.reset();
    toast("Solicitud enviada. Ya puedes seguir la conversación.");
    await refreshDashboard();
    await selectRequest(request.id);
}

async function createPet(event) {
    event.preventDefault();
    const payload = normalizeNumbers(formToObject(event.currentTarget), ["age", "weight"]);
    await api("/api/pets", {
        method: "POST",
        body: JSON.stringify(payload)
    });
    event.currentTarget.reset();
    toast("Mascota registrada.");
    await refreshDashboard();
}

function renderDashboard() {
    const loggedIn = Boolean(state.me);
    el.dashboardBand.hidden = !loggedIn;
    if (!loggedIn) {
        renderWalkers();
        return;
    }

    const isWalker = state.me.role === "WALKER";
    const isAdmin = state.me.role === "ADMIN";
    el.ownerPanel.hidden = isWalker;
    el.walkerPanel.hidden = !isWalker;
    el.adminPanel.hidden = !isAdmin;

    if (!isWalker) {
        renderPets();
        renderRequestList(el.ownerRequests, state.requests, "Selecciona una solicitud para conversar, aceptar o ver el paseo.");
    }
    if (isWalker) {
        renderWalkerProfile();
        renderRequestList(el.walkerRequests, state.requests, "Aquí verás las solicitudes que te enviaron.");
        renderDogWalkList();
    }
    if (isAdmin) {
        renderAdminWalkers();
    }
    renderWalkers();
}

function renderPets() {
    if (!state.pets.length) {
        el.petsList.innerHTML = `<div class="empty-state">No tienes mascotas registradas.</div>`;
        return;
    }
    el.petsList.innerHTML = state.pets.map((pet) => `
        <article class="list-item">
            <header>
                <strong>${escapeHtml(pet.name)}</strong>
                <span class="tag ${pet.status === "ACTIVE" ? "ok" : "warn"}">${escapeHtml(pet.statusLabel || pet.status)}</span>
            </header>
            <div class="meta-line">
                <span>${escapeHtml(pet.type || "Sin tipo")}</span>
                <span>${escapeHtml(pet.race || "Sin raza")}</span>
                <span>${escapeHtml(pet.neighborhood || "Ubicación sin resolver")}</span>
            </div>
        </article>
    `).join("");
}

function renderWalkerProfile() {
    if (!state.myWalkerProfile) {
        el.walkerProfileCard.innerHTML = `<div class="empty-state">No existe perfil de paseador vinculado a tu usuario.</div>`;
        return;
    }
    const walker = state.myWalkerProfile;
    el.walkerProfileCard.innerHTML = `
        <article class="list-item">
            <header>
                <strong>${escapeHtml(walker.name)}</strong>
                <span class="tag ${walker.approvalStatus === "APPROVED" ? "ok" : "warn"}">${walker.approvalStatus}</span>
            </header>
            <div>${escapeHtml(walker.bio || "Sin biografía")}</div>
            <div class="meta-line">
                <span>Zona: ${escapeHtml(walker.neighborhood || "Sin zona")}</span>
                <span>Tarifa base: $${formatMoney(walker.basePrice)}</span>
            </div>
            <div class="muted">${escapeHtml(walker.priceNotes || "Sin notas de tarifa")}</div>
        </article>
    `;
}

function renderRequestList(container, requests, emptyText) {
    if (!requests.length) {
        container.innerHTML = `<div class="empty-state">${emptyText}</div>`;
        return;
    }
    container.innerHTML = requests.map((request) => `
        <article class="list-item">
            <header>
                <div>
                    <strong>${escapeHtml(request.petName || "Paseo sin mascota asignada")}</strong>
                    <div class="muted">${escapeHtml(state.me.role === "WALKER" ? request.ownerName : request.walkerName)}</div>
                </div>
                <span class="tag ${request.status === "ACCEPTED" || request.status === "IN_PROGRESS" || request.status === "COMPLETED" ? "ok" : "warn"}">${request.status}</span>
            </header>
            <div class="meta-line">
                <span>${formatDateTime(request.requestedStart)}</span>
                <span>${request.durationMinutes ? request.durationMinutes + " min" : "Duración por definir"}</span>
                <span>Dueño $${formatMoney(request.ownerBudget)}</span>
                <span>Paseador $${formatMoney(request.walkerQuotedPrice)}</span>
            </div>
            <button data-request-id="${request.id}" class="icon-button">Abrir detalle</button>
        </article>
    `).join("");

    container.querySelectorAll("[data-request-id]").forEach((button) => {
        button.addEventListener("click", async () => {
            await selectRequest(Number(button.dataset.requestId));
        });
    });
}

function renderDogWalkList() {
    if (!state.dogWalks.length) {
        el.walkerDogWalks.innerHTML = `<div class="empty-state">Todavía no hay paseos iniciados.</div>`;
        return;
    }
    el.walkerDogWalks.innerHTML = state.dogWalks.map((walk) => `
        <article class="list-item">
            <header>
                <div>
                    <strong>${escapeHtml(walk.petName || "Paseo sin mascota")}</strong>
                    <div class="muted">${escapeHtml(walk.ownerName)}</div>
                </div>
                <span class="tag ${walk.status === "COMPLETED" ? "ok" : "warn"}">${walk.status}</span>
            </header>
            <div class="meta-line">
                <span>Inicio: ${formatInstant(walk.startedAt)}</span>
                <span>Tarifa: $${formatMoney(walk.agreedPrice)}</span>
            </div>
            <button data-walk-id="${walk.id}" class="icon-button">Ver mapa</button>
        </article>
    `).join("");

    el.walkerDogWalks.querySelectorAll("[data-walk-id]").forEach((button) => {
        button.addEventListener("click", async () => {
            await selectDogWalk(Number(button.dataset.walkId));
        });
    });
}

function renderAdminWalkers() {
    if (!state.adminWalkers?.length) {
        el.adminWalkers.innerHTML = `<div class="empty-state">No hay perfiles para administrar.</div>`;
        return;
    }
    el.adminWalkers.innerHTML = state.adminWalkers.map((walker) => `
        <article class="list-item">
            <header>
                <div>
                    <strong>${escapeHtml(walker.name)}</strong>
                    <div class="muted">${escapeHtml(walker.email)}</div>
                </div>
                <span class="tag ${walker.approvalStatus === "APPROVED" ? "ok" : "warn"}">${walker.approvalStatus}</span>
            </header>
            <div class="meta-line">
                <span>${escapeHtml(walker.neighborhood || "Sin zona")}</span>
                <span>$${formatMoney(walker.basePrice)}</span>
                <span>${walker.active ? "Activo" : "Inactivo"}</span>
            </div>
            <div class="panel-actions">
                <button data-admin-action="approve" data-walker-id="${walker.id}" class="icon-button">Aprobar</button>
                <button data-admin-action="reject" data-walker-id="${walker.id}" class="icon-button">Rechazar</button>
                <button data-admin-action="toggle" data-walker-id="${walker.id}" class="icon-button">${walker.active ? "Desactivar" : "Activar"}</button>
                <button data-admin-action="delete" data-walker-id="${walker.id}" class="danger">Eliminar</button>
            </div>
        </article>
    `).join("");

    el.adminWalkers.querySelectorAll("[data-admin-action]").forEach((button) => {
        button.addEventListener("click", () => handleAdminWalkerAction(button.dataset.adminAction, Number(button.dataset.walkerId)));
    });
}

async function createWalkerByAdmin(event) {
    event.preventDefault();
    const payload = normalizeNumbers(formToObject(event.currentTarget), ["basePrice"]);
    await api("/api/admin/walkers", {
        method: "POST",
        body: JSON.stringify(payload)
    });
    event.currentTarget.reset();
    toast("Paseador registrado desde backend.");
    await refreshDashboard();
}

async function handleAdminWalkerAction(action, walkerId) {
    const walker = state.adminWalkers.find((item) => item.id === walkerId);
    if (!walker) {
        return;
    }
    if (action === "delete") {
        await api(`/api/admin/walkers/${walkerId}`, { method: "DELETE" });
        toast("Paseador eliminado.");
        await refreshDashboard();
        return;
    }

    const payload = {
        name: walker.name,
        email: walker.email,
        phone: walker.phone,
        bio: walker.bio,
        neighborhood: walker.neighborhood,
        experienceYears: walker.experienceYears,
        basePrice: walker.basePrice,
        priceNotes: walker.priceNotes,
        services: walker.services,
        availability: walker.availability,
        active: action === "toggle" ? !walker.active : walker.active,
        approvalStatus: action === "approve"
            ? "APPROVED"
            : action === "reject"
                ? "REJECTED"
                : walker.approvalStatus
    };

    await api(`/api/admin/walkers/${walkerId}`, {
        method: "PUT",
        body: JSON.stringify(payload)
    });
    toast("Perfil de paseador actualizado.");
    await refreshDashboard();
}

async function selectRequest(requestId) {
    state.selectedRequest = await api(`/api/walk-requests/${requestId}`);
    const messages = await api(`/api/walk-requests/${requestId}/messages`);
    renderRequestDetail(messages);
    if (state.selectedRequest.activeDogWalkId) {
        await selectDogWalk(state.selectedRequest.activeDogWalkId);
    }
}

function renderRequestDetail(messages = []) {
    if (!state.selectedRequest) {
        el.conversationPanel.hidden = true;
        return;
    }

    const request = state.selectedRequest;
    el.conversationPanel.hidden = false;
    el.conversationTitle.textContent = `Solicitud #${request.id} · ${request.petName || "Sin mascota"}`;
    el.requestDetail.innerHTML = `
        <strong>Paseador:</strong> ${escapeHtml(request.walkerName)}<br>
        <strong>Dueño:</strong> ${escapeHtml(request.ownerName)}<br>
        <strong>Fecha solicitada:</strong> ${formatDateTime(request.requestedStart)}<br>
        <strong>Duración:</strong> ${request.durationMinutes ? request.durationMinutes + " min" : "Por definir"}<br>
        <strong>Dirección:</strong> ${escapeHtml(request.serviceAddress || "Por definir")}<br>
        <strong>Presupuesto dueño:</strong> $${formatMoney(request.ownerBudget)}<br>
        <strong>Tarifa paseador:</strong> $${formatMoney(request.walkerQuotedPrice)}<br>
        <strong>Estado:</strong> ${request.status}
    `;

    el.requestActions.innerHTML = "";
    renderRequestActions(request);
    renderMessages(messages);
}

function renderRequestActions(request) {
    const fragment = document.createDocumentFragment();

    if (state.me.role === "WALKER") {
        const wrapper = document.createElement("form");
        wrapper.className = "inline-grid";
        wrapper.innerHTML = `
            <input name="walkerQuotedPrice" type="number" min="0" step="0.01" placeholder="Tarifa propuesta">
            <select name="status">
                <option value="NEGOTIATING">En negociación</option>
                <option value="ACCEPTED">Aceptar condiciones</option>
                <option value="REJECTED">Rechazar</option>
            </select>
            <button type="submit">Guardar tarifa / estado</button>
        `;
        wrapper.addEventListener("submit", async (event) => {
            event.preventDefault();
            const payload = normalizeNumbers(formToObject(event.currentTarget), ["walkerQuotedPrice"]);
            await api(`/api/walk-requests/${request.id}/quote`, {
                method: "PUT",
                body: JSON.stringify(payload)
            });
            toast("Tarifa o estado actualizados.");
            await refreshDashboard();
        });
        fragment.appendChild(wrapper);

        if (request.status === "ACCEPTED" || request.status === "NEGOTIATING") {
            const startButton = document.createElement("button");
            startButton.textContent = "Iniciar paseo";
            startButton.className = "icon-button";
            startButton.addEventListener("click", async () => {
                const walk = await api(`/api/walk-requests/${request.id}/start`, { method: "POST" });
                toast("Paseo iniciado.");
                await refreshDashboard();
                await selectDogWalk(walk.id);
            });
            fragment.appendChild(startButton);
        }
    }

    if (state.me.role !== "WALKER") {
        ["ACCEPTED", "REJECTED", "CANCELLED"].forEach((status) => {
            const button = document.createElement("button");
            button.textContent = status === "ACCEPTED" ? "Aceptar propuesta" : status === "REJECTED" ? "Rechazar" : "Cancelar";
            button.className = status === "ACCEPTED" ? "" : "icon-button";
            button.addEventListener("click", async () => {
                await api(`/api/walk-requests/${request.id}/decision`, {
                    method: "PUT",
                    body: JSON.stringify({ status })
                });
                toast("Estado de la solicitud actualizado.");
                await refreshDashboard();
            });
            fragment.appendChild(button);
        });
    }

    el.requestActions.appendChild(fragment);
}

function renderMessages(messages) {
    if (!messages.length) {
        el.messagesList.innerHTML = `<div class="empty-state">Todavía no hay mensajes en esta solicitud.</div>`;
        return;
    }
    el.messagesList.innerHTML = messages.map((message) => `
        <article class="message-item">
            <strong>${escapeHtml(message.senderName)}</strong>
            <div>${escapeHtml(message.body)}</div>
            <small>${formatInstant(message.createdAt)}</small>
        </article>
    `).join("");
}

async function sendMessage(event) {
    event.preventDefault();
    if (!state.selectedRequest) {
        toast("Selecciona una solicitud primero.");
        return;
    }
    const payload = formToObject(event.currentTarget);
    await api(`/api/walk-requests/${state.selectedRequest.id}/messages`, {
        method: "POST",
        body: JSON.stringify(payload)
    });
    event.currentTarget.reset();
    await refreshSelectedRequestMessages();
}

async function refreshSelectedRequestMessages() {
    if (!state.selectedRequest || !state.token) {
        return;
    }
    try {
        state.selectedRequest = await api(`/api/walk-requests/${state.selectedRequest.id}`);
        const messages = await api(`/api/walk-requests/${state.selectedRequest.id}/messages`);
        renderRequestDetail(messages);
    } catch (error) {
        console.warn(error);
    }
}

async function selectDogWalk(dogWalkId) {
    state.selectedWalk = await api(`/api/dog-walks/${dogWalkId}`);
    const positions = await api(`/api/dog-walks/${dogWalkId}/positions`);
    renderSelectedWalk(positions);
}

function renderSelectedWalk(positions) {
    if (!state.selectedWalk) {
        el.mapPanel.hidden = true;
        return;
    }

    el.mapPanel.hidden = false;
    el.mapTitle.textContent = `Mapa del paseo #${state.selectedWalk.id}`;
    el.mapMeta.innerHTML = `
        <strong>Mascota:</strong> ${escapeHtml(state.selectedWalk.petName || "Sin mascota")}<br>
        <strong>Paseador:</strong> ${escapeHtml(state.selectedWalk.walkerName)}<br>
        <strong>Dueño:</strong> ${escapeHtml(state.selectedWalk.ownerName)}<br>
        <strong>Estado:</strong> ${state.selectedWalk.status}<br>
        <strong>Última posición:</strong> ${state.selectedWalk.lastPosition
            ? `${state.selectedWalk.lastPosition.latitude.toFixed(5)}, ${state.selectedWalk.lastPosition.longitude.toFixed(5)}`
            : "Sin posiciones aún"}
    `;

    const walkerUserSelected = state.me
        && state.selectedWalk
        && state.me.walkerProfileId
        && state.me.walkerProfileId === state.selectedWalk.walkerProfileId;

    el.sendMyPositionBtn.hidden = !walkerUserSelected || state.selectedWalk.status !== "IN_PROGRESS";
    el.completeWalkBtn.hidden = !walkerUserSelected || state.selectedWalk.status !== "IN_PROGRESS";

    renderDogWalkMap(positions);
}

async function refreshSelectedWalkMap() {
    if (!state.selectedWalk || !state.token) {
        return;
    }
    try {
        state.selectedWalk = await api(`/api/dog-walks/${state.selectedWalk.id}`);
        const positions = await api(`/api/dog-walks/${state.selectedWalk.id}/positions`);
        renderSelectedWalk(positions);
    } catch (error) {
        console.warn(error);
    }
}

function renderDogWalkMap(positions) {
    if (!state.map) {
        state.map = L.map("walkMap");
        L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
            maxZoom: 19,
            attribution: "&copy; OpenStreetMap"
        }).addTo(state.map);
        state.map.setView([4.711, -74.0721], 12);
    }

    state.mapMarkers.forEach((marker) => marker.remove());
    state.mapMarkers = [];
    if (state.mapLine) {
        state.mapLine.remove();
        state.mapLine = null;
    }

    if (!positions.length) {
        state.map.invalidateSize();
        return;
    }

    const latLngs = positions.map((position) => [position.latitude, position.longitude]);
    state.mapLine = L.polyline(latLngs, { color: "#166534", weight: 4 }).addTo(state.map);
    latLngs.forEach((latLng, index) => {
        const marker = L.circleMarker(latLng, {
            radius: index === latLngs.length - 1 ? 7 : 5,
            color: index === latLngs.length - 1 ? "#b91c1c" : "#166534",
            fillOpacity: 0.9
        }).addTo(state.map);
        state.mapMarkers.push(marker);
    });
    state.map.fitBounds(state.mapLine.getBounds(), { padding: [24, 24] });
    state.map.invalidateSize();
}

async function sendCurrentPosition() {
    if (!state.selectedWalk) {
        return;
    }
    if (!navigator.geolocation) {
        toast("El navegador no soporta geolocalización.");
        return;
    }

    navigator.geolocation.getCurrentPosition(async (position) => {
        try {
            await api(`/api/dog-walks/${state.selectedWalk.id}/positions`, {
                method: "POST",
                body: JSON.stringify({
                    latitude: position.coords.latitude,
                    longitude: position.coords.longitude
                })
            });
            toast("Posición enviada.");
            await refreshSelectedWalkMap();
        } catch (error) {
            toast(error.message);
        }
    }, () => {
        toast("No fue posible obtener tu posición.");
    }, { enableHighAccuracy: true });
}

async function completeSelectedWalk() {
    if (!state.selectedWalk) {
        return;
    }
    await api(`/api/dog-walks/${state.selectedWalk.id}/complete`, { method: "POST" });
    toast("Paseo marcado como finalizado.");
    await refreshDashboard();
}

function formToObject(form) {
    return Object.fromEntries(new FormData(form).entries());
}

function normalizeNumbers(payload, numberFields) {
    numberFields.forEach((field) => {
        if (payload[field] === "" || payload[field] == null) {
            delete payload[field];
            return;
        }
        payload[field] = Number(payload[field]);
    });
    return payload;
}

function formatMoney(value) {
    if (value == null || Number.isNaN(Number(value))) {
        return "0";
    }
    return Number(value).toLocaleString("es-CO", { maximumFractionDigits: 0 });
}

function formatDateTime(value) {
    if (!value) {
        return "Sin fecha";
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return value;
    }
    return date.toLocaleString("es-CO");
}

function formatInstant(value) {
    if (!value) {
        return "Sin fecha";
    }
    return new Date(value).toLocaleString("es-CO");
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

function toast(message) {
    el.toast.hidden = false;
    el.toast.textContent = message;
    clearTimeout(toast._timer);
    toast._timer = setTimeout(() => {
        el.toast.hidden = true;
    }, 3000);
}
