const { app, BrowserWindow, ipcMain, shell, dialog } = require('electron');
const path = require('path');
const { pathToFileURL } = require('url');
const { spawn } = require('child_process');
const fs = require('fs');
const http = require('http');
const log = require('electron-log');

// Determine if we're in development or production (packaged)
const isDev = !app.isPackaged;
// In dev: __dirname is desktop-shell/, FRONTEND_ROOT is parent (where HTML files are)
// In packaged: FRONTEND_ROOT is app install dir (where executable is, for data/)
const FRONTEND_ROOT = isDev ? path.resolve(__dirname, '..') : path.dirname(process.execPath);

// HTML files location - check multiple possible locations
let HTML_ROOT = FRONTEND_ROOT;
if (!isDev) {
  // Try multiple locations where files might be
  const possiblePaths = [
    path.dirname(process.execPath), // App root (where extraFiles puts them)
    path.join(path.dirname(process.execPath), 'resources', 'app'), // resources/app
    path.join(process.resourcesPath, 'app'), // Alternative resources path
    __dirname // Current directory (where main.js is)
  ];
  
  for (const testPath of possiblePaths) {
    if (fs.existsSync(path.join(testPath, 'index.html'))) {
      HTML_ROOT = testPath;
      log.info('Found HTML files at:', HTML_ROOT);
      break;
    }
  }
}

function searchForIndexHtml(rootDir, maxDepth = 4) {
  if (!rootDir) return null;
  const visited = new Set();
  const target = 'index.html';

  function walk(dir, depth) {
    if (!dir || depth < 0) return null;

    let real = dir;
    try {
      real = fs.realpathSync(dir);
    } catch {
      // ignore
    }
    if (visited.has(real)) return null;
    visited.add(real);

    const direct = path.join(dir, target);
    if (fs.existsSync(direct)) return direct;
    if (depth === 0) return null;

    const skip = new Set(['node_modules', 'runtime', 'bayport-backend', '.git', '.cursor']);
    let entries;
    try {
      entries = fs.readdirSync(dir, { withFileTypes: true });
    } catch {
      return null;
    }

    for (const ent of entries) {
      if (!ent.isDirectory()) continue;
      if (skip.has(ent.name)) continue;
      const found = walk(path.join(dir, ent.name), depth - 1);
      if (found) return found;
    }
    return null;
  }

  return walk(rootDir, maxDepth);
}
const BACKEND_DIR = isDev 
  ? path.resolve(__dirname, '..', 'bayport-backend')
  : FRONTEND_ROOT; // In packaged app, JAR is in app root (via extraFiles)
const JAR_PATH = isDev
  ? path.join(BACKEND_DIR, 'target', 'bayport-backend-0.0.1-SNAPSHOT.jar')
  : path.join(FRONTEND_ROOT, 'bayport-backend-0.0.1-SNAPSHOT.jar');
const DATA_DIR = isDev ? path.join(FRONTEND_ROOT, 'data') : path.join(app.getPath('userData'), 'data');

// Resolve embedded Java runtime directory
// On macOS, packaged apps are in .app bundles:
// - process.execPath = /Applications/App.app/Contents/MacOS/App
// - Resources are in Contents/Resources/
// On Windows, files are in the same directory as the executable
let EMBEDDED_JAVA_DIR;
if (isDev) {
  EMBEDDED_JAVA_DIR = path.join(__dirname, 'runtime');
} else {
  if (process.platform === 'darwin') {
    // macOS: runtime could be in Contents/Resources/runtime (extraResources) 
    // or in app root (extraFiles)
    const resourcesPath = process.resourcesPath || path.join(path.dirname(process.execPath), '..', 'Resources');
    const resourcesRuntime = path.join(resourcesPath, 'runtime');
    const appRoot = path.join(path.dirname(process.execPath), '..', '..');
    const appRootRuntime = path.join(appRoot, 'runtime');
    
    // Check both locations, prefer Resources (extraResources)
    if (fs.existsSync(resourcesRuntime)) {
      EMBEDDED_JAVA_DIR = resourcesRuntime;
    } else if (fs.existsSync(appRootRuntime)) {
      EMBEDDED_JAVA_DIR = appRootRuntime;
    } else {
      // Default to Resources location
      EMBEDDED_JAVA_DIR = resourcesRuntime;
    }
  } else {
    // Windows/Linux: runtime is in the same directory as executable (where extraFiles puts it)
    EMBEDDED_JAVA_DIR = path.join(FRONTEND_ROOT, 'runtime');
  }
}

let mainWindow;
let backendProcess = null;
const BACKEND_PORT = 8080;
const BACKEND_URL = `http://localhost:${BACKEND_PORT}`;
const API_HEALTH_CHECK = `${BACKEND_URL}/api/health`;

// Ensure data directory exists
if (!fs.existsSync(DATA_DIR)) {
  fs.mkdirSync(DATA_DIR, { recursive: true });
  fs.mkdirSync(path.join(DATA_DIR, 'uploads'), { recursive: true });
}

function resolveJavaExecutable() {
  const javaCommand = process.platform === 'win32' ? 'java.exe' : 'java';
  
  // First, try embedded Java runtime
  const embeddedJava = path.join(EMBEDDED_JAVA_DIR, 'bin', javaCommand);
  if (fs.existsSync(embeddedJava)) {
    log.info('Using embedded Java runtime:', embeddedJava);
    return embeddedJava;
  }
  
  // Log that embedded Java wasn't found
  log.warn('Embedded Java not found at:', embeddedJava);
  log.warn('EMBEDDED_JAVA_DIR:', EMBEDDED_JAVA_DIR);
  log.warn('Checking alternative locations...');
  
  // On macOS, also check Contents/Resources/runtime
  if (process.platform === 'darwin' && !isDev) {
    const altPaths = [
      path.join(process.resourcesPath, 'runtime', 'bin', javaCommand),
      path.join(path.dirname(process.execPath), '..', 'Resources', 'runtime', 'bin', javaCommand),
      path.join(path.dirname(process.execPath), '..', '..', 'runtime', 'bin', javaCommand),
    ];
    for (const altPath of altPaths) {
      if (fs.existsSync(altPath)) {
        log.info('Found Java at alternative location:', altPath);
        return altPath;
      }
    }
  }

  // Fallback to JAVA_HOME
  if (process.env.JAVA_HOME) {
    const javaHomeExec = path.join(process.env.JAVA_HOME, 'bin', javaCommand);
    if (fs.existsSync(javaHomeExec)) {
      log.info('Using JAVA_HOME:', javaHomeExec);
      return javaHomeExec;
    }
  }

  // Last resort: use system Java (if in PATH)
  log.warn('Using system Java (may not be available):', javaCommand);
  return javaCommand;
}

function startBackend() {
  return new Promise((resolve, reject) => {
    // Check if JAR exists
    if (!fs.existsSync(JAR_PATH)) {
      reject(new Error(`Backend JAR not found at ${JAR_PATH}. Please build it first with: mvn clean package`));
      return;
    }

    log.info('Starting Spring Boot backend...');
    
    // Find Java executable
    const javaPath = resolveJavaExecutable();
    
    // Check if Java exists (skip check if it's just 'java' or 'java.exe' - will try to run it)
    if (javaPath !== 'java' && javaPath !== 'java.exe' && !fs.existsSync(javaPath)) {
      const errorMsg = `Java runtime not found at ${javaPath}.\n\n` +
        `Embedded Java directory: ${EMBEDDED_JAVA_DIR}\n` +
        `Please ensure:\n` +
        `1. Run 'npm run build-runtime' to create the embedded Java runtime\n` +
        `2. The runtime directory is included in the build\n` +
        `3. Or install Java 17+ and set JAVA_HOME environment variable`;
      log.error(errorMsg);
      reject(new Error(errorMsg));
      return;
    }
    
    log.info('Starting backend with Java:', javaPath);

    // Start backend with h2 profile so desktop app runs cross-OS without MySQL setup.
    const normalizedDataDir = DATA_DIR.replace(/\\/g, '/');
    const dbPath = `${normalizedDataDir}/bayport-db`;
    const uploadPath = `${normalizedDataDir}/uploads`;

    backendProcess = spawn(javaPath, [
      '-jar',
      JAR_PATH,
      '--spring.profiles.active=h2',
      `--spring.datasource.url=jdbc:h2:file:${dbPath}`,
      `--bayport.upload-dir=${uploadPath}`
    ], {
      cwd: FRONTEND_ROOT, // Set working directory so data/ folder is relative to app root
      stdio: ['ignore', 'pipe', 'pipe'],
      shell: false
    });

    let backendOutput = '';
    let backendError = '';

    backendProcess.stdout.on('data', (data) => {
      const text = data.toString();
      backendOutput += text;
      log.info(`[Backend] ${text.trim()}`);
      
      // Check if backend is ready (look for "Started BayportApplication" or similar)
      if (text.includes('Started BayportApplication') || text.includes('Tomcat started on port')) {
        log.info('Backend appears to be starting...');
      }
    });

    backendProcess.stderr.on('data', (data) => {
      const text = data.toString();
      backendError += text;
      log.error(`[Backend Error] ${text.trim()}`);
    });

    backendProcess.on('error', (err) => {
      log.error('Failed to start backend process:', err);
      reject(new Error(`Failed to start backend: ${err.message}. Make sure Java 17+ is installed and in PATH.`));
    });

    backendProcess.on('exit', (code) => {
      if (code !== 0 && code !== null) {
        log.error(`Backend process exited with code ${code}`);
        log.error('Backend output:', backendOutput);
        log.error('Backend errors:', backendError);
      }
    });

    // Wait for backend to be ready by pinging the health endpoint
    let attempts = 0;
    const maxAttempts = 120; // 120 seconds max wait for slower machines
    const checkInterval = setInterval(() => {
      attempts++;
      
      const req = http.get(API_HEALTH_CHECK, { timeout: 2000 }, (res) => {
        if (res.statusCode === 200) {
          clearInterval(checkInterval);
          log.info(`Backend is ready! (took ${attempts} seconds)`);
          resolve();
        } else {
          // Non-200 status, backend might be starting
          if (attempts >= maxAttempts) {
            clearInterval(checkInterval);
            reject(new Error(`Backend returned status ${res.statusCode} after ${maxAttempts} seconds.`));
          }
        }
      });
      
      req.on('error', (err) => {
        // Backend not ready yet, continue waiting
        if (attempts >= maxAttempts) {
          clearInterval(checkInterval);
          const javaCheck = javaPath === 'java' || javaPath === 'java.exe' 
            ? '\n\n⚠️ Java might not be installed. Please install Java 17+ from https://adoptium.net/'
            : '';
          reject(new Error(`Backend failed to start within ${maxAttempts} seconds.${javaCheck}\n\nError: ${err.message}\n\nCheck that:\n- Java 17+ is installed\n- Port 8080 is available\n- Backend JAR exists at: ${JAR_PATH}`));
        }
      });
      
      req.on('timeout', () => {
        req.destroy();
        if (attempts >= maxAttempts) {
          clearInterval(checkInterval);
          reject(new Error(`Backend failed to start within ${maxAttempts} seconds. The server may be taking longer than expected to initialize.`));
        }
      });
    }, 1000);
  });
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1400,
    height: 900,
    minWidth: 1024,
    minHeight: 768,
    backgroundColor: '#f6f9f9',
    autoHideMenuBar: true,
    show: true, // Show immediately with loading screen
    icon: isDev ? path.join(__dirname, '..', 'assets', 'logo.png') : (process.platform === 'win32' 
      ? path.join(path.dirname(process.execPath), 'resources', 'app', 'assets', 'logo.png')
      : path.join(process.resourcesPath, 'assets', 'logo.png')),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      sandbox: false, // Changed to false for better compatibility
      webSecurity: false, // Allow file:// protocol to access localhost API
      nodeIntegration: false,
      backgroundThrottling: false,
      spellcheck: false
    }
  });

  // Show loading screen immediately
  const loadingHTML = `
    <!DOCTYPE html>
    <html>
    <head>
      <meta charset="UTF-8">
      <title>Bayport Veterinary Clinic</title>
      <style>
        body {
          margin: 0;
          padding: 0;
          background: #f6f9f9;
          display: flex;
          justify-content: center;
          align-items: center;
          height: 100vh;
          font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
        }
        .loading-container {
          text-align: center;
        }
        .spinner {
          border: 4px solid #e5e7eb;
          border-top: 4px solid #0057b8;
          border-radius: 50%;
          width: 50px;
          height: 50px;
          animation: spin 1s linear infinite;
          margin: 0 auto 20px;
        }
        @keyframes spin {
          0% { transform: rotate(0deg); }
          100% { transform: rotate(360deg); }
        }
        h2 {
          color: #0057b8;
          margin: 0;
        }
        p {
          color: #6b7280;
          margin-top: 10px;
        }
      </style>
    </head>
    <body>
      <div class="loading-container">
        <div class="spinner"></div>
        <h2>Bayport Veterinary Clinic</h2>
        <p>Starting application...</p>
      </div>
    </body>
    </html>
  `;
  mainWindow.loadURL(`data:text/html;charset=utf-8,${encodeURIComponent(loadingHTML)}`);

  // Load frontend after backend is ready
  startBackend()
    .then(() => {
      const directIndex = path.join(HTML_ROOT, 'index.html');
      if (fs.existsSync(directIndex)) {
        mainWindow.loadURL(pathToFileURL(directIndex).href);
        log.info('Frontend loaded from:', directIndex);
        return;
      }

      // Packaged apps may place html under different subfolders; search common roots.
      const candidateRoots = [
        HTML_ROOT,
        FRONTEND_ROOT,
        app.getAppPath(),
        path.dirname(process.execPath),
        process.resourcesPath && path.join(process.resourcesPath, 'app'),
        process.resourcesPath && path.join(process.resourcesPath, 'app.asar.unpacked'),
      ].filter(Boolean);

      let foundPath = null;
      for (const root of candidateRoots) {
        foundPath = searchForIndexHtml(root, 4);
        if (foundPath) break;
      }

      if (!foundPath) {
        throw new Error(
          'Frontend file not found (index.html). Looked in common roots and recursively searched for index.html.\n' +
          `Direct expected: ${directIndex}\n` +
          `Roots: ${candidateRoots.join(', ')}`
        );
      }

      mainWindow.loadURL(pathToFileURL(foundPath).href);
      log.info('Frontend loaded from:', foundPath);
      
      // Add error handler for frontend loading issues
      mainWindow.webContents.on('did-fail-load', (event, errorCode, errorDescription, validatedURL) => {
          log.error('Failed to load frontend:', errorCode, errorDescription, validatedURL);
          if (errorCode !== -3) { // -3 is ERR_ABORTED, which we can ignore
            const errorHTML = `
              <!DOCTYPE html>
              <html>
              <head>
                <meta charset="UTF-8">
                <title>Bayport Veterinary Clinic - Loading Error</title>
                <style>
                  body {
                    margin: 0;
                    padding: 40px;
                    background: #f6f9f9;
                    font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                  }
                  .error-container {
                    max-width: 600px;
                    margin: 0 auto;
                    background: white;
                    padding: 30px;
                    border-radius: 8px;
                    box-shadow: 0 2px 8px rgba(0,0,0,0.1);
                  }
                  h1 { color: #dc2626; margin-top: 0; }
                  pre { background: #f3f4f6; padding: 15px; border-radius: 4px; overflow-x: auto; }
                  .retry-btn {
                    background: #0057b8;
                    color: white;
                    padding: 10px 20px;
                    border: none;
                    border-radius: 5px;
                    cursor: pointer;
                    margin-top: 15px;
                  }
                  .retry-btn:hover { background: #1d4ed8; }
                </style>
              </head>
              <body>
                <div class="error-container">
                  <h1>Loading Error</h1>
                  <p>Failed to load the application:</p>
                  <pre>Error Code: ${errorCode}\n${errorDescription}\n\nURL: ${validatedURL}</pre>
                  <p><strong>Possible solutions:</strong></p>
                  <ul>
                    <li>Check that all files are properly installed</li>
                    <li>Try restarting the application</li>
                    <li>Check the log files for more details</li>
                  </ul>
                  <button class="retry-btn" onclick="location.reload()">Retry</button>
                </div>
              </body>
              </html>
            `;
            mainWindow.loadURL(`data:text/html;charset=utf-8,${encodeURIComponent(errorHTML)}`);
          }
      });
    })
    .catch((err) => {
      log.error('Failed to start backend:', err);
      const errorHTML = `
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="UTF-8">
          <title>Bayport Veterinary Clinic - Error</title>
          <style>
            body {
              margin: 0;
              padding: 40px;
              background: #f6f9f9;
              font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            }
            .error-container {
              max-width: 600px;
              margin: 0 auto;
              background: white;
              padding: 30px;
              border-radius: 8px;
              box-shadow: 0 2px 8px rgba(0,0,0,0.1);
            }
            h1 { color: #dc2626; margin-top: 0; }
            pre { background: #f3f4f6; padding: 15px; border-radius: 4px; overflow-x: auto; white-space: pre-wrap; word-wrap: break-word; }
            .solution { background: #f0f9ff; padding: 15px; border-radius: 4px; margin-top: 15px; border-left: 4px solid #0057b8; }
            .solution h3 { margin-top: 0; color: #0057b8; }
          </style>
        </head>
        <body>
          <div class="error-container">
            <h1>⚠️ Backend Startup Error</h1>
            <p>Failed to start the backend server:</p>
            <pre>${err.message}</pre>
            <div class="solution">
              <h3>Solutions:</h3>
              <ol>
                <li><strong>Install Java 17+</strong><br>
                  Download from: <a href="https://adoptium.net/" target="_blank">https://adoptium.net/</a><br>
                  Choose "Temurin 17 LTS" and install it.
                </li>
                <li><strong>Check if Java is installed:</strong><br>
                  Open Command Prompt and type: <code>java -version</code><br>
                  You should see version 17 or higher.
                </li>
                <li><strong>Restart the application</strong> after installing Java.</li>
                <li><strong>Check port 8080:</strong> Make sure no other application is using port 8080.</li>
              </ol>
            </div>
            <p style="margin-top: 20px; color: #6b7280; font-size: 14px;">
              <strong>Note:</strong> This application requires Java 17 or higher to run. 
              The backend server must start before you can use the application.
            </p>
          </div>
        </body>
        </html>
      `;
      mainWindow.loadURL(`data:text/html;charset=utf-8,${encodeURIComponent(errorHTML)}`);
    });

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: 'deny' };
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

app.whenReady().then(() => {
  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});

app.on('window-all-closed', () => {
  // Kill backend process when app closes
  if (backendProcess) {
    log.info('Stopping backend process...');
    if (process.platform === 'win32') {
      spawn('taskkill', ['/pid', backendProcess.pid, '/F', '/T']);
    } else {
      backendProcess.kill('SIGTERM');
    }
    backendProcess = null;
  }
  
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

app.on('before-quit', () => {
  // Ensure backend is killed
  if (backendProcess) {
    if (process.platform === 'win32') {
      spawn('taskkill', ['/pid', backendProcess.pid, '/F', '/T']);
    } else {
      backendProcess.kill('SIGTERM');
    }
  }
});

ipcMain.handle('desktop:show-error', async (_evt, message) => {
  await dialog.showMessageBox({
    type: 'error',
    title: 'Bayport Desktop',
    message: message || 'Unknown error'
  });
  log.error(message);
});

ipcMain.handle('desktop:list-printers', async (evt) => {
  try {
    const win = BrowserWindow.fromWebContents(evt.sender);
    if (!win) return [];
    return await win.webContents.getPrintersAsync();
  } catch (err) {
    log.error('Unable to read printer list:', err);
    return [];
  }
});

ipcMain.handle('desktop:print-receipt', async (_evt, payload) => {
  const html = payload && typeof payload.html === 'string' ? payload.html : '';
  const requestedDeviceName = payload && typeof payload.deviceName === 'string' ? payload.deviceName : undefined;
  const silent = !!(payload && payload.silent);
  if (!html.trim()) {
    return { ok: false, error: 'Missing receipt content.' };
  }
  const printWindow = new BrowserWindow({
    show: false,
    width: 420,
    height: 900,
    webPreferences: {
      sandbox: true,
      contextIsolation: true,
      nodeIntegration: false
    }
  });
  try {
    await printWindow.loadURL(`data:text/html;charset=utf-8,${encodeURIComponent(html)}`);
    const printers = await printWindow.webContents.getPrintersAsync();
    const names = new Set((printers || []).map((p) => (p?.name || '').trim()).filter(Boolean));
    const autoPrinter = (printers || []).find((p) => p && p.isDefault) ||
      (printers || []).find((p) => /usb|thermal|receipt|pos|epson|tm-/i.test(String(p?.name || ''))) ||
      (printers || [])[0];
    const selectedDeviceName = requestedDeviceName && names.has(requestedDeviceName)
      ? requestedDeviceName
      : (autoPrinter?.name || undefined);
    const result = await new Promise((resolve) => {
      printWindow.webContents.print(
        {
          silent,
          printBackground: true,
          margins: { marginType: 'none' },
          pageSize: 'A4',
          deviceName: selectedDeviceName
        },
        (success, failureReason) => {
          if (success) resolve({ ok: true, deviceName: selectedDeviceName || null });
          else resolve({ ok: false, error: failureReason || 'Print failed.' });
        }
      );
    });
    return result;
  } catch (err) {
    log.error('Receipt print failed:', err);
    return { ok: false, error: err.message || 'Print failed.' };
  } finally {
    if (!printWindow.isDestroyed()) {
      printWindow.close();
    }
  }
});


