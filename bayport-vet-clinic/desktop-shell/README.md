# Bayport Desktop Shell

This directory contains a lightweight Electron wrapper that loads the Bayport Veterinary Clinic frontend and talks to the Spring Boot backend running on `http://localhost:8080`.

## Prerequisites

1. Node.js 18+ and npm.
2. Java 17 + Maven (for the backend).
3. MySQL instance configured the same way as the web version.

## Development workflow

```powershell
# Build the Spring Boot backend + start Electron (one command)
npm install
npm run desktop
```

The `desktop` script compiles the backend JAR (via the bundled Maven wrapper) and launches Electron pointed at `index.html` from the parent folder. All HTTP calls keep using `http://localhost:8080` (set via `window.API_BASE` inside the HTML pages), so adjust that inline script if your backend runs elsewhere.

## Packaging

Windows installers:

```
npm run package
```

macOS installers (run this on macOS to generate universal Intel + Apple Silicon DMG/ZIP artifacts):

```
npm run dist:mac
```

Cross-platform builds (run on macOS for mac + win + linux artifacts):

```
npm run dist:all
```

All packages are emitted under `desktop-shell/dist`. Adjust the `build` section of `package.json` if you need different targets or metadata.

