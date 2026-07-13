#!/usr/bin/env node
/**
 * pi-mobile wrapper server
 *
 * Starts pi-web and serves PWA manifest + service worker
 * so the phone can install it as a home-screen app.
 */

const { spawn } = require('child_process');
const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = process.env.PI_MOBILE_PORT || 30142;
const PI_WEB_PORT = process.env.PI_WEB_PORT || 30141;
const HOST = process.env.PI_MOBILE_HOST || '0.0.0.0';

const PUBLIC_DIR = path.join(__dirname, 'public');

// ── Find pi-web binary ────────────────────────────────────────────
function findPiWeb() {
  // 1. Try npx global
  const npxPath = process.env.HOME + '/.npm/_npx/*/node_modules/.bin/pi-web';
  const glob = require('child_process').execSync(
    `ls -t ${npxPath} 2>/dev/null | head -1`,
    { encoding: 'utf8', shell: true }
  ).trim();
  if (glob) return glob;

  // 2. Try which
  try {
    return require('child_process').execSync('which pi-web', { encoding: 'utf8' }).trim();
  } catch {}

  // 3. Fall back to npx
  return 'npx';
}

// ── Check if pi-web is already running ──────────────────────────────
function isPortInUse(port) {
  return new Promise((resolve) => {
    const server = require('net').createServer();
    server.once('error', () => resolve(true));
    server.once('listening', () => { server.close(); resolve(false); });
    server.listen(port, '127.0.0.1');
  });
}

// ── Start pi-web ──────────────────────────────────────────────────
async function startPiWeb() {
  const inUse = await isPortInUse(PI_WEB_PORT);
  if (inUse) {
    console.log(`[pi-mobile] pi-web already running on :${PI_WEB_PORT}, reusing.`);
    return null;
  }

  const bin = findPiWeb();
  const args = bin === 'npx'
    ? ['@agegr/pi-web@latest', '--port', String(PI_WEB_PORT)]
    : ['--port', String(PI_WEB_PORT)];

  console.log(`[pi-mobile] Starting pi-web on :${PI_WEB_PORT} via ${bin} ...`);
  const proc = spawn(bin, args, {
    stdio: ['ignore', 'inherit', 'inherit'],
    env: { ...process.env, PORT: String(PI_WEB_PORT) },
    shell: true,
  });

  proc.on('error', (err) => {
    console.error('[pi-mobile] Failed to start pi-web:', err.message);
    process.exit(1);
  });

  proc.on('exit', (code) => {
    console.log(`[pi-mobile] pi-web exited with code ${code}`);
    process.exit(code ?? 0);
  });

  return proc;
}

// ── PWA manifest ──────────────────────────────────────────────────
const MANIFEST = {
  name: 'Pi Coding Agent',
  short_name: 'Pi',
  description: 'Mobile client for pi-web — access Pi coding assistant from your phone',
  start_url: '/',
  display: 'standalone',
  orientation: 'portrait',
  background_color: '#1a1a2e',
  theme_color: '#0f3460',
  icons: [
    { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png' },
    { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png' },
    { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
  ],
  categories: ['productivity', 'developer-tools'],
};

// ── Service Worker ────────────────────────────────────────────────
const SW = `
self.addEventListener('fetch', (event) => {
  // Network-first for all requests — offline isn't useful for Pi
  event.respondWith(fetch(event.request).catch(() => {
    return new Response('Offline — Pi needs a network connection', {
      status: 503,
      headers: { 'Content-Type': 'text/plain' },
    });
  }));
});
`;

// ── Serve PWA files ───────────────────────────────────────────────
function startPWAServer() {
  const server = http.createServer((req, res) => {
    if (req.url === '/manifest.json') {
      res.writeHead(200, {
        'Content-Type': 'application/manifest+json',
        'Access-Control-Allow-Origin': '*',
      });
      res.end(JSON.stringify(MANIFEST));
      return;
    }

    if (req.url === '/sw.js') {
      res.writeHead(200, {
        'Content-Type': 'application/javascript',
        'Access-Control-Allow-Origin': '*',
      });
      res.end(SW);
      return;
    }

    // Serve icons from public dir
    const iconMatch = req.url.match(/^\/icons\/(.+)$/);
    if (iconMatch) {
      const filePath = path.join(PUBLIC_DIR, iconMatch[1]);
      if (fs.existsSync(filePath)) {
        const ext = path.extname(filePath);
        const types = { '.png': 'image/png', '.svg': 'image/svg+xml' };
        res.writeHead(200, { 'Content-Type': types[ext] || 'application/octet-stream' });
        res.end(fs.readFileSync(filePath));
        return;
      }
    }

    // Health check
    if (req.url === '/health') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ status: 'ok', piWebPort: PI_WEB_PORT }));
      return;
    }

    // Proxy to pi-web for manifest.json and sw.js injection
    const options = {
      hostname: '127.0.0.1',
      port: PI_WEB_PORT,
      path: req.url,
      method: req.method,
      headers: { ...req.headers, host: `localhost:${PI_WEB_PORT}` },
    };

    const proxyReq = http.request(options, (proxyRes) => {
      // Inject manifest link into HTML
      let body = '';
      proxyRes.on('data', (chunk) => { body += chunk; });
      proxyRes.on('end', () => {
        if (proxyRes.headers['content-type']?.includes('text/html')) {
          body = body.replace(
            '</head>',
            `<link rel="manifest" href="/manifest.json">\n<script>if('serviceWorker' in navigator) { navigator.serviceWorker.register('/sw.js'); }</script>\n</head>`
          );
          res.writeHead(proxyRes.statusCode, {
            ...proxyRes.headers,
            'content-length': Buffer.byteLength(body),
          });
          res.end(body);
        } else {
          res.writeHead(proxyRes.statusCode, proxyRes.headers);
          res.end(body);
        }
      });
    });

    proxyReq.on('error', (err) => {
      res.writeHead(502, { 'Content-Type': 'text/plain' });
      res.end(`Proxy error: ${err.message}`);
    });

    req.pipe(proxyReq);
  });

  server.listen(PORT, HOST, () => {
    console.log(`\n╔══════════════════════════════════════════════╗`);
    console.log(`║  🚀 Pi Mobile                               ║`);
    console.log(`║                                              ║`);
    console.log(`║  Web UI:  http://localhost:${PI_WEB_PORT}          ║`);
    console.log(`║  Mobile:  http://localhost:${PORT} (PWA proxy)    ║`);
    console.log(`║  ── Phone access (Tailscale) ──                      ║`);
    console.log(`║                                                      ║`);
    console.log(`║  Option A (PWA, needs HTTPS):                        ║`);
    console.log(`║    tailscale serve --bg --https 443 localhost:${PORT}   ║`);
    console.log(`║    Open https://<hostname>.ts.net → Add to Home Screen║`);
    console.log(`║                                                      ║`);
    console.log(`║  Option B (Android APK, no address bar):             ║`);
    console.log(`║    cd android && ./gradlew assembleDebug             ║`);
    console.log(`║                                                      ║`);
    console.log(`║  Need help? Read README.md                          ║`);
    console.log(`╚══════════════════════════════════════════════╝\n`);
  });
}

// ── Main ──────────────────────────────────────────────────────────
(async () => {
  const piWeb = await startPiWeb();
  const waitMs = piWeb ? 2000 : 500;
  setTimeout(startPWAServer, waitMs);

  process.on('SIGINT', () => {
    if (piWeb) piWeb.kill();
    process.exit(0);
  });

  process.on('SIGTERM', () => {
    if (piWeb) piWeb.kill();
    process.exit(0);
  });
})();
