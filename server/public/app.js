/**
 * PC Web Remote Controller - WebRTC & Input Client
 */

// DOM Elements
const statusBadge = document.getElementById('statusBadge');
const statusText = document.getElementById('statusText');
const connectPanel = document.getElementById('connectPanel');
const viewerPanel = document.getElementById('viewerPanel');
const connectForm = document.getElementById('connectForm');
const deviceIdInput = document.getElementById('deviceIdInput');
const pinInput = document.getElementById('pinInput');
const btnConnect = document.getElementById('btnConnect');
const errorMessage = document.getElementById('errorMessage');
const connectedDeviceId = document.getElementById('connectedDeviceId');
const remoteVideo = document.getElementById('remoteVideo');
const touchIndicator = document.getElementById('touchIndicator');
const btnBack = document.getElementById('btnBack');
const btnHome = document.getElementById('btnHome');
const btnRecents = document.getElementById('btnRecents');
const btnDisconnect = document.getElementById('btnDisconnect');

// State
let ws = null;
let peerConnection = null;
let dataChannel = null;
let isMouseDown = false;
let currentTargetId = '';
let lastSentMoveTime = 0;

const rtcConfig = {
  iceServers: [
    { urls: 'stun:stun.l.google.com:19302' },
    { urls: 'stun:stun1.l.google.com:19302' }
  ]
};

// Formatting ID input: XXX-XXX-XXX
deviceIdInput.addEventListener('input', (e) => {
  let val = e.target.value.replace(/[^0-9]/g, '');
  if (val.length > 9) val = val.substring(0, 9);
  if (val.length > 6) {
    e.target.value = `${val.slice(0, 3)}-${val.slice(3, 6)}-${val.slice(6)}`;
  } else if (val.length > 3) {
    e.target.value = `${val.slice(0, 3)}-${val.slice(3)}`;
  } else {
    e.target.value = val;
  }
});

function setStatus(state, message) {
  statusBadge.className = `status-badge ${state}`;
  statusText.textContent = message;
}

function showError(msg) {
  errorMessage.textContent = msg;
  errorMessage.style.display = 'block';
}

function hideError() {
  errorMessage.style.display = 'none';
}

// Connect Form Submit
connectForm.addEventListener('submit', (e) => {
  e.preventDefault();
  hideError();

  const targetId = deviceIdInput.value.trim();
  const pin = pinInput.value.trim();

  if (!targetId || !pin) {
    showError('Mohon isi Device ID dan PIN.');
    return;
  }

  currentTargetId = targetId;
  initConnection(targetId, pin);
});

function initConnection(targetId, pin) {
  setStatus('connecting', 'Menghubungkan ke Signaling...');
  btnConnect.disabled = true;

  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const wsUrl = `${protocol}//${window.location.host}`;

  ws = new WebSocket(wsUrl);

  ws.onopen = () => {
    setStatus('connecting', 'Memverifikasi Unattended Access...');
    ws.send(JSON.stringify({
      type: 'connect_request',
      targetDeviceId: targetId,
      pin: pin
    }));
  };

  ws.onmessage = async (event) => {
    try {
      const msg = JSON.parse(event.data);
      handleSignalingMessage(msg);
    } catch (err) {
      console.error('Signaling parse error:', err);
    }
  };

  ws.onerror = (err) => {
    console.error('WebSocket error:', err);
    showError('Gagal terhubung ke Signaling Server. Pastikan server aktif.');
    cleanup();
  };

  ws.onclose = () => {
    if (statusBadge.classList.contains('connected')) {
      showError('Koneksi ke server terputus.');
    }
    cleanup();
  };
}

async function handleSignalingMessage(msg) {
  switch (msg.type) {
    case 'connect_accepted': {
      setStatus('connecting', 'Membuat P2P WebRTC...');
      await setupWebRTC();
      break;
    }

    case 'answer': {
      if (peerConnection) {
        await peerConnection.setRemoteDescription(new RTCSessionDescription({
          type: 'answer',
          sdp: msg.sdp
        }));
      }
      break;
    }

    case 'candidate': {
      if (peerConnection && msg.candidate) {
        try {
          await peerConnection.addIceCandidate(new RTCIceCandidate(msg.candidate));
        } catch (e) {
          console.warn('Gagal menambah ICE candidate:', e);
        }
      }
      break;
    }

    case 'peer_disconnected': {
      showError(msg.message || 'HP target memutuskan koneksi.');
      cleanup();
      break;
    }

    case 'error': {
      showError(msg.message);
      cleanup();
      break;
    }
  }
}

async function setupWebRTC() {
  peerConnection = new RTCPeerConnection(rtcConfig);

  // Minta video track via Unified Plan Transceiver
  peerConnection.addTransceiver('video', { direction: 'recvonly' });

  // Setup DataChannel untuk input sentuhan rendah latensi
  dataChannel = peerConnection.createDataChannel('input_channel', {
    ordered: true
  });

  dataChannel.onopen = () => {
    console.log('[DataChannel] Terbuka & siap kirim event sentuhan');
  };

  // Terima video stream dari HP
  peerConnection.ontrack = (event) => {
    console.log('[WebRTC] Menerima video track:', event.track);
    if (event.streams && event.streams[0]) {
      remoteVideo.srcObject = event.streams[0];
    } else {
      remoteVideo.srcObject = new MediaStream([event.track]);
    }

    remoteVideo.play().catch(err => {
      console.warn('[Video Play]', err);
    });
    
    // Tampilkan viewer UI
    connectPanel.style.display = 'none';
    viewerPanel.style.display = 'flex';
    connectedDeviceId.textContent = currentTargetId;
    setStatus('connected', 'Tersambung (Unattended)');
  };

  remoteVideo.onloadedmetadata = () => {
    console.log(`[Video Metadata] Resolusi HP: ${remoteVideo.videoWidth}x${remoteVideo.videoHeight}`);
  };

  peerConnection.onicecandidate = (event) => {
    if (event.candidate && ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({
        type: 'candidate',
        targetDeviceId: currentTargetId,
        candidate: event.candidate
      }));
    }
  };

  peerConnection.onconnectionstatechange = () => {
    console.log('[WebRTC State]', peerConnection.connectionState);
    if (peerConnection.connectionState === 'disconnected' || peerConnection.connectionState === 'failed') {
      showError('Koneksi WebRTC terputus.');
      cleanup();
    }
  };

  // Buat SDP Offer dan kirim ke Host
  const offer = await peerConnection.createOffer();
  await peerConnection.setLocalDescription(offer);

  ws.send(JSON.stringify({
    type: 'offer',
    targetDeviceId: currentTargetId,
    sdp: offer.sdp
  }));
}

// Input Touch Injection Handlers
function sendInput(data) {
  if (dataChannel && dataChannel.readyState === 'open') {
    dataChannel.send(JSON.stringify(data));
  }
}

function getNormalizedCoordinates(e) {
  const rect = remoteVideo.getBoundingClientRect();
  const videoWidth = remoteVideo.videoWidth;
  const videoHeight = remoteVideo.videoHeight;

  // Jika metadata video belum ada, fallback ke rasio elemen
  if (!videoWidth || !videoHeight) {
    const x = Math.max(0, Math.min(rect.width, e.clientX - rect.left));
    const y = Math.max(0, Math.min(rect.height, e.clientY - rect.top));
    return {
      xRatio: +(x / rect.width).toFixed(4),
      yRatio: +(y / rect.height).toFixed(4),
      rawX: e.clientX - rect.left,
      rawY: e.clientY - rect.top
    };
  }

  // Hitung area sebenarnya yang ditempati oleh video di dalam elemen (kompensasi letterbox/pillarbox)
  const videoAspect = videoWidth / videoHeight;
  const elementAspect = rect.width / rect.height;

  let actualRenderedWidth = rect.width;
  let actualRenderedHeight = rect.height;
  let offsetX = 0;
  let offsetY = 0;

  if (elementAspect > videoAspect) {
    // Ada pillarbox (margin hitam di sisi kiri dan kanan)
    actualRenderedWidth = rect.height * videoAspect;
    offsetX = (rect.width - actualRenderedWidth) / 2;
  } else {
    // Ada letterbox (margin hitam di sisi atas dan bawah)
    actualRenderedHeight = rect.width / videoAspect;
    offsetY = (rect.height - actualRenderedHeight) / 2;
  }

  // Hitung posisi klik relatif terhadap konten video murni
  const clickX = e.clientX - rect.left - offsetX;
  const clickY = e.clientY - rect.top - offsetY;

  const xRatio = Math.max(0, Math.min(1, clickX / actualRenderedWidth));
  const yRatio = Math.max(0, Math.min(1, clickY / actualRenderedHeight));

  return {
    xRatio: +xRatio.toFixed(4),
    yRatio: +yRatio.toFixed(4),
    rawX: e.clientX - rect.left,
    rawY: e.clientY - rect.top
  };
}

function showTouch(x, y) {
  touchIndicator.style.left = `${x}px`;
  touchIndicator.style.top = `${y}px`;
  touchIndicator.style.display = 'block';
}

function hideTouch() {
  touchIndicator.style.display = 'none';
}

remoteVideo.addEventListener('mousedown', (e) => {
  e.preventDefault();
  isMouseDown = true;
  const coords = getNormalizedCoordinates(e);
  showTouch(coords.rawX, coords.rawY);
  sendInput({
    action: 'DOWN',
    xRatio: coords.xRatio,
    yRatio: coords.yRatio
  });
});

window.addEventListener('mousemove', (e) => {
  if (!isMouseDown) return;
  const now = Date.now();
  if (now - lastSentMoveTime < 16) return; // Cap at ~60fps
  lastSentMoveTime = now;

  const coords = getNormalizedCoordinates(e);
  showTouch(coords.rawX, coords.rawY);
  sendInput({
    action: 'MOVE',
    xRatio: coords.xRatio,
    yRatio: coords.yRatio
  });
});

window.addEventListener('mouseup', (e) => {
  if (!isMouseDown) return;
  isMouseDown = false;
  hideTouch();
  const coords = getNormalizedCoordinates(e);
  sendInput({
    action: 'UP',
    xRatio: coords.xRatio,
    yRatio: coords.yRatio
  });
});

// Scroll / Wheel
remoteVideo.addEventListener('wheel', (e) => {
  e.preventDefault();
  const coords = getNormalizedCoordinates(e);
  // Send vertical swipe
  const delta = e.deltaY;
  const targetY = delta > 0 ? Math.max(0, coords.yRatio - 0.2) : Math.min(1, coords.yRatio + 0.2);
  sendInput({
    action: 'SWIPE',
    fromX: coords.xRatio,
    fromY: coords.yRatio,
    toX: coords.xRatio,
    toY: +targetY.toFixed(4),
    durationMs: 150
  });
}, { passive: false });

// Keyboard text injection
window.addEventListener('keydown', (e) => {
  // Hanya jika sedang aktif di remote viewer panel
  if (viewerPanel.style.display !== 'flex') return;

  if (e.key === 'Backspace') {
    sendInput({ action: 'KEY', key: 'BACKSPACE' });
  } else if (e.key === 'Enter') {
    sendInput({ action: 'KEY', key: 'ENTER' });
  } else if (e.key === 'Escape') {
    sendInput({ action: 'COMMAND', command: 'BACK' });
  } else if (e.key.length === 1 && !e.ctrlKey && !e.altKey && !e.metaKey) {
    sendInput({ action: 'TEXT', text: e.key });
  }
});

// Navigation toolbar buttons
btnBack.addEventListener('click', () => {
  sendInput({ action: 'COMMAND', command: 'BACK' });
});

btnHome.addEventListener('click', () => {
  sendInput({ action: 'COMMAND', command: 'HOME' });
});

btnRecents.addEventListener('click', () => {
  sendInput({ action: 'COMMAND', command: 'RECENTS' });
});

btnDisconnect.addEventListener('click', () => {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({ type: 'disconnect' }));
  }
  cleanup();
});

function cleanup() {
  if (dataChannel) {
    dataChannel.close();
    dataChannel = null;
  }
  if (peerConnection) {
    peerConnection.close();
    peerConnection = null;
  }
  if (ws) {
    ws.close();
    ws = null;
  }
  if (remoteVideo.srcObject) {
    remoteVideo.srcObject.getTracks().forEach(t => t.stop());
    remoteVideo.srcObject = null;
  }
  btnConnect.disabled = false;
  connectPanel.style.display = 'block';
  viewerPanel.style.display = 'none';
  setStatus('disconnected', 'Terputus');
}
