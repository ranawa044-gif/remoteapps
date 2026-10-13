# RemoteApps - Android & PC Remote Control (Unattended Access)

Aplikasi *remote control* dua arah HP-ke-HP dan PC-ke-HP yang dirancang dengan sistem **Unattended Access** (setelah izin pertama diberikan dan layanan aktif, perangkat dapat diremote kapan saja tanpa perlu konfirmasi manual berulang kali).

---

## 🌟 Fitur Utama

- 🔄 **Remote HP-ke-HP**: Kendalikan layar HP Android lain secara langsung dari HP Android Anda dengan latensi sangat rendah via WebRTC P2P.
- 💻 **Remote PC-ke-HP (Web Client)**: Tidak perlu instal aplikasi apa pun di PC! Cukup buka browser (Google Chrome, Microsoft Edge, Firefox) di PC/laptop Anda, masukkan Device ID & PIN, lalu kendalikan HP menggunakan klik mouse dan keyboard PC.
- 🔐 **Unattended Access (Tanpa Izin Ulang)**:
  - Menggunakan **Accessibility Service** untuk menyuntikkan sentuhan layar (`dispatchGesture`) dan tombol sistem (Back, Home, Recents) tanpa root.
  - Menggunakan **Persistent Foreground Service** yang menjaga sesi rekam layar (`MediaProjection`) dan koneksi WebSocket tetap hidup di latar belakang.
  - Otentikasi otomatis via PIN permanen (*Auto-Accept*).
- ⚡ **WebRTC DataChannel Ultra-Low Latency**: Pengiriman koordinat sentuhan dan gestur swipe diproses seketika (<50ms).

---

## 📁 Struktur Direktori

```text
remoteapps/
├── server/                          # Signaling Server & PC Web Client
│   ├── src/
│   │   ├── server.js                # WebSocket Signaling & REST API
│   │   └── registry.js              # Manajemen Device ID 9-Digit & PIN Auth
│   ├── public/                      # Web Remote Viewer untuk PC / Laptop
│   │   ├── index.html
│   │   ├── app.js
│   │   └── style.css
│   └── test/
│       └── signaling.test.js        # Unit Test Otomatis
│
└── android/                         # Aplikasi Native Android (Kotlin + Jetpack Compose)
    ├── app/src/main/
    │   ├── AndroidManifest.xml
    │   ├── res/xml/accessibility_service_config.xml
    │   └── java/com/remoteapps/
    │       ├── core/
    │       │   ├── webrtc/WebRtcManager.kt
    │       │   ├── signaling/SignalingClient.kt
    │       │   ├── protocol/InputEvents.kt
    │       │   └── security/PreferenceManager.kt
    │       ├── host/
    │       │   ├── service/HostAccessibilityService.kt
    │       │   ├── service/HostForegroundService.kt
    │       │   └── capture/ScreenCaptureManager.kt
    │       ├── controller/
    │       │   └── ui/RemoteViewerScreen.kt
    │       └── ui/
    │           ├── MainActivity.kt
    │           ├── HomeScreen.kt
    │           └── theme/Theme.kt
    └── app/src/test/
        └── java/com/remoteapps/InputEventsTest.kt
```

---

## 🚀 Panduan Menjalankan Sistem

### 1. Menjalankan Signaling Server (PC / Laptop / VPS)

Server ini bertugas menghubungkan HP Host dengan HP Pengendali atau Web Browser PC.

```bash
cd server
npm install
npm start
```

Output terminal:
```text
=======================================================
 RemoteApps Signaling Server running at:
 http://localhost:3000
 WebSocket Signaling URL: ws://localhost:3000
 PC Web Remote Client:   http://localhost:3000
=======================================================
```

*(Untuk pengujian otomatis server, Anda dapat menjalankan `npm test`)*

---

### 2. Membuka & Menjalankan Proyek Android

1. Buka **Android Studio**.
2. Pilih **Open**, lalu arahkan ke folder: `c:\laragon\www\remoteapps\android`.
3. Tunggu hingga Gradle Sync selesai.
4. Hubungkan HP Android fisik atau jalankan Android Emulator (Android 8.0 s/d Android 14+).
5. Klik tombol **Run (Shift + F10)**.

---

### 3. Cara Mengaktifkan Unattended Access di HP Target (Host)

Agar HP target bisa diremote tanpa harus ditekan izin berulang kali:

1. **Buka Aplikasi RemoteApps** di HP target.
2. Di **Tab "Beri Akses (Host)"**:
   - Jika status *"Izin Sentuhan (Aksesibilitas)"* berwarna merah, tekan tombol **"Buka Pengaturan"**. Cari aplikasi **RemoteApps Input Control** dan aktifkan (*ON*).
   - Atur **PIN Akses Tetap** (misal: `123456`).
   - Masukkan alamat IP Signaling Server Anda pada kolom *Signaling Server URL* (misal: `ws://192.168.1.50:3000` jika dalam WiFi yang sama, atau `ws://10.0.2.2:3000` jika menggunakan Android Emulator).
   - Geser tombol **Switch Layanan Remote** ke posisi **ON**.
   - Tekan **"Mulai Sekarang"** pada dialog konfirmasi rekam layar sistem Android.
3. Notifikasi *"Layanan Remote Host Aktif"* akan muncul di status bar HP. **Selesai!** HP target kini siap diremote kapan saja di latar belakang.

---

### 4. Cara Meremote dari PC / Laptop (via Web Browser)

1. Di PC/laptop Anda, buka browser ke: `http://localhost:3000` (atau `http://IP_PC:3000`).
2. Masukkan **Device ID** HP target (tertera di layar HP target, contoh: `888-999-000`).
3. Masukkan **PIN Akses** (contoh: `123456`).
4. Klik **Koneksikan Sekarang**.
5. Layar HP target akan langsung muncul di browser PC Anda:
   - **Klik mouse** = Sentuh layar HP.
   - **Klik & seret (drag)** = Geser (swipe) layar HP.
   - **Scroll mouse** = Gulir layar HP.
   - **Tombol Back, Home, Recents** = Tersedia di bilah navigasi atas browser.

---

### 5. Cara Meremote dari HP Android Lain (HP-ke-HP)

1. Buka aplikasi RemoteApps di HP kedua (HP Pengendali).
2. Buka tab **"Kendalikan (Remote)"**.
3. Masukkan **Device ID** dan **PIN** HP target.
4. Tekan **"Hubungkan Sekarang"**.
5. Layar HP target akan muncul secara *fullscreen* di HP Anda lengkap dengan kontrol sentuhan dan bilah tombol navigasi.
