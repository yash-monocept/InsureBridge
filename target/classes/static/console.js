(() => {
  "use strict";

  const state = {
    partnerPage: 0,
    insurerPage: 0,
    partnerPages: 0,
    insurerPages: 0,
    mappingPartner: null,
    accessToken: "",
    refreshToken: "",
    partnerSearchTimer: null
  };

  const $ = (id) => document.getElementById(id);
  const esc = encodeURIComponent;

  async function request(path, options = {}) {
    const headers = new Headers(options.headers || {});
    if (options.body && !(options.body instanceof URLSearchParams) && !headers.has("Content-Type")) {
      headers.set("Content-Type", "application/json");
    }
    if (options.auth && state.accessToken) {
      headers.set("Authorization", `Bearer ${state.accessToken}`);
    }
    const response = await fetch(path, { ...options, headers });
    const raw = await response.text();
    let body;
    try { body = raw ? JSON.parse(raw) : null; } catch { body = raw; }
    if (!response.ok) {
      const error = new Error(
        (body && (body.message || body.error_description || body.error)) ||
        `Request failed with HTTP ${response.status}`
      );
      error.status = response.status;
      error.body = body;
      throw error;
    }
    return body;
  }

  function showToast(message, kind = "success") {
    const toast = document.createElement("div");
    toast.className = `toast ${kind}`;
    toast.textContent = message;
    $("toast-region").append(toast);
    window.setTimeout(() => toast.remove(), 4800);
  }

  function showError(error, prefix = "") {
    const message = error && error.message ? error.message : "The request failed.";
    showToast(`${prefix}${message}`, "error");
  }

  function showResult(element, data) {
    element.textContent = JSON.stringify(data, null, 2);
    element.classList.remove("hidden");
  }

  function hideResult(element) {
    element.textContent = "";
    element.classList.add("hidden");
  }

  function node(tag, className, text) {
    const element = document.createElement(tag);
    if (className) element.className = className;
    if (text !== undefined && text !== null) element.textContent = text;
    return element;
  }

  function appendCell(row, content, className) {
    const cell = document.createElement("td");
    if (content instanceof Node) cell.append(content);
    else cell.textContent = content ?? "—";
    if (className) cell.className = className;
    row.append(cell);
    return cell;
  }

  function addAction(container, label, action, id, danger = false) {
    const button = node("button", `row-action${danger ? " danger" : ""}`, label);
    button.type = "button";
    button.dataset.action = action;
    button.dataset.id = id;
    container.append(button);
  }

  function statusBadge(active) {
    return node("span", `status-pill ${active ? "status-active" : "status-inactive"}`, active ? "Active" : "Inactive");
  }

  function setPage(name) {
    document.querySelectorAll(".page").forEach(page => page.classList.toggle("active", page.id === `page-${name}`));
    document.querySelectorAll(".nav-link").forEach(link => link.classList.toggle("active", link.dataset.page === name));
    const activeLink = document.querySelector(`.nav-link[data-page="${name}"]`);
    $("breadcrumb-current").textContent = activeLink ? activeLink.textContent.trim() : name;
    if (name === "overview") loadOverview();
    if (name === "partners") loadPartners();
    if (name === "insurers") loadInsurers();
    if (name === "clients") loadPartnerOptions();
  }

  async function loadHealth() {
    try {
      const response = await request("/api/v1/health");
      const up = response?.data?.status === "UP";
      $("health-dot").className = `status-dot ${up ? "up" : "down"}`;
      $("health-label").textContent = up ? "API online" : "API issue";
      $("overview-health").textContent = up ? "Online" : "Unavailable";
      $("overview-health-caption").textContent = response?.data?.service || "Health check responded";
    } catch (error) {
      $("health-dot").className = "status-dot down";
      $("health-label").textContent = "API offline";
      $("overview-health").textContent = "Offline";
      $("overview-health-caption").textContent = error.message;
    }
  }

  async function loadOverview() {
    loadHealth();
    try {
      const [partners, insurers] = await Promise.all([
        request("/api/v1/admin/partners?page=0&size=1"),
        request("/api/v1/admin/insurers?page=0&size=1")
      ]);
      $("overview-partners").textContent = partners.totalRecords ?? "—";
      $("overview-insurers").textContent = insurers.totalRecords ?? "—";
    } catch (error) {
      $("overview-partners").textContent = "—";
      $("overview-insurers").textContent = "—";
      showError(error, "Could not load overview: ");
    }
    $("overview-clients").textContent = "—";
    $("overview-clients").title = "Choose a partner on the API Clients page to see its clients.";
  }

  function partnerRow(partner) {
    const row = document.createElement("tr");
    const identity = node("div");
    identity.append(node("div", "table-primary", partner.partnerName));
    identity.append(node("div", "table-secondary", partner.partnerCode));
    appendCell(row, identity);
    appendCell(row, partner.partnerType);
    appendCell(row, partner.contactEmail);
    appendCell(row, statusBadge(partner.active));
    appendCell(row, `${partner.maxDocumentSizeMb ?? "—"} MB`);
    const actions = node("div", "row-actions");
    addAction(actions, "Edit", "edit-partner", partner.id);
    addAction(actions, "Mappings", "mappings", partner.id);
    addAction(actions, partner.active ? "Deactivate" : "Activate", partner.active ? "deactivate-partner" : "activate-partner", partner.id, partner.active);
    appendCell(row, actions);
    return row;
  }

  async function loadPartners() {
    const table = $("partners-table");
    table.replaceChildren();
    const query = new URLSearchParams({ page: String(state.partnerPage), size: "10", sortBy: "createdAt", sortDir: "DESC" });
    const search = $("partner-search").value.trim();
    const type = $("partner-type-filter").value;
    const active = $("partner-active-filter").value;
    if (search) query.set("search", search);
    if (type) query.set("partnerType", type);
    if (active) query.set("active", active);
    try {
      const result = await request(`/api/v1/admin/partners?${query}`);
      (result.records || []).forEach(partner => table.append(partnerRow(partner)));
      if (!result.records?.length) table.append(emptyRow(6, "No partners match these filters."));
      state.partnerPages = result.totalPages || 0;
      $("partners-count").textContent = `${result.totalRecords ?? 0} partner${result.totalRecords === 1 ? "" : "s"}`;
      $("partners-page").textContent = `Page ${state.partnerPage + 1}${state.partnerPages ? ` of ${state.partnerPages}` : ""}`;
      $("partners-prev").disabled = state.partnerPage <= 0;
      $("partners-next").disabled = state.partnerPages === 0 || state.partnerPage >= state.partnerPages - 1;
    } catch (error) {
      table.append(emptyRow(6, error.message));
      $("partners-count").textContent = "Could not load partners";
      showError(error);
    }
  }

  function insurerRow(insurer) {
    const row = document.createElement("tr");
    const identity = node("div");
    identity.append(node("div", "table-primary", insurer.insurerName));
    identity.append(node("div", "table-secondary", insurer.insurerCode));
    appendCell(row, identity);
    appendCell(row, insurer.insurerType);
    appendCell(row, insurer.contactEmail);
    appendCell(row, statusBadge(insurer.active));
    appendCell(row, insurer.destinationConfigured ? "Configured" : "Not configured");
    const actions = node("div", "row-actions");
    addAction(actions, "Details", "insurer-details", insurer.id);
    addAction(actions, insurer.active ? "Deactivate" : "Activate", insurer.active ? "deactivate-insurer" : "activate-insurer", insurer.id, insurer.active);
    appendCell(row, actions);
    return row;
  }

  async function loadInsurers() {
    const table = $("insurers-table");
    table.replaceChildren();
    const query = new URLSearchParams({ page: String(state.insurerPage), size: "10", sortBy: "createdAt", sortDir: "DESC" });
    const type = $("insurer-type-filter").value;
    const active = $("insurer-active-filter").value;
    if (type) query.set("insurerType", type);
    if (active) query.set("active", active);
    try {
      const result = await request(`/api/v1/admin/insurers?${query}`);
      (result.records || []).forEach(insurer => table.append(insurerRow(insurer)));
      if (!result.records?.length) table.append(emptyRow(6, "No insurers match these filters."));
      state.insurerPages = result.totalPages || 0;
      $("insurers-count").textContent = `${result.totalRecords ?? 0} insurer${result.totalRecords === 1 ? "" : "s"}`;
      $("insurers-page").textContent = `Page ${state.insurerPage + 1}${state.insurerPages ? ` of ${state.insurerPages}` : ""}`;
      $("insurers-prev").disabled = state.insurerPage <= 0;
      $("insurers-next").disabled = state.insurerPages === 0 || state.insurerPage >= state.insurerPages - 1;
    } catch (error) {
      table.append(emptyRow(6, error.message));
      $("insurers-count").textContent = "Could not load insurers";
      showError(error);
    }
  }

  function emptyRow(span, message) {
    const row = document.createElement("tr");
    appendCell(row, message, "empty-state");
    row.firstElementChild.colSpan = span;
    return row;
  }

  async function loadPartnerOptions(selected = "") {
    try {
      const result = await request("/api/v1/admin/partners?page=0&size=100&sortBy=partnerName&sortDir=ASC");
      const select = $("client-partner-select");
      const previous = selected || select.value;
      select.replaceChildren(new Option("Select a partner", ""));
      (result.records || []).forEach(partner => {
        const option = new Option(`${partner.partnerName} · ${partner.partnerCode}`, partner.id);
        select.append(option);
      });
      if (previous) select.value = previous;
    } catch (error) {
      showError(error, "Could not load partner list: ");
    }
  }

  async function lookupResource(resource) {
    const kind = $(`${resource}-lookup-kind`).value;
    const value = $(`${resource}-lookup-value`).value.trim();
    const output = $(`${resource}-lookup-result`);
    if (!value) {
      showToast(`Enter a ${kind === "id" ? "UUID" : "business code"} first.`, "error");
      return;
    }
    const base = resource === "partner" ? "/api/v1/admin/partners" : "/api/v1/admin/insurers";
    try {
      const result = await request(`${base}/${kind === "code" ? `code/${esc(value)}` : esc(value)}`);
      showResult(output, result);
      if (resource === "partner") await loadPartnerOptions(result.data?.id);
      showToast(`${resource === "partner" ? "Partner" : "Insurer"} found.`);
    } catch (error) {
      showResult(output, error.body || { message: error.message, status: error.status });
      showError(error);
    }
  }

  function openPartnerForm(partner = null) {
    const editing = Boolean(partner);
    $("partner-form").reset();
    $("partner-form-error").classList.add("hidden");
    $("partner-dialog-title").textContent = editing ? "Update partner" : "Register a partner";
    $("partner-dialog-eyebrow").textContent = editing ? "EDIT PARTNER" : "NEW PARTNER";
    $("save-partner").textContent = editing ? "Save changes" : "Create partner";
    $("partner-code").disabled = editing;
    $("partner-type").disabled = editing;
    $("partner-max-size").value = partner?.maxDocumentSizeMb ?? 50;
    $("partner-code").value = partner?.partnerCode ?? "";
    $("partner-name").value = partner?.partnerName ?? "";
    $("partner-type").value = partner?.partnerType ?? "";
    $("partner-email").value = partner?.contactEmail ?? "";
    $("partner-phone").value = partner?.contactPhone ?? "";
    $("partner-website").value = partner?.websiteUrl ?? "";
    $("partner-description").value = partner?.description ?? "";
    $("partner-form").dataset.partnerId = partner?.id ?? "";
    $("partner-dialog").showModal();
  }

  async function savePartner(event) {
    event.preventDefault();
    const form = $("partner-form");
    if (!form.reportValidity()) return;
    const id = form.dataset.partnerId;
    const payload = id ? {
      partnerName: $("partner-name").value.trim(),
      contactEmail: $("partner-email").value.trim(),
      contactPhone: $("partner-phone").value.trim() || null,
      websiteUrl: $("partner-website").value.trim() || null,
      description: $("partner-description").value.trim() || null,
      maxDocumentSizeMb: Number($("partner-max-size").value)
    } : {
      partnerCode: $("partner-code").value.trim(),
      partnerName: $("partner-name").value.trim(),
      partnerType: $("partner-type").value,
      contactEmail: $("partner-email").value.trim(),
      contactPhone: $("partner-phone").value.trim() || null,
      websiteUrl: $("partner-website").value.trim() || null,
      description: $("partner-description").value.trim() || null,
      maxDocumentSizeMb: Number($("partner-max-size").value)
    };
    try {
      await request(id ? `/api/v1/admin/partners/${esc(id)}` : "/api/v1/admin/partners", {
        method: id ? "PUT" : "POST", body: JSON.stringify(payload)
      });
      $("partner-dialog").close();
      showToast(id ? "Partner updated." : "Partner created.");
      await Promise.all([loadPartners(), loadPartnerOptions(id)]);
    } catch (error) {
      $("partner-form-error").textContent = error.message;
      $("partner-form-error").classList.remove("hidden");
    }
  }

  async function openMappings(partnerId) {
    state.mappingPartner = partnerId;
    const panel = $("partner-mappings-panel");
    panel.classList.remove("hidden");
    $("mapping-partner-title").textContent = "Partner insurer mappings";
    $("mapping-partner-subtitle").textContent = `Partner ${partnerId}`;
    $("mapping-insurer-code").value = "";
    $("mapping-products").value = "";
    panel.scrollIntoView({ behavior: "smooth", block: "start" });
    await loadMappings();
  }

  async function loadMappings() {
    const table = $("mappings-table");
    table.replaceChildren();
    if (!state.mappingPartner) {
      table.append(emptyRow(4, "Select a partner to view mappings."));
      return;
    }
    try {
      const result = await request(`/api/v1/admin/partners/${esc(state.mappingPartner)}/mappings`);
      const mappings = result.data || [];
      mappings.forEach(mapping => {
        const row = document.createElement("tr");
        appendCell(row, mapping.insurerCode);
        appendCell(row, mapping.permittedProductTypes || "All product types");
        appendCell(row, statusBadge(mapping.active));
        const actions = node("div", "row-actions");
        if (mapping.active) addAction(actions, "Deactivate", "deactivate-mapping", mapping.id, true);
        appendCell(row, actions);
        table.append(row);
      });
      if (!mappings.length) table.append(emptyRow(4, "No insurer mappings yet."));
    } catch (error) {
      table.append(emptyRow(4, error.message));
      showError(error);
    }
  }

  async function createMapping() {
    if (!state.mappingPartner) return;
    const insurerCode = $("mapping-insurer-code").value.trim();
    if (!insurerCode) {
      showToast("Enter an insurer code.", "error");
      return;
    }
    const permitted = $("mapping-products").value.trim();
    const payload = { insurerCode };
    if (permitted) payload.permittedProductTypes = permitted;
    try {
      await request(`/api/v1/admin/partners/${esc(state.mappingPartner)}/mappings`, {
        method: "POST", body: JSON.stringify(payload)
      });
      $("mapping-insurer-code").value = "";
      $("mapping-products").value = "";
      showToast("Insurer mapping created.");
      loadMappings();
    } catch (error) {
      showError(error);
    }
  }

  async function createInsurer(event) {
    event.preventDefault();
    const form = $("insurer-form");
    if (!form.reportValidity()) return;
    const payload = {
      insurerCode: $("insurer-code").value.trim(),
      insurerName: $("insurer-name").value.trim(),
      insurerType: $("insurer-type").value,
      contactEmail: $("insurer-email").value.trim() || null
    };
    try {
      await request("/api/v1/admin/insurers", { method: "POST", body: JSON.stringify(payload) });
      $("insurer-dialog").close();
      showToast("Insurer created.");
      await loadInsurers();
    } catch (error) {
      $("insurer-form-error").textContent = error.message;
      $("insurer-form-error").classList.remove("hidden");
    }
  }

  async function loadClients() {
    const partnerId = $("client-partner-select").value;
    const table = $("clients-table");
    table.replaceChildren();
    $("overview-clients").textContent = "—";
    if (!partnerId) {
      table.append(emptyRow(6, "Select a partner to load clients."));
      return;
    }
    try {
      const result = await request(`/api/v1/admin/partners/${esc(partnerId)}/api-clients`);
      const clients = result.data || [];
      $("overview-clients").textContent = String(clients.length);
      clients.forEach(client => {
        const row = document.createElement("tr");
        appendCell(row, node("code", "table-primary", client.clientId));
        appendCell(row, (client.scopes || []).join(", "));
        appendCell(row, `${client.rateLimitPerMinute} / min`);
        appendCell(row, statusBadge(client.active));
        appendCell(row, client.createdAt ? new Date(client.createdAt).toLocaleDateString() : "—");
        const actions = node("div", "row-actions");
        addAction(actions, "Rotate secret", "rotate-client", client.clientId);
        addAction(actions, client.active ? "Deactivate" : "Activate", client.active ? "deactivate-client" : "activate-client", client.clientId, client.active);
        appendCell(row, actions);
        table.append(row);
      });
      if (!clients.length) table.append(emptyRow(6, "No API clients for this partner yet."));
    } catch (error) {
      table.append(emptyRow(6, error.message));
      showError(error);
    }
  }

  function showSecret(title, clientId, secret, validUntil = "") {
    const banner = $("client-secret-banner");
    banner.replaceChildren();
    banner.classList.remove("hidden");
    banner.append(node("strong", "", title));
    const wrapper = node("div", "secret-value");
    wrapper.append(node("code", "", `${clientId} · ${secret}`));
    const copy = node("button", "button button-secondary", "Copy");
    copy.type = "button";
    copy.addEventListener("click", async () => {
      try {
        await navigator.clipboard.writeText(secret);
        showToast("Secret copied. Store it securely; it will not be shown again.");
      } catch {
        showToast("Clipboard access is unavailable. Select and copy the secret above.", "error");
      }
    });
    wrapper.append(copy);
    banner.append(wrapper);
    if (validUntil) banner.append(node("div", "table-secondary", `Previous secret remains valid until ${new Date(validUntil).toLocaleString()}.`));
  }

  async function createClient() {
    const partnerId = $("client-partner-select").value;
    if (!partnerId) {
      showToast("Select a partner first.", "error");
      return;
    }
    const scopes = [...document.querySelectorAll('input[name="scope"]:checked')].map(input => input.value);
    if (!scopes.length) {
      showToast("Select at least one scope for the API client.", "error");
      return;
    }
    const payload = {
      scopes,
      tokenExpirySeconds: Number($("client-expiry").value),
      rateLimitPerMinute: Number($("client-rate-limit").value)
    };
    try {
      const result = await request(`/api/v1/admin/partners/${esc(partnerId)}/api-clients`, {
        method: "POST", body: JSON.stringify(payload)
      });
      const client = result.data;
      showSecret("Client secret — copy it now; it is only shown once.", client.clientId, client.client_secret);
      $("auth-client-id").value = client.clientId || "";
      $("auth-client-secret").value = client.client_secret || "";
      showToast("API credentials created.");
      loadClients();
    } catch (error) {
      showError(error);
    }
  }

  async function updateClient(clientId, operation) {
    const partnerId = $("client-partner-select").value;
    try {
      const result = await request(`/api/v1/admin/partners/${esc(partnerId)}/api-clients/${esc(clientId)}/${operation}`, { method: "PUT" });
      if (operation === "rotate") {
        const client = result.data;
        showSecret("Rotated secret — copy it now.", client.clientId, client.client_secret, client.previousSecretValidUntil);
        $("auth-client-id").value = client.clientId || "";
        $("auth-client-secret").value = client.client_secret || "";
      } else {
        showToast(`API client ${operation === "activate" ? "activated" : "deactivated"}.`);
      }
      await loadClients();
    } catch (error) {
      showError(error);
    }
  }

  function setToken(data) {
    state.accessToken = data.access_token || "";
    state.refreshToken = data.refresh_token || "";
    $("access-token-output").value = state.accessToken;
    $("token-expiry-label").textContent = data.expires_in ? `Expires in ${data.expires_in} seconds` : "";
    $("token-scopes").textContent = `Scopes: ${(data.scopes || []).join(", ") || "none"}`;
    $("token-result").classList.remove("hidden");
  }

  async function requestToken() {
    const clientId = $("auth-client-id").value.trim();
    const clientSecret = $("auth-client-secret").value;
    if (!clientId || !clientSecret) {
      showToast("Enter both client ID and client secret.", "error");
      return;
    }
    const format = $("auth-format").value;
    try {
      const options = format === "form"
        ? {
            method: "POST",
            body: new URLSearchParams({ grant_type: "client_credentials", client_id: clientId, client_secret: clientSecret })
          }
        : {
            method: "POST",
            body: JSON.stringify({ grant_type: "client_credentials", client_id: clientId, client_secret: clientSecret })
          };
      const result = await request("/api/v1/auth/token", options);
      setToken(result);
      hideResult($("auth-result"));
      showToast("Access and refresh tokens issued.");
    } catch (error) {
      showError(error);
    }
  }

  async function verifyToken() {
    if (!state.accessToken) {
      showToast("Request an access token first.", "error");
      return;
    }
    try {
      const result = await request("/api/v1/auth/verify", { auth: true });
      showResult($("auth-result"), result);
      showToast("Access token is valid.");
    } catch (error) {
      showResult($("auth-result"), error.body || { message: error.message, status: error.status });
      showError(error);
    }
  }

  async function loadPartnerProfile() {
    if (!state.accessToken) {
      showToast("Request an access token first.", "error");
      return;
    }
    try {
      const result = await request("/api/v1/partner/me", { auth: true });
      showResult($("auth-result"), result);
      showToast("Protected partner profile loaded.");
    } catch (error) {
      showResult($("auth-result"), error.body || { message: error.message, status: error.status });
      showError(error);
    }
  }

  async function refreshTokens() {
    if (!state.refreshToken) {
      showToast("Request a token first to get a refresh token.", "error");
      return;
    }
    try {
      const result = await request("/api/v1/auth/refresh", {
        method: "POST", body: JSON.stringify({ refresh_token: state.refreshToken })
      });
      setToken(result);
      showToast("Token pair refreshed.");
    } catch (error) {
      showResult($("auth-result"), error.body || { message: error.message, status: error.status });
      showError(error);
    }
  }

  function clearAuth() {
    state.accessToken = "";
    state.refreshToken = "";
    $("auth-client-id").value = "";
    $("auth-client-secret").value = "";
    $("access-token-output").value = "";
    $("token-scopes").textContent = "";
    $("token-expiry-label").textContent = "";
    $("token-result").classList.add("hidden");
    hideResult($("auth-result"));
    showToast("Authentication session cleared.");
  }

  async function handleTableAction(event) {
    const button = event.target.closest("button[data-action]");
    if (!button) return;
    const { action, id } = button.dataset;
    try {
      if (action === "edit-partner") {
        const partner = await request(`/api/v1/admin/partners/${esc(id)}`);
        openPartnerForm(partner.data);
      } else if (action === "mappings") {
        await openMappings(id);
      } else if (action === "activate-partner" || action === "deactivate-partner") {
        await request(`/api/v1/admin/partners/${esc(id)}/${action.replace("-partner", "")}`, { method: "PUT" });
        showToast(`Partner ${action.startsWith("activate") ? "activated" : "deactivated"}.`);
        await Promise.all([loadPartners(), loadPartnerOptions()]);
      } else if (action === "insurer-details") {
        const result = await request(`/api/v1/admin/insurers/${esc(id)}`);
        showResult($("insurer-lookup-result"), result);
        $("insurer-lookup-result").scrollIntoView({ behavior: "smooth", block: "center" });
      } else if (action === "activate-insurer" || action === "deactivate-insurer") {
        await request(`/api/v1/admin/insurers/${esc(id)}/${action.replace("-insurer", "")}`, { method: "PUT" });
        showToast(`Insurer ${action.startsWith("activate") ? "activated" : "deactivated"}.`);
        loadInsurers();
      } else if (action === "deactivate-mapping") {
        await request(`/api/v1/admin/partners/mappings/${esc(id)}`, { method: "DELETE" });
        showToast("Partner-insurer mapping deactivated.");
        loadMappings();
      } else if (action.endsWith("-client") || action === "rotate-client") {
        await updateClient(id, action.replace("-client", ""));
      }
    } catch (error) {
      showError(error);
    }
  }

  function wireEvents() {
    document.querySelectorAll(".nav-link").forEach(link => link.addEventListener("click", () => setPage(link.dataset.page)));
    document.querySelectorAll("[data-navigate]").forEach(button => button.addEventListener("click", () => setPage(button.dataset.navigate)));
    $("refresh-health").addEventListener("click", loadHealth);
    $("open-create-partner").addEventListener("click", () => openPartnerForm());
    $("partner-form").addEventListener("submit", savePartner);
    $("open-create-insurer").addEventListener("click", () => {
      $("insurer-form").reset();
      $("insurer-form-error").classList.add("hidden");
      $("insurer-dialog").showModal();
    });
    $("insurer-form").addEventListener("submit", createInsurer);
    $("load-partners").addEventListener("click", () => { state.partnerPage = 0; loadPartners(); });
    $("partner-search").addEventListener("input", () => {
      window.clearTimeout(state.partnerSearchTimer);
      state.partnerSearchTimer = window.setTimeout(() => { state.partnerPage = 0; loadPartners(); }, 280);
    });
    $("partner-type-filter").addEventListener("change", () => { state.partnerPage = 0; loadPartners(); });
    $("partner-active-filter").addEventListener("change", () => { state.partnerPage = 0; loadPartners(); });
    $("partners-prev").addEventListener("click", () => { state.partnerPage = Math.max(0, state.partnerPage - 1); loadPartners(); });
    $("partners-next").addEventListener("click", () => { state.partnerPage += 1; loadPartners(); });
    $("load-insurers").addEventListener("click", () => { state.insurerPage = 0; loadInsurers(); });
    $("insurer-type-filter").addEventListener("change", () => { state.insurerPage = 0; loadInsurers(); });
    $("insurer-active-filter").addEventListener("change", () => { state.insurerPage = 0; loadInsurers(); });
    $("insurers-prev").addEventListener("click", () => { state.insurerPage = Math.max(0, state.insurerPage - 1); loadInsurers(); });
    $("insurers-next").addEventListener("click", () => { state.insurerPage += 1; loadInsurers(); });
    $("partner-lookup").addEventListener("click", () => lookupResource("partner"));
    $("insurer-lookup").addEventListener("click", () => lookupResource("insurer"));
    $("partners-table").addEventListener("click", handleTableAction);
    $("insurers-table").addEventListener("click", handleTableAction);
    $("clients-table").addEventListener("click", handleTableAction);
    $("mappings-table").addEventListener("click", handleTableAction);
    $("create-mapping").addEventListener("click", createMapping);
    $("close-mappings").addEventListener("click", () => $("partner-mappings-panel").classList.add("hidden"));
    $("client-partner-select").addEventListener("change", loadClients);
    $("load-clients").addEventListener("click", loadClients);
    $("create-client").addEventListener("click", createClient);
    $("request-token").addEventListener("click", requestToken);
    $("verify-token").addEventListener("click", verifyToken);
    $("load-profile").addEventListener("click", loadPartnerProfile);
    $("refresh-token").addEventListener("click", refreshTokens);
    $("clear-auth").addEventListener("click", clearAuth);
    $("copy-token").addEventListener("click", async () => {
      try {
        await navigator.clipboard.writeText(state.accessToken);
        showToast("Access token copied.");
      } catch {
        showToast("Clipboard access unavailable. Select and copy the token.", "error");
      }
    });
  }

  wireEvents();
  loadOverview();
})();
