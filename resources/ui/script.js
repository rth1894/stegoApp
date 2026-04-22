// ─────────────────────────────────────────────────────────────────────────────
// script.js — UI logic for Digital Steganography App
// Prompt 8: All Java bridge calls are now active.
// ─────────────────────────────────────────────────────────────────────────────

// ── State ─────────────────────────────────────────────────────────────────────
const state = {
  maxChars: 500   // updated by Java via setCapacity() after image upload
};

// ── Tab Switching ─────────────────────────────────────────────────────────────
function switchTab(tabName) {
  document.querySelectorAll('.section').forEach(s => s.classList.remove('active'));
  document.querySelectorAll('.nav-btn').forEach(b => b.classList.remove('active'));
  document.getElementById('section-' + tabName).classList.add('active');
  document.getElementById('tab-'     + tabName).classList.add('active');
}

// ── Upload Zone Click → Java FileChooser ──────────────────────────────────────
// Instead of browser file input, we call Java to open a native FileChooser.
// Java gets the real absolute path and calls onImageSelected() back.
function triggerUpload(context) {
  if (typeof window.javaConnector !== 'undefined') {
    window.javaConnector.openFileChooser(context);
  } else {
    showToast('Bridge not connected yet.', 'error');
  }
}

// ── Called BY Java after FileChooser selection ────────────────────────────────
// Java passes the absolute path and context back here.
// We show a preview using the file:// protocol.
function onImageSelected(absolutePath, context) {
  const previewEl = document.getElementById(context + '-preview');
  const promptEl  = document.getElementById(context + '-upload-prompt');

  if (previewEl && promptEl) {
    // Use file:// protocol to show local image preview in WebView
    previewEl.src          = 'file:///' + absolutePath.replace(/\\/g, '/');
    previewEl.style.display = 'block';
    promptEl.style.display  = 'none';
  }

  // Show capacity bar for encode context
  if (context === 'encode') {
    document.getElementById('encode-capacity-bar').style.display = 'flex';
    updateCharCounter();
  }

  showToast('Image loaded successfully.', 'success');
}

// ── Character Counter ─────────────────────────────────────────────────────────
function updateCharCounter() {
  const textarea = document.getElementById('encode-message');
  const counter  = document.getElementById('encode-char-counter');
  const used     = textarea.value.length;
  const max      = state.maxChars;

  counter.textContent = used + ' / ' + max;

  counter.classList.remove('warn', 'danger');
  if      (used >= max * 0.9)  counter.classList.add('danger');
  else if (used >= max * 0.75) counter.classList.add('warn');

  // Update capacity bar fill
  const pct  = Math.min((used / max) * 100, 100);
  const fill = document.getElementById('encode-capacity-fill');
  if (fill) fill.style.width = pct + '%';

  const text = document.getElementById('encode-capacity-text');
  if (text) text.textContent = used + ' / ' + max + ' chars';
}

// ── Called BY Java to update max character capacity ───────────────────────────
function setCapacity(maxChars) {
  state.maxChars = parseInt(maxChars);
  const textarea = document.getElementById('encode-message');
  textarea.maxLength = state.maxChars;
  updateCharCounter();
  showToast('Capacity: ' + state.maxChars + ' chars available.', 'success');
}

// ── Password Toggle ───────────────────────────────────────────────────────────
function togglePassword(inputId) {
  const input = document.getElementById(inputId);
  input.type  = input.type === 'password' ? 'text' : 'password';
}

// ── ENCODE ────────────────────────────────────────────────────────────────────
function encodeMessage() {
  const message  = document.getElementById('encode-message').value.trim();
  const password = document.getElementById('encode-password').value;

  // JS-side validation before calling Java
  if (!message)  { showToast('Please enter a secret message.', 'error'); return; }
  if (!password) { showToast('Please enter a password.',       'error'); return; }

  showToast('Encoding...', '');

  // Call Java bridge
  if (typeof window.javaConnector !== 'undefined') {
    window.javaConnector.encodeMessage(message, password);
  } else {
    showToast('Bridge not connected.', 'error');
  }
}

// ── DECODE ────────────────────────────────────────────────────────────────────
function decodeMessage() {
  const password = document.getElementById('decode-password').value;

  if (!password) { showToast('Please enter a password.', 'error'); return; }

  showToast('Decoding...', '');

  if (typeof window.javaConnector !== 'undefined') {
    window.javaConnector.decodeMessage(password);
  } else {
    showToast('Bridge not connected.', 'error');
  }
}

// ── ANALYSIS ──────────────────────────────────────────────────────────────────
function analyseImage() {
  if (typeof window.javaConnector !== 'undefined') {
    window.javaConnector.openFileChooser('analysis');
  } else {
    showToast('Bridge not connected.', 'error');
  }
}

// ── Called BY Java to update a single analysis stat ──────────────────────────
function setAnalysisStat(elementId, value) {
  const el = document.getElementById(elementId);
  if (el) el.textContent = value;
}

// ── Result Display — called BY Java via WebEngine.executeScript() ─────────────

function displayEncodeResult(outputPath) {
  const box = document.getElementById('encode-result');
  box.innerHTML =
    '<div class="result-success">' +
      '<div class="result-empty-icon">DONE</div>' +
      '<strong>Encoding Successful</strong>' +
      '<p style="font-size:0.78rem;color:var(--text-soft);margin-top:6px">' +
        'Saved to:<br>' + outputPath +
      '</p>' +
    '</div>';
  showToast('Image encoded and saved.', 'success');
}

function displayDecodeResult(message) {
  const box = document.getElementById('decode-result');
  box.innerHTML =
    '<div style="width:100%">' +
      '<div class="field-label" style="margin-bottom:8px">Revealed Message</div>' +
      '<div class="result-message">' + escapeHtml(message) + '</div>' +
    '</div>';
  showToast('Message decoded successfully.', 'success');
}

function displayError(context, errorMessage) {
  const box = document.getElementById(context + '-result');
  if (box) {
    box.innerHTML =
      '<div class="result-error">Error: ' + escapeHtml(errorMessage) + '</div>';
  }
  showToast(errorMessage, 'error');
}

// ── Toast ─────────────────────────────────────────────────────────────────────
function showToast(message, type) {
  const toast       = document.getElementById('toast');
  toast.textContent = message;
  toast.className   = 'toast show ' + (type || '');
  clearTimeout(window._toastTimer);
  window._toastTimer = setTimeout(() => toast.classList.remove('show'), 3000);
}

// ── Helpers ───────────────────────────────────────────────────────────────────
function escapeHtml(str) {
  return String(str)
    .replace(/&/g,  '&amp;')
    .replace(/</g,  '&lt;')
    .replace(/>/g,  '&gt;')
    .replace(/"/g,  '&quot;')
    .replace(/'/g,  '&#39;');
}


// ── Clear Section ─────────────────────────────────────────────────────────────
function clearSection(context) {
  // Reset upload zone — hide preview, show prompt
  const previewEl = document.getElementById(context + '-preview');
  const promptEl  = document.getElementById(context + '-upload-prompt');

  if (previewEl) {
    previewEl.src          = '';
    previewEl.style.display = 'none';
  }
  if (promptEl) {
    promptEl.style.display = 'flex';
  }

  // Clear result box
  const resultBox = document.getElementById(context + '-result');
  if (resultBox) {
    if (context === 'encode') {
      resultBox.innerHTML =
        '<div class="result-empty">' +
          '<div class="result-empty-icon">IMG</div>' +
          '<p>Stego-image will appear here after encoding.</p>' +
        '</div>';
    } else if (context === 'decode') {
      resultBox.innerHTML =
        '<div class="result-empty">' +
          '<div class="result-empty-icon">TXT</div>' +
          '<p>Decrypted message will appear here.</p>' +
        '</div>';
    }
  }

  // Clear inputs
  if (context === 'encode') {
    document.getElementById('encode-message').value  = '';
    document.getElementById('encode-password').value = '';
    document.getElementById('encode-capacity-bar').style.display = 'none';
    updateCharCounter();
  }

  if (context === 'decode') {
    document.getElementById('decode-password').value = '';
  }

  // Clear analysis stats
  if (context === 'analysis') {
    ['stat-width', 'stat-height', 'stat-capacity', 'stat-pixels'].forEach(id => {
      document.getElementById(id).textContent = '--';
    });
  }

  // Tell Java to clear stored path for this context
  if (typeof window.javaConnector !== 'undefined') {
    window.javaConnector.clearContext(context);
  }

  showToast('Cleared.', '');
}