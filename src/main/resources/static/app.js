const state = {
    token: localStorage.getItem("pettrackerToken") || "",
    me: null,
    walkers: [],
    pets: [],
    petWalkDistancesByPetId: {},
    alerts: [],
    requests: [],
    dogWalks: [],
    searchGroups: [],
    adminMetrics: null,
    selectedRequest: null,
    selectedWalk: null,
    map: null,
    mapLine: null,
    mapMarkers: []
};

const savedTheme = localStorage.getItem("pettrackerTheme") || "firuapp";
document.body.dataset.theme = savedTheme;

const el = {
    sessionStatus: document.getElementById("sessionStatus"),
    logoutBtn: document.getElementById("logoutBtn"),
    walkersWall: document.getElementById("walkersWall"),
    toast: document.getElementById("toast"),
    dashboardBand: document.getElementById("dashboardBand"),
    alertsPanel: document.getElementById("alertsPanel"),
    alertsList: document.getElementById("alertsList"),
    searchGroupsList: document.getElementById("searchGroupsList"),
    dashboardGreeting: document.getElementById("dashboardGreeting"),
    dashboardSubtitle: document.getElementById("dashboardSubtitle"),
    dashboardRole: document.getElementById("dashboardRole"),
    dashboardStats: document.getElementById("dashboardStats"),
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
    adminMetrics: document.getElementById("adminMetrics"),
    adminWalkers: document.getElementById("adminWalkers")
};

const themeToggleBtn = document.getElementById("themeToggleBtn");
themeToggleBtn.addEventListener("click", () => {
    const nextTheme = document.body.dataset.theme === "firuapp" ? "default" : "firuapp";
    document.body.dataset.theme = nextTheme;
    localStorage.setItem("pettrackerTheme", nextTheme);
    themeToggleBtn.setAttribute("aria-pressed", String(nextTheme === "firuapp"));
    themeToggleBtn.innerHTML = nextTheme === "firuapp"
        ? "<span aria-hidden=\"true\">◑</span> Vista clásica"
        : "<span aria-hidden=\"true\">◐</span> Vista FiruApp";
});

themeToggleBtn.setAttribute("aria-pressed", String(savedTheme === "firuapp"));
themeToggleBtn.innerHTML = savedTheme === "firuapp"
    ? "<span aria-hidden=\"true\">◑</span> Vista clásica"
    : "<span aria-hidden=\"true\">◐</span> Vista FiruApp";

document.getElementById("ownerRegisterForm").addEventListener("submit", registerOwner);
document.getElementById("loginForm").addEventListener("submit", login);
document.getElementById("walkerApplicationForm").addEventListener("submit", applyWalker);
document.getElementById("petForm").addEventListener("submit", createPet);
document.getElementById("searchGroupForm").addEventListener("submit", createSearchGroup);
document.getElementById("petShareForm").addEventListener("submit", sharePet);
document.getElementById("temporaryAccessForm").addEventListener("submit", createTemporaryAccess);
document.querySelector("#temporaryAccessForm select[name=petId]").addEventListener("change", loadTemporaryAccesses);
document.querySelector("#temporaryAccessForm [name=recurring]").addEventListener("change", updateTemporaryAccessFormState);
document.getElementById("temporaryAccessList").addEventListener("click", revokeTemporaryAccess);
document.getElementById("messageForm").addEventListener("submit", sendMessage);
document.getElementById("adminCreateWalkerForm").addEventListener("submit", createWalkerByAdmin);
document.getElementById("logoutBtn").addEventListener("click", logout);
document.getElementById("refreshWalkersBtn").addEventListener("click", loadPublicWalkers);
document.getElementById("refreshDashboardBtn").addEventListener("click", refreshDashboard);
document.getElementById("refreshAlertsBtn").addEventListener("click", refreshAlerts);
document.getElementById("refreshSearchGroupsBtn").addEventListener("click", refreshSearchGroups);
document.getElementById("refreshMapBtn").addEventListener("click", refreshSelectedWalkMap);
document.getElementById("sendMyPositionBtn").addEventListener("click", sendCurrentPosition);
document.getElementById("completeWalkBtn").addEventListener("click", completeSelectedWalk);
document.getElementById("alertsList").addEventListener("click", dismissAlert);
document.getElementById("searchGroupsList").addEventListener("click", joinSearchGroup);
document.getElementById("petsList").addEventListener("click", exportPetHistory);
document.querySelectorAll("[data-demo-email]").forEach((button) => {
    button.addEventListener("click", () => fillDemoLogin(button.dataset.demoEmail));
});

bootstrap();
setInterval(() => {
    if (state.token) {
        refreshInbox();
        refreshSelectedRequestMessages();
        refreshSelectedWalkMap();
        refreshAlerts();
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
    state.petWalkDistancesByPetId = {};
    state.alerts = [];
    state.searchGroups = [];
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

function fillDemoLogin(email) {
    const loginForm = document.getElementById("loginForm");
    loginForm.elements.email.value = email;
    loginForm.elements.password.value = "FiruDemo123!";
    loginForm.elements.email.focus();
    loginForm.scrollIntoView({ behavior: "smooth", block: "center" });
    toast("Credenciales demo cargadas. Pulsa Entrar.");
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
        loadDogWalks(),
        loadAlerts(),
        loadSearchGroups()
    ];

    if (state.me && state.me.role !== "WALKER") {
        loads.push(loadPets());
    }
    if (state.me && state.me.role === "WALKER") {
        loads.push(loadMyWalkerProfile());
    }
    if (state.me && state.me.role === "ADMIN") {
        loads.push(loadAdminWalkers());
        loads.push(loadAdminMetrics());
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

async function loadAlerts() {
    state.alerts = await api("/api/alerts");
    renderAlerts();
}

async function loadSearchGroups() {
    state.searchGroups = await api("/api/search-groups");
    renderSearchGroups();
}

async function refreshSearchGroups() {
    if (!state.token || !state.me) {
        return;
    }
    try {
        await loadSearchGroups();
        toast("Grupos de búsqueda actualizados.");
    } catch (error) {
        toast(error.message);
    }
}

async function joinSearchGroup(event) {
    const button = event.target.closest("[data-join-search-group]");
    if (!button) {
        return;
    }

    button.disabled = true;
    try {
        const response = await api(`/api/search-groups/${button.dataset.joinSearchGroup}/join`, {
            method: "POST"
        });
        toast(response.message || "Respuesta recibida del grupo.");
        await loadSearchGroups();
    } catch (error) {
        button.disabled = false;
        toast(error.message);
    }
}

async function refreshAlerts() {
    if (!state.token || !state.me) {
        return;
    }
    try {
        await loadAlerts();
    } catch (error) {
        console.warn(error);
    }
}

async function refreshInbox() {
    if (!state.token || !state.me) {
        return;
    }
    try {
        await Promise.all([loadRequests(), loadDogWalks()]);
        if (state.me.role === "WALKER" || state.me.role === "ADMIN") {
            renderDashboard();
        } else {
            renderDashboardStats(false, false);
            renderRequestList(el.ownerRequests, state.requests, "Selecciona una solicitud para conversar, aceptar o ver el paseo.");
        }
    } catch (error) {
        console.warn(error);
    }
}

async function dismissAlert(event) {
    const button = event.target.closest("[data-dismiss-alert]");
    if (!button) {
        return;
    }

    button.disabled = true;
    try {
        await api(`/api/alerts/${button.dataset.dismissAlert}`, { method: "DELETE" });
        state.alerts = state.alerts.filter((alert) => String(alert.id) !== button.dataset.dismissAlert);
        renderAlerts();
    } catch (error) {
        button.disabled = false;
        toast(error.message);
    }
}

async function loadPets() {
    state.pets = await api("/api/pets");
    await loadPetWalkDistances();
    renderSearchGroupPetOptions();
    renderPetShareOptions();
    renderTemporaryAccessPetOptions();
    updateTemporaryAccessFormState();
}

async function createTemporaryAccess(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const payload = formToObject(form);
    const recurring = form.querySelector('[name="recurring"]').checked;
    const body = {
        email: payload.email,
        startsAt: new Date(payload.startsAt).toISOString(),
        expiresAt: new Date(payload.expiresAt).toISOString(),
        recurring,
        recurringDays: [...form.querySelectorAll('[name="recurringDays"]:checked')].map((input) => input.value),
        recurringStartTime: payload.recurringStartTime || null,
        recurringEndTime: payload.recurringEndTime || null,
        canViewLocation: form.querySelector('[name="canViewLocation"]').checked,
        canViewAlerts: form.querySelector('[name="canViewAlerts"]').checked,
        canViewHistory: form.querySelector('[name="canViewHistory"]').checked
    };
    try {
        await api(`/api/pets/${payload.petId}/temporary-access`, { method: "POST", body: JSON.stringify(body) });
        form.querySelector('[name="email"]').value = "";
        document.getElementById("temporaryAccessMessage").textContent = "Acceso creado y correo enviado.";
        toast("Acceso temporal enviado por correo.");
        await loadTemporaryAccesses();
    } catch (error) {
        toast(error.message);
    }
}

async function loadTemporaryAccesses() {
    const petId = document.querySelector("#temporaryAccessForm select[name=petId]").value;
    const list = document.getElementById("temporaryAccessList");
    if (!petId) {
        list.innerHTML = "";
        return;
    }
    try {
        const accesses = await api(`/api/pets/${petId}/temporary-access`);
        list.innerHTML = accesses.length ? accesses.map((access) => `
            <article class="list-item compact-item">
                <strong>${escapeHtml(access.userEmail || access.userName || "Usuario")}</strong>
                <div class="meta-line"><span>${formatDateTime(access.startsAt)} → ${formatDateTime(access.expiresAt)}</span><span>${access.currentlyActive ? "Activo ahora" : access.active ? "Programado" : "Revocado"}</span></div>
                <button type="button" class="icon-button" data-revoke-temporary-access="${access.id}" ${access.active ? "" : "disabled"}>Revocar</button>
            </article>`).join("") : `<div class="empty-state compact-empty">No hay accesos temporales para esta mascota.</div>`;
    } catch (error) {
        list.innerHTML = `<div class="empty-state compact-empty">${escapeHtml(error.message)}</div>`;
    }
}

async function revokeTemporaryAccess(event) {
    const button = event.target.closest("[data-revoke-temporary-access]");
    if (!button) return;
    const petId = document.querySelector("#temporaryAccessForm select[name=petId]").value;
    button.disabled = true;
    try {
        await api(`/api/pets/${petId}/temporary-access/${button.dataset.revokeTemporaryAccess}`, { method: "DELETE" });
        toast("Acceso temporal revocado.");
        await loadTemporaryAccesses();
    } catch (error) {
        button.disabled = false;
        toast(error.message);
    }
}

async function sharePet(event) {
    event.preventDefault();
    const payload = formToObject(event.currentTarget);
    try {
        await api(`/api/pets/${payload.petId}/shares`, {
            method: "POST",
            body: JSON.stringify({ email: payload.email })
        });
        event.currentTarget.reset();
        toast("Acceso compartido con la persona autorizada.");
    } catch (error) {
        toast(error.message);
    }
}

async function createSearchGroup(event) {
    event.preventDefault();
    const payload = formToObject(event.currentTarget);
    payload.petId = Number(payload.petId);
    payload.status = "active";
    try {
        await api("/api/search-groups", { method: "POST", body: JSON.stringify(payload) });
        event.currentTarget.reset();
        toast(payload.visibility === "PUBLIC" ? "Grupo público creado: cualquiera puede verlo y unirse." : "Grupo privado creado.");
        await loadSearchGroups();
    } catch (error) {
        toast(error.message);
    }
}

function renderSearchGroupPetOptions() {
    const select = document.querySelector("#searchGroupForm select[name=petId]");
    if (!select) return;
    const current = select.value;
    select.innerHTML = `<option value="">Selecciona la mascota perdida</option>` + state.pets
        .map((pet) => `<option value="${pet.id}">${escapeHtml(pet.name)} · #${pet.id}</option>`)
        .join("");
    select.value = current;
}

function renderPetShareOptions() {
    const select = document.querySelector("#petShareForm select[name=petId]");
    if (!select) return;
    const current = select.value;
    select.innerHTML = `<option value="">Selecciona la mascota</option>` + state.pets
        .map((pet) => `<option value="${pet.id}">${escapeHtml(pet.name)} · #${pet.id}</option>`)
        .join("");
    select.value = current;
}

function renderTemporaryAccessPetOptions() {
    const select = document.querySelector("#temporaryAccessForm select[name=petId]");
    if (!select) return;
    const current = select.value;
    select.innerHTML = `<option value="">Selecciona la mascota</option>` + state.pets
        .map((pet) => `<option value="${pet.id}">${escapeHtml(pet.name)} · #${pet.id}</option>`)
        .join("");
    select.value = current;
}

function updateTemporaryAccessFormState() {
    const form = document.getElementById("temporaryAccessForm");
    if (!form || !state.me) return;
    const plan = state.me.plan || "BASIC";
    const basic = plan === "BASIC" && state.me.role !== "ADMIN";
    const recurring = form.querySelector('[name="recurring"]');
    form.querySelectorAll("input, select, button").forEach((input) => {
        if (input.dataset.revokeTemporaryAccess) return;
        input.disabled = basic;
    });
    recurring.disabled = basic || plan !== "PREMIUM";
    form.querySelector(".temporary-recurring-fields").hidden = !recurring.checked || recurring.disabled;
    form.querySelector("#temporaryAccessMessage").textContent = basic
        ? "Disponible desde el plan Family."
        : plan === "PREMIUM" ? "Premium permite horarios recurrentes." : "Family permite accesos con fecha de inicio y vencimiento.";
}

async function exportPetHistory(event) {
    const button = event.target.closest("[data-export-pet-history]");
    if (!button) return;
    button.disabled = true;
    try {
        const format = button.dataset.exportFormat || "pdf";
        const response = await fetch(`/api/pets/${button.dataset.exportPetHistory}/history/export?format=${format}`, {
            headers: { Authorization: `Bearer ${state.token}` }
        });
        if (!response.ok) {
            const data = await response.json().catch(() => null);
            throw new Error(data?.message || "La descarga de reportes es exclusiva de Premium.");
        }
        const blob = await response.blob();
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url;
        link.download = `pettracker-route-report.${format}`;
        link.click();
        URL.revokeObjectURL(url);
    } catch (error) {
        toast(error.message);
    } finally {
        button.disabled = false;
    }
}

async function loadPetWalkDistances() {
    const entries = await Promise.all(state.pets.map(async (pet) => {
        try {
            const distances = await api(`/api/pets/${pet.id}/walk-distance/week`);
            return [pet.id, distances];
        } catch (error) {
            console.warn(error);
            return [pet.id, []];
        }
    }));
    state.petWalkDistancesByPetId = Object.fromEntries(entries);
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

async function loadAdminMetrics() {
    state.adminMetrics = await api("/api/admin/metrics");
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
        el.dashboardStats.innerHTML = "";
        renderWalkers();
        return;
    }

    const isWalker = state.me.role === "WALKER";
    const isAdmin = state.me.role === "ADMIN";
    const roleLabel = isWalker ? "Paseador" : isAdmin ? "Administrador" : "Dueño";
    el.dashboardGreeting.textContent = `Hola, ${state.me.name}`;
    el.dashboardSubtitle.textContent = isWalker
        ? "Revisa solicitudes nuevas, responde a cada familia y comienza el paseo cuando esté confirmado."
        : "Revisa el estado de tus mascotas y encuentra un paseador disponible para tu próximo paseo.";
    const planLabel = state.me.plan ? String(state.me.plan).replace("_", " ") : "BASIC";
    el.dashboardRole.textContent = `${roleLabel} · ${planLabel}`;
    renderDashboardStats(isWalker, isAdmin);
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
        renderAdminMetrics();
        renderAdminWalkers();
    }
    renderAlerts();
    renderSearchGroups();
    renderWalkers();
}

function renderDashboardStats(isWalker, isAdmin) {
    const pendingRequests = state.requests.filter((request) => request.status === "PENDING").length;
    const activeRequests = state.requests.filter((request) => ["ACCEPTED", "IN_PROGRESS"].includes(request.status)).length;
    const activeWalks = state.dogWalks.filter((walk) => walk.status === "IN_PROGRESS").length;
    const stats = isWalker
        ? [
            ["Solicitudes nuevas", pendingRequests, "Por revisar"],
            ["En negociación", state.requests.filter((request) => request.status === "NEGOTIATING").length, "Conversaciones abiertas"],
            ["Paseos activos", activeWalks, "Seguimiento en mapa"]
        ]
        : isAdmin
            ? [
                ["Paseadores visibles", state.walkers.length, "Perfiles disponibles"],
                ["Solicitudes", state.requests.length, "Todas las cuentas"],
                ["Paseos activos", activeWalks, "En seguimiento"]
            ]
            : [
                ["Mis mascotas", state.pets.length, "Registradas en FiruApp"],
                ["Solicitudes", state.requests.length, `${activeRequests} activas`],
                ["Paseos activos", activeWalks, "Seguimiento en mapa"]
            ];

    el.dashboardStats.innerHTML = stats.map(([label, value, detail], index) => `
        <article class="dashboard-stat dashboard-stat-${index + 1}">
            <span class="stat-icon" aria-hidden="true">${["✦", "⌁", "◉"][index]}</span>
            <div><strong>${formatNumber(value)}</strong><span>${label}</span><small>${detail}</small></div>
        </article>
    `).join("");
}

function renderAlerts() {
    if (!el.alertsPanel || !el.alertsList) {
        return;
    }
    if (!state.me || !state.alerts.length) {
        el.alertsPanel.hidden = true;
        el.alertsList.innerHTML = "";
        return;
    }

    el.alertsPanel.hidden = false;
    el.alertsList.innerHTML = state.alerts.map((alert) => `
        <article class="alert-item">
            <div class="alert-item-main">
                <header>
                    <strong>${escapeHtml(alert.petName || "Mascota")}</strong>
                    <span class="tag warn">${escapeHtml(alert.alertType || "ALERTA")}</span>
                </header>
                <p>${escapeHtml(alert.metadata || "Alerta de geocerca")}</p>
                <small>${escapeHtml(formatDateTime(alert.createdAt))}</small>
            </div>
            <button type="button" class="icon-button" data-dismiss-alert="${alert.id}">Ocultar</button>
        </article>
    `).join("");
}

function renderSearchGroups() {
    if (!el.searchGroupsList) {
        return;
    }
    if (!state.me) {
        el.searchGroupsList.innerHTML = `<div class="empty-state">Inicia sesión para ver y apoyar grupos de búsqueda.</div>`;
        return;
    }
    if (!state.searchGroups.length) {
        el.searchGroupsList.innerHTML = `<div class="empty-state">No hay grupos de búsqueda visibles.</div>`;
        return;
    }

    el.searchGroupsList.innerHTML = state.searchGroups.map((group) => `
        <article class="list-item search-group-card">
            <div class="search-banner">
                <span class="search-banner-label">BUSCANDO ESTA MASCOTA</span>
                <strong>${escapeHtml(group.petName || "Mascota")}</strong>
                <span>Foto #${escapeHtml(String(group.petId || "—"))}</span>
            </div>
            <header>
                <div>
                    <strong>${escapeHtml(group.groupName || "Grupo de búsqueda")}</strong>
                    <div class="muted">${escapeHtml(group.petName || "Mascota sin nombre")}</div>
                </div>
                <span class="tag ${String(group.status || "").toLowerCase() === "active" ? "ok" : "warn"}">
                    ${escapeHtml(group.status || "Sin estado")}
                </span>
            </header>
            <span class="tag ${group.visibility === "PUBLIC" ? "ok" : "muted"}">
                ${group.visibility === "PUBLIC" ? "Público · cualquiera puede unirse" : "Privado"}
            </span>
            <p>${escapeHtml(group.description || "Ayuda a coordinar la búsqueda de esta mascota.")}</p>
            <div class="meta-line">
                <span>${escapeHtml([group.area, group.city].filter(Boolean).join(" · ") || "Zona sin definir")}</span>
                <span>${formatNumber(group.membersCount || 0)} voluntario(s)</span>
            </div>
            <button type="button" class="icon-button" data-join-search-group="${group.id}">
                Unirme al grupo
            </button>
        </article>
    `).join("");
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
            ${state.me?.plan === "PREMIUM" || state.me?.role === "ADMIN"
                ? `<div class="pet-report-actions">
                    <button type="button" class="icon-button" data-export-pet-history="${pet.id}" data-export-format="pdf">Descargar PDF</button>
                    <button type="button" class="icon-button" data-export-pet-history="${pet.id}" data-export-format="csv">Descargar CSV</button>
                </div>`
                : `<button type="button" class="icon-button" data-export-pet-history="${pet.id}" data-export-format="pdf">Reporte · solo Premium</button>`}
            ${renderPetWeeklyDistanceTable(pet)}
        </article>
    `).join("");
}

function renderPetWeeklyDistanceTable(pet) {
    const distances = state.petWalkDistancesByPetId[pet.id] || [];
    if (!distances.length) {
        return `<div class="empty-state compact-empty">Sin datos de caminata esta semana.</div>`;
    }

    const totalKm = distances.reduce((total, day) => total + Number(day.distanceKm || 0), 0);
    return `
        <div class="mini-table-wrap">
            <div class="mini-table-title">
                <strong>Km caminados esta semana</strong>
                <span>${formatKm(totalKm)} km</span>
            </div>
            <table class="mini-table">
                <thead>
                    <tr>
                        <th>Día</th>
                        <th>Fecha</th>
                        <th>Km</th>
                    </tr>
                </thead>
                <tbody>
                    ${distances.map((day) => `
                        <tr>
                            <td>${formatWeekday(day.dayOfWeek)}</td>
                            <td>${formatDate(day.date)}</td>
                            <td>${formatKm(day.distanceKm)}</td>
                        </tr>
                    `).join("")}
                </tbody>
            </table>
        </div>
    `;
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
        <article class="list-item request-card ${request.status === "PENDING" ? "request-card-new" : ""}">
            <header>
                <div>
                    <strong>${escapeHtml(request.petName || "Paseo sin mascota asignada")}</strong>
                    <div class="muted">${escapeHtml(state.me.role === "WALKER" ? request.ownerName : request.walkerName)}</div>
                </div>
                <span class="tag ${request.status === "ACCEPTED" || request.status === "IN_PROGRESS" || request.status === "COMPLETED" ? "ok" : "warn"}">${formatRequestStatus(request.status)}</span>
            </header>
            <div class="meta-line">
                <span>${formatDateTime(request.requestedStart)}</span>
                <span>${request.durationMinutes ? request.durationMinutes + " min" : "Duración por definir"}</span>
                <span>Dueño $${formatMoney(request.ownerBudget)}</span>
                <span>Paseador $${formatMoney(request.walkerQuotedPrice)}</span>
            </div>
            ${request.notes ? `<p class="request-note">“${escapeHtml(request.notes)}”</p>` : ""}
            <button data-request-id="${request.id}" class="icon-button">${state.me.role === "WALKER" ? "Revisar solicitud" : "Abrir detalle"}</button>
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

function renderAdminMetrics() {
    if (!state.adminMetrics) {
        el.adminMetrics.innerHTML = `<div class="empty-state">Sin métricas disponibles.</div>`;
        return;
    }

    const { http, database, pool, gps, collectedAt } = state.adminMetrics;
    const cards = [
        ["Requests", formatNumber(http.totalRequests), `${formatNumber(http.inFlightRequests)} activos`],
        ["Errores 5xx", formatNumber(http.serverErrorResponses), `${formatNumber(http.clientErrorResponses)} errores 4xx`],
        ["Transacciones", formatNumber(database.committedTransactions), `${formatNumber(database.rolledBackTransactions)} rollback`],
        ["Conexiones DB", formatNumber(database.openConnections), `${formatNumber(database.idleInTransactionConnections)} idle tx`],
        ["Pool Hikari", `${formatNumber(pool.activeConnections)}/${formatNumber(pool.maxPoolSize)}`, `${formatNumber(pool.threadsAwaitingConnection)} esperando`],
        ["GPS drops", formatNumber(gps.droppedTasks), "executor async"],
        ["GPS queue", gps.messageQueueEnabled ? "Activa" : "Inactiva", `${formatNumber(gps.messageQueueDepth)} pendientes · ${formatNumber(gps.droppedMessages)} drops`]
    ];

    el.adminMetrics.innerHTML = `
        <div class="metrics-head">
            <h4>Salud del sistema</h4>
            <span class="muted">${formatInstant(collectedAt)}</span>
        </div>
        ${cards.map(([label, value, detail]) => `
            <article class="metric-card">
                <span>${label}</span>
                <strong>${value}</strong>
                <small>${detail}</small>
            </article>
        `).join("")}
    `;
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
        <strong>Estado:</strong> ${formatRequestStatus(request.status)}
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
            <label class="field-label">Tu propuesta de tarifa
                <input name="walkerQuotedPrice" type="number" min="0" step="0.01" value="${request.walkerQuotedPrice ?? ""}" placeholder="Tarifa propuesta">
            </label>
            <select name="status">
                <option value="NEGOTIATING" ${request.status === "NEGOTIATING" ? "selected" : ""}>En negociación</option>
                <option value="ACCEPTED" ${request.status === "ACCEPTED" ? "selected" : ""}>Aceptar condiciones</option>
                <option value="REJECTED" ${request.status === "REJECTED" ? "selected" : ""}>Rechazar</option>
            </select>
            <button type="submit">Guardar respuesta</button>
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
    const routeColor = document.body.dataset.theme === "firuapp" ? "#1260d8" : "#166534";
    state.mapLine = L.polyline(latLngs, { color: routeColor, weight: 4 }).addTo(state.map);
    latLngs.forEach((latLng, index) => {
        const marker = L.circleMarker(latLng, {
            radius: index === latLngs.length - 1 ? 7 : 5,
            color: index === latLngs.length - 1 ? "#b91c1c" : routeColor,
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

function formatRequestStatus(status) {
    const labels = {
        PENDING: "Nueva",
        NEGOTIATING: "En negociación",
        ACCEPTED: "Aceptada",
        REJECTED: "Rechazada",
        IN_PROGRESS: "En curso",
        COMPLETED: "Completada",
        CANCELLED: "Cancelada"
    };
    return labels[status] || status || "Sin estado";
}

function formatMoney(value) {
    if (value == null || Number.isNaN(Number(value))) {
        return "0";
    }
    return Number(value).toLocaleString("es-CO", { maximumFractionDigits: 0 });
}

function formatNumber(value) {
    return Number(value || 0).toLocaleString("es-CO");
}

function formatKm(value) {
    return Number(value || 0).toLocaleString("es-CO", {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    });
}

function formatDate(value) {
    if (!value) {
        return "Sin fecha";
    }
    const date = new Date(`${value}T00:00:00`);
    if (Number.isNaN(date.getTime())) {
        return value;
    }
    return date.toLocaleDateString("es-CO", { month: "short", day: "numeric" });
}

function formatWeekday(value) {
    const labels = {
        MONDAY: "Lunes",
        TUESDAY: "Martes",
        WEDNESDAY: "Miércoles",
        THURSDAY: "Jueves",
        FRIDAY: "Viernes",
        SATURDAY: "Sábado",
        SUNDAY: "Domingo"
    };
    return labels[value] || value || "Día";
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
