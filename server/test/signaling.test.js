const assert = require('assert');
const http = require('http');
const { WebSocket } = require('ws');
const { app, server } = require('../src/server');

const TEST_PORT = 3999;

function runTests() {
  server.listen(TEST_PORT, async () => {
    console.log(`[Test] Server running on port ${TEST_PORT}`);
    try {
      // 1. Test HTTP /api/status
      await testHttpStatus();

      // 2. Test Host Registration & Unattended Connect Flow
      await testHostRegistrationAndPairing();

      console.log('✅ ALL SIGNALING TESTS PASSED!');
      server.close();
      process.exit(0);
    } catch (err) {
      console.error('❌ Test failed:', err);
      server.close();
      process.exit(1);
    }
  });
}

function testHttpStatus() {
  return new Promise((resolve, reject) => {
    http.get(`http://localhost:${TEST_PORT}/api/status`, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          const json = JSON.parse(data);
          assert.strictEqual(json.status, 'online');
          console.log('✔ HTTP /api/status passed');
          resolve();
        } catch (e) {
          reject(e);
        }
      });
    }).on('error', reject);
  });
}

function testHostRegistrationAndPairing() {
  return new Promise((resolve, reject) => {
    const wsUrl = `ws://localhost:${TEST_PORT}`;
    const hostWs = new WebSocket(wsUrl);
    let hostDeviceId = '888-999-000';
    const hostPin = '123456';

    hostWs.on('open', () => {
      // Register host
      hostWs.send(JSON.stringify({
        type: 'register_host',
        deviceId: hostDeviceId,
        pin: hostPin
      }));
    });

    hostWs.on('message', (data) => {
      const msg = JSON.parse(data.toString());

      if (msg.type === 'host_registered') {
        assert.strictEqual(msg.deviceId, hostDeviceId);
        console.log('✔ Host registered successfully with ID:', msg.deviceId);

        // Now test controller wrong PIN
        testWrongPin(hostDeviceId, hostPin, resolve, reject, hostWs);
      }
    });

    hostWs.on('error', reject);
  });
}

function testWrongPin(deviceId, correctPin, resolve, reject, hostWs) {
  const ctrlWs = new WebSocket(`ws://localhost:${TEST_PORT}`);
  ctrlWs.on('open', () => {
    ctrlWs.send(JSON.stringify({
      type: 'connect_request',
      targetDeviceId: deviceId,
      pin: '999999' // Wrong PIN
    }));
  });

  ctrlWs.on('message', (data) => {
    const msg = JSON.parse(data.toString());
    if (msg.type === 'error' && msg.code === 'INVALID_PIN') {
      console.log('✔ Wrong PIN correctly rejected');
      ctrlWs.close();
      testCorrectPin(deviceId, correctPin, resolve, reject, hostWs);
    }
  });

  ctrlWs.on('error', reject);
}

function testCorrectPin(deviceId, correctPin, resolve, reject, hostWs) {
  const ctrlWs = new WebSocket(`ws://localhost:${TEST_PORT}`);
  let hostReceivedIncoming = false;
  let ctrlReceivedAccepted = false;

  hostWs.on('message', (data) => {
    const msg = JSON.parse(data.toString());
    if (msg.type === 'incoming_connection') {
      hostReceivedIncoming = true;
      checkDone();
    }
  });

  ctrlWs.on('open', () => {
    ctrlWs.send(JSON.stringify({
      type: 'connect_request',
      targetDeviceId: deviceId,
      pin: correctPin // Correct PIN
    }));
  });

  ctrlWs.on('message', (data) => {
    const msg = JSON.parse(data.toString());
    if (msg.type === 'connect_accepted') {
      ctrlReceivedAccepted = true;
      checkDone();
    }
  });

  function checkDone() {
    if (hostReceivedIncoming && ctrlReceivedAccepted) {
      console.log('✔ Unattended pairing with correct PIN auto-accepted!');
      ctrlWs.close();
      hostWs.close();
      resolve();
    }
  }

  ctrlWs.on('error', reject);
}

runTests();
