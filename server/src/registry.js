/**
 * Device and Session Registry
 * Mengelola host terdaftar, autentikasi PIN, dan sesi koneksi aktif.
 */
class DeviceRegistry {
  constructor() {
    // Map deviceId -> { ws, pin, registeredAt, lastSeen }
    this.hosts = new Map();
    // Map controllerWs -> { targetDeviceId }
    this.controllers = new Map();
  }

  generateDeviceId() {
    let id;
    do {
      const p1 = Math.floor(100 + Math.random() * 900);
      const p2 = Math.floor(100 + Math.random() * 900);
      const p3 = Math.floor(100 + Math.random() * 900);
      id = `${p1}-${p2}-${p3}`;
    } while (this.hosts.has(id));
    return id;
  }

  registerHost(deviceId, pin, ws) {
    const formattedId = deviceId || this.generateDeviceId();
    this.hosts.set(formattedId, {
      ws,
      pin: String(pin || '123456'),
      registeredAt: Date.now(),
      lastSeen: Date.now()
    });
    return formattedId;
  }

  unregisterHost(deviceId) {
    if (this.hosts.has(deviceId)) {
      this.hosts.delete(deviceId);
      return true;
    }
    return false;
  }

  getHost(deviceId) {
    return this.hosts.get(deviceId);
  }

  isHostOnline(deviceId) {
    const host = this.hosts.get(deviceId);
    return !!(host && host.ws && host.ws.readyState === 1); // 1 = OPEN
  }

  verifyPin(deviceId, inputPin) {
    const host = this.hosts.get(deviceId);
    if (!host) return false;
    return String(host.pin) === String(inputPin);
  }

  registerController(controllerWs, targetDeviceId) {
    this.controllers.set(controllerWs, { targetDeviceId, connectedAt: Date.now() });
  }

  unregisterController(controllerWs) {
    this.controllers.delete(controllerWs);
  }

  getControllerSession(controllerWs) {
    return this.controllers.get(controllerWs);
  }

  getOnlineHostsCount() {
    return this.hosts.size;
  }
}

module.exports = new DeviceRegistry();
