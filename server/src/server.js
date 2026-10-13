const http = require('http');
const path = require('path');
const express = require('express');
const cors = require('cors');
const { WebSocketServer } = require('ws');
const registry = require('./registry');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, '../public')));

// REST endpoints
app.get('/api/status', (req, res) => {
  res.json({
    status: 'online',
    onlineHosts: registry.getOnlineHostsCount(),
    timestamp: new Date().toISOString()
  });
});

app.get('/api/check-host/:id', (req, res) => {
  const deviceId = req.params.id;
  const online = registry.isHostOnline(deviceId);
  res.json({ deviceId, online });
});

const server = http.createServer(app);
const wss = new WebSocketServer({ server });

wss.on('connection', (ws) => {
  let boundDeviceId = null;
  let isHostRole = false;

  ws.on('message', (rawMessage) => {
    try {
      const message = JSON.parse(rawMessage.toString());
      handleMessage(ws, message);
    } catch (err) {
      console.error('Invalid message format:', err.message);
      safeSend(ws, { type: 'error', message: 'Format pesan tidak valid (JSON required)' });
    }
  });

  function handleMessage(socket, msg) {
    switch (msg.type) {
      case 'register_host': {
        isHostRole = true;
        const registeredId = registry.registerHost(msg.deviceId, msg.pin, socket);
        boundDeviceId = registeredId;
        console.log(`[Host Registered] Device ID: ${registeredId}`);
        safeSend(socket, {
          type: 'host_registered',
          deviceId: registeredId,
          message: 'Berhasil terdaftar sebagai Host'
        });
        break;
      }

      case 'connect_request': {
        const targetId = msg.targetDeviceId;
        const inputPin = msg.pin;
        console.log(`[Connect Request] Controller meminta koneksi ke: ${targetId}`);

        if (!registry.isHostOnline(targetId)) {
          safeSend(socket, {
            type: 'error',
            code: 'HOST_OFFLINE',
            message: `HP Target (${targetId}) sedang offline atau layanan belum aktif.`
          });
          return;
        }

        if (!registry.verifyPin(targetId, inputPin)) {
          safeSend(socket, {
            type: 'error',
            code: 'INVALID_PIN',
            message: 'PIN / Password akses salah. Akses ditolak.'
          });
          return;
        }

        // Simpan asosiasi controller
        registry.registerController(socket, targetId);
        const host = registry.getHost(targetId);

        // Beritahu Controller bahwa otentikasi berhasil
        safeSend(socket, {
          type: 'connect_accepted',
          targetDeviceId: targetId,
          message: 'Autentikasi berhasil. Memulai negosiasi WebRTC...'
        });

        // Beritahu Host bahwa ada koneksi yang terverifikasi (Unattended Auto-Accept)
        safeSend(host.ws, {
          type: 'incoming_connection',
          targetDeviceId: targetId
        });
        break;
      }

      case 'offer': {
        // SDP Offer dikirim oleh Controller ke Host
        const session = registry.getControllerSession(socket);
        const targetId = msg.targetDeviceId || (session && session.targetDeviceId);
        if (targetId && registry.isHostOnline(targetId)) {
          const host = registry.getHost(targetId);
          safeSend(host.ws, {
            type: 'offer',
            sdp: msg.sdp
          });
        }
        break;
      }

      case 'answer': {
        // SDP Answer dikirim oleh Host kembali ke Controller
        const targetId = boundDeviceId;
        // Cari controller yang terhubung ke host ini
        for (const [ctrlWs, data] of registry.controllers.entries()) {
          if (data.targetDeviceId === targetId) {
            safeSend(ctrlWs, {
              type: 'answer',
              sdp: msg.sdp
            });
            break;
          }
        }
        break;
      }

      case 'candidate': {
        // ICE candidate exchange
        if (isHostRole) {
          // Dari Host ke Controller
          for (const [ctrlWs, data] of registry.controllers.entries()) {
            if (data.targetDeviceId === boundDeviceId) {
              safeSend(ctrlWs, {
                type: 'candidate',
                candidate: msg.candidate
              });
              break;
            }
          }
        } else {
          // Dari Controller ke Host
          const session = registry.getControllerSession(socket);
          if (session && registry.isHostOnline(session.targetDeviceId)) {
            const host = registry.getHost(session.targetDeviceId);
            safeSend(host.ws, {
              type: 'candidate',
              candidate: msg.candidate
            });
          }
        }
        break;
      }

      case 'disconnect': {
        handleDisconnect(socket);
        break;
      }

      default:
        console.warn('Unknown message type:', msg.type);
    }
  }

  function handleDisconnect(socket) {
    if (isHostRole && boundDeviceId) {
      console.log(`[Host Disconnected] Device ID: ${boundDeviceId}`);
      registry.unregisterHost(boundDeviceId);
      // Notifikasi ke controller yang sedang meremote
      for (const [ctrlWs, data] of registry.controllers.entries()) {
        if (data.targetDeviceId === boundDeviceId) {
          safeSend(ctrlWs, {
            type: 'peer_disconnected',
            message: 'HP Host telah memutuskan koneksi.'
          });
          registry.unregisterController(ctrlWs);
        }
      }
    } else {
      const session = registry.getControllerSession(socket);
      if (session) {
        console.log(`[Controller Disconnected] dari ${session.targetDeviceId}`);
        if (registry.isHostOnline(session.targetDeviceId)) {
          const host = registry.getHost(session.targetDeviceId);
          safeSend(host.ws, {
            type: 'peer_disconnected',
            message: 'Pengendali telah memutuskan koneksi.'
          });
        }
        registry.unregisterController(socket);
      }
    }
  }

  ws.on('close', () => {
    handleDisconnect(ws);
  });

  ws.on('error', (err) => {
    console.error('WebSocket client error:', err.message);
  });
});

function safeSend(ws, data) {
  if (ws && ws.readyState === 1) { // WebSocket.OPEN
    ws.send(JSON.stringify(data));
  }
}

if (require.main === module) {
  server.listen(PORT, () => {
    console.log(`=======================================================`);
    console.log(` RemoteApps Signaling Server running at:`);
    console.log(` http://localhost:${PORT}`);
    console.log(` WebSocket Signaling URL: ws://localhost:${PORT}`);
    console.log(` PC Web Remote Client:   http://localhost:${PORT}`);
    console.log(`=======================================================`);
  });
}

module.exports = { app, server, registry };
