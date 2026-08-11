# Refactoring Plan: Kotlin Multiplatform → Web UI

## Executive Summary

This plan outlines the migration from a **Kotlin Multiplatform + Jetpack Compose Desktop** application to a **Kotlin Server + Vanilla Web UI** architecture. The server module already has a functional web UI foundation that we will enhance and complete.

---

## Current Architecture

```
ai-music-workstation/
├── ui/                       ← MULTIPLATFORM + COMPOSE (TO BE REMOVED)
│   ├── commonMain/           ← Compose UI components + ViewModels
│   └── desktopMain/          ← Desktop entry point
├── server/                   ← HTTP Server + Basic Web UI (KEEP & ENHANCE)
├── worker-client/            ← Worker IPC client (KEEP, simplify)
├── shared/                   ← Models & utilities (KEEP, simplify)
├── cli/                      ← CLI tool (KEEP)
├── app/                      ← Legacy app (TO BE REMOVED)
└── worker/                   ← Python worker (KEEP, unchanged)
```

---

## Target Architecture

```
ai-music-workstation/
├── server/                   ← HTTP Server (Ktor or stdlib)
│   ├── src/main/kotlin/      ← Server code (APIs, services)
│   └── src/main/resources/
│       └── web/              ← Web UI (HTML, CSS, JS)
├── worker-client/            ← Simplified (JVM only)
├── shared/                   ← Simplified (JVM only)
├── cli/                      ← CLI tool (unchanged)
└── worker/                   ← Python worker (unchanged)
```

---

## Phase 1: Foundation & Cleanup (Week 1)

### 1.1 Remove Multiplatform from Build Configuration

**Files to modify:**
- `build.gradle.kts` (root)
- `settings.gradle.kts`
- `ui/build.gradle.kts`
- `shared/build.gradle.kts`
- `worker-client/build.gradle.kts`

**Actions:**
```kotlin
// build.gradle.kts - Remove multiplatform plugins
plugins {
    kotlin("jvm") version "2.0.0" apply false
    kotlin("plugin.serialization") version "2.0.0" apply false
}

// settings.gradle.kts - Remove ui project
// include("ui")  <-- DELETE THIS LINE
```

### 1.2 Remove Unused Modules

**Directories to remove:**
- `ui/` - Entire Compose UI module
- `app/` - Legacy app module (redundant with server)

**Gradle tasks to clean:**
```bash
./gradlew clean
git rm -r ui/
git rm -r app/
```

### 1.3 Simplify Shared Module

**Changes:**
- Convert `shared/build.gradle.kts` from multiplatform to pure JVM
- Remove any `expect/actual` declarations
- Ensure all models are serializable with kotlinx.serialization

**Before:**
```kotlin
kotlin {
    jvm()
    sourceSets {
        named("commonMain") { ... }
        named("jvmMain") { ... }
    }
}
```

**After:**
```kotlin
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(21)
}
```

### 1.4 Simplify Worker-Client Module

**Changes:**
- Convert from multiplatform to pure JVM
- The `WorkerClient` becomes a simple HTTP client to the Python worker

**Before:**
```kotlin
kotlin {
    jvm()
    sourceSets {
        named("commonMain") { ... }
    }
}
```

**After:**
```kotlin
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(21)
}
```

---

## Phase 2: Server Enhancement (Week 2)

### 2.1 Upgrade Server Framework

**Option A: Keep stdlib HttpServer (minimal, no dependencies)**
- Current approach works, just enhance

**Option B: Use Ktor (recommended for production)**
```kotlin
dependencies {
    implementation("io.ktor:ktor-server-core:2.3.0")
    implementation("io.ktor:ktor-server-netty:2.3.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.0")
    implementation("io.ktor:ktor-server-cors:2.3.0")
    implementation("io.ktor:ktor-server-static-content:2.3.0")
}
```

### 2.2 Enhance API Endpoints

**Current endpoints (from `Server.kt`):**
```
GET    /health
GET    /api/projects
POST   /api/projects
GET    /api/projects/{id}
PUT    /api/projects/{id}
DELETE /api/projects/{id}
POST   /api/audio/upload
GET    /api/audio/{projectId}/{trackId}
POST   /api/audio/{projectId}/{trackId}/export
GET    /api/audio/{projectId}/{trackId}/waveform
POST   /api/worker/start
POST   /api/worker/stop
GET    /api/worker/health
POST   /api/worker/command
GET    /api/worker/job/{jobId}
GET    /api/worker/job/{jobId}/progress  (SSE)
GET    /api/worker/jobs
```

**Add missing endpoints:**
```
GET    /api/projects/{id}/tracks          # List tracks
POST   /api/projects/{id}/tracks          # Add track
DELETE /api/projects/{id}/tracks/{trackId}
GET    /api/projects/{id}/analysis        # Get analysis results
GET    /api/projects/{id}/provenance      # Get provenance log
GET    /api/config                        # Get server config
PUT    /api/config                        # Update server config
```

### 2.3 Implement SSE for Real-time Updates

The existing web UI uses `EventSource` for job progress. Ensure the server supports Server-Sent Events:

```kotlin
// Server.kt - Add SSE endpoint
javaServer!!.createContext("/api/worker/job/{id}/progress") { exchange ->
    val jobId = extractId(exchange.requestURI.path)
    workerService.subscribeToProgress(jobId) { progressData ->
        exchange.sendResponseHeaders(200, -1)
        exchange.responseBody.use { out ->
            val sseLine = "data: ${Json.encodeToString(progressData)}\n\n"
            out.write(sseLine.toByteArray(Charsets.UTF_8))
            out.flush()
        }
    }
    exchange.close()
}
```

---

## Phase 3: Web UI Refactoring (Week 3-4)

### 3.1 Web UI Architecture

**Technology choice: Vanilla JS with ES Modules** (no framework needed for this scope)

**Directory structure:**
```
server/src/main/resources/web/
├── index.html
├── css/
│   ├── main.css           # Base styles & CSS variables
│   ├── layout.css         # Grid/flex layouts
│   ├── components.css     # Button, input, card styles
│   └── waveform.css       # Waveform-specific styles
├── js/
│   ├── api.js             # API client (fetch wrapper)
│   ├── state.js           # Global state management (Store pattern)
│   ├── router.js          # Simple client-side routing
│   ├── components/
│   │   ├── header.js      # App header
│   │   ├── sidebar.js     # Project list
│   │   ├── waveform.js    # Waveform renderer (Canvas)
│   │   ├── tracks.js      # Track management
│   │   ├── worker-panel.js # AI operations
│   │   └── modals.js      # Modal dialogs
│   ├── views/
│   │   ├── project-view.js    # Project workspace
│   │   └── projects-view.js   # Project list view
│   └── app.js             # Application entry point
└── assets/
    └── icons/             # SVG icons
```

### 3.2 State Management (Store Pattern)

Replace the 14 ViewModels with a single centralized store:

```javascript
// js/state.js
class Store {
    #state = {
        projects: [],
        currentProject: null,
        currentTrack: null,
        waveform: null,
        playback: {
            isPlaying: false,
            position: 0,
            duration: 0
        },
        worker: {
            isRunning: false,
            currentJob: null,
            progress: 0,
            jobs: []
        },
        analysis: null,
        ui: {
            activeTab: 'analyze',
            sidebarOpen: true
        }
    };

    #listeners = new Map();

    get(key) {
        return this.#state[key];
    }

    set(key, value) {
        this.#state[key] = value;
        this.#notify(key);
    }

    subscribe(key, callback) {
        if (!this.#listeners.has(key)) {
            this.#listeners.set(key, new Set());
        }
        this.#listeners.get(key).add(callback);
        return () => this.#listeners.get(key).delete(callback);
    }

    #notify(key) {
        const listeners = this.#listeners.get(key);
        if (listeners) {
            listeners.forEach(cb => cb(this.#state[key]));
        }
    }
}

export const store = new Store();
```

### 3.3 Component Architecture

Each component follows this pattern:

```javascript
// js/components/header.js
import { store } from '../state.js';
import { api } from '../api.js';

export class Header {
    constructor() {
        this.element = null;
        this.unsubscribe = null;
    }

    render() {
        this.element = document.createElement('header');
        this.element.className = 'app-header';
        this.element.innerHTML = `
            <div class="header-left">
                <h1>AI Music Workstation</h1>
            </div>
            <nav class="header-nav">
                <button id="btn-new-project" class="btn btn-primary">New Project</button>
                <button id="btn-worker-start" class="btn">Start Worker</button>
                <button id="btn-worker-stop" class="btn">Stop Worker</button>
                <span id="worker-status" class="status-badge">Worker: Stopped</span>
            </nav>
        `;
        this.bindEvents();
        this.subscribeToState();
        return this.element;
    }

    bindEvents() {
        this.element.querySelector('#btn-new-project')
            .addEventListener('click', () => this.handleNewProject());
        // ... more event bindings
    }

    subscribeToState() {
        this.unsubscribe = store.subscribe('worker', (worker) => {
            const badge = this.element.querySelector('#worker-status');
            badge.textContent = `Worker: ${worker.isRunning ? 'Running' : 'Stopped'}`;
            badge.className = `status-badge ${worker.isRunning ? 'healthy' : ''}`;
        });
    }

    destroy() {
        this.unsubscribe?.();
    }

    async handleNewProject() {
        // Show modal, create project, etc.
    }
}
```

### 3.4 Waveform Renderer

Replace the Compose `Canvas` with HTML5 Canvas:

```javascript
// js/components/waveform.js
export class WaveformRenderer {
    constructor(canvas, data, playbackPosition, onSeek) {
        this.canvas = canvas;
        this.ctx = canvas.getContext('2d');
        this.data = data;
        this.playbackPosition = playbackPosition;
        this.onSeek = onSeek;
        this.resize();
    }

    resize() {
        const rect = this.canvas.parentElement.getBoundingClientRect();
        this.canvas.width = rect.width * window.devicePixelRatio;
        this.canvas.height = 120 * window.devicePixelRatio;
        this.ctx.scale(window.devicePixelRatio, window.devicePixelRatio);
        this.width = rect.width;
        this.height = 120;
        this.draw();
    }

    draw() {
        const ctx = this.ctx;
        const { width, height } = this;
        const centerY = height / 2;

        ctx.clearRect(0, 0, width, height);

        if (!this.data || this.data.isEmpty) {
            // Draw empty state
            ctx.strokeStyle = 'rgba(255,255,255,0.3)';
            ctx.beginPath();
            ctx.moveTo(0, centerY);
            ctx.lineTo(width, centerY);
            ctx.stroke();
            return;
        }

        const sampleWidth = width / this.data.leftChannel.length;

        // Draw waveform
        for (let i = 0; i < this.data.leftChannel.length; i++) {
            const x = i * sampleWidth;
            const top = centerY + this.data.leftChannel[i] * centerY;
            const bottom = centerY + (this.data.rightChannel?.[i] ?? this.data.leftChannel[i]) * centerY;

            ctx.strokeStyle = '#e94560';
            ctx.beginPath();
            ctx.moveTo(x, top);
            ctx.lineTo(x + sampleWidth, bottom);
            ctx.stroke();
        }

        // Draw playback position
        if (this.data.duration > 0 && this.playbackPosition >= 0) {
            const posX = (this.playbackPosition / this.data.duration) * width;
            ctx.strokeStyle = 'white';
            ctx.lineWidth = 2;
            ctx.beginPath();
            ctx.moveTo(posX, 0);
            ctx.lineTo(posX, height);
            ctx.stroke();
        }
    }

    update(data, playbackPosition) {
        this.data = data;
        this.playbackPosition = playbackPosition;
        this.draw();
    }
}
```

### 3.5 Audio Player

```javascript
// js/components/audio-player.js
export class AudioPlayer {
    constructor() {
        this.audio = new Audio();
        this.isPlaying = false;
        this.onTimeUpdate = null;
        this.onEnded = null;
    }

    setSource(url) {
        this.audio.src = url;
        this.audio.load();
    }

    play() {
        this.audio.play();
        this.isPlaying = true;
        this.scheduleUpdate();
    }

    pause() {
        this.audio.pause();
        this.isPlaying = false;
    }

    stop() {
        this.audio.pause();
        this.audio.currentTime = 0;
        this.isPlaying = false;
    }

    seek(percent) {
        this.audio.currentTime = percent * this.audio.duration;
    }

    get position() { return this.audio.currentTime; }
    get duration() { return this.audio.duration; }

    scheduleUpdate() {
        if (!this.isPlaying) return;
        if (this.onTimeUpdate) this.onTimeUpdate(this.position, this.duration);
        requestAnimationFrame(() => this.scheduleUpdate());
    }
}
```

---

## Phase 4: Data Model Migration (Week 5)

### 4.1 Model Mapping

| Kotlin (shared/) | JavaScript (web/) | Notes |
|-----------------|-------------------|-------|
| `Project` | `Project` | Same structure, no serialization needed |
| `ProjectDTO` | (merged into Project) | DTOs not needed in frontend |
| `Job` | `Job` | Same structure |
| `JobStatus` | `JobStatus` enum | Same values |
| `WaveformDTO` | `WaveformData` | Simplified for JS |
| `SongAnalysis` | `Analysis` | Same structure |
| `DSPSettings` | `DSPSettings` | Same structure |

### 4.2 API Client Enhancement

```javascript
// js/api.js
const API_BASE = '/api';

export const api = {
    // Projects
    async getProjects() {
        return this.request('GET', '/projects');
    },

    async createProject(title, artist) {
        return this.request('POST', '/projects', { title, artist });
    },

    async getProject(id) {
        return this.request('GET', `/projects/${id}`);
    },

    async updateProject(id, updates) {
        return this.request('PUT', `/projects/${id}`, updates);
    },

    async deleteProject(id) {
        return this.request('DELETE', `/projects/${id}`);
    },

    // Audio
    async uploadAudio(projectId, file) {
        const formData = new FormData();
        formData.append('file', file);
        const response = await fetch(`${API_BASE}/audio/${projectId}/upload`, {
            method: 'POST',
            body: formData
        });
        if (!response.ok) throw new Error('Upload failed');
        return response.json();
    },

    async getWaveform(projectId, trackId) {
        try {
            return await this.request('GET', `/audio/${projectId}/${trackId}/waveform`);
        } catch {
            return null;
        }
    },

    getAudioURL(projectId, trackId) {
        return `${API_BASE}/audio/${projectId}/${trackId}`;
    },

    // Worker
    async startWorker() {
        return this.request('POST', '/worker/start');
    },

    async stopWorker() {
        return this.request('POST', '/worker/stop');
    },

    async getWorkerHealth() {
        return this.request('GET', '/worker/health');
    },

    async submitCommand(command, params) {
        return this.request('POST', '/worker/command', { commandType: command, params });
    },

    // SSE
    onJobProgress(jobId, onProgress, onComplete, onError) {
        const eventSource = new EventSource(`${API_BASE}/worker/job/${jobId}/progress`);

        eventSource.onmessage = (event) => {
            const data = JSON.parse(event.data);
            if (data.error) onError(data.error);
            else onProgress(data);
        };

        eventSource.onerror = () => {
            eventSource.close();
            onError('Connection lost');
        };

        return () => eventSource.close();
    },

    // Helpers
    async request(method, path, body = null) {
        const options = {
            method,
            headers: { 'Content-Type': 'application/json' }
        };
        if (body) options.body = JSON.stringify(body);

        const response = await fetch(`${API_BASE}${path}`, options);
        if (!response.ok) {
            const error = await response.json().catch(() => ({ error: response.statusText }));
            throw new Error(error.error || 'Request failed');
        }
        if (response.status === 204) return null;
        return response.json();
    }
};
```

---

## Phase 5: Testing & Migration (Week 6)

### 5.1 Keep Existing Tests

- `shared/src/commonTest/` → `shared/src/test/` (JVM tests)
- `worker-client/src/commonTest/` → `worker-client/src/test/`

### 5.2 Add Integration Tests

```kotlin
// server/src/test/kotlin/...
class ServerIntegrationTest {
    @Test
    fun `test project CRUD`() {
        // Test project endpoints
    }

    @Test
    fun `test worker command submission`() {
        // Test worker command flow
    }
}
```

### 5.3 Migration Checklist

- [ ] Remove `ui/` directory
- [ ] Remove `app/` directory
- [ ] Update `settings.gradle.kts`
- [ ] Update root `build.gradle.kts`
- [ ] Convert `shared/` to JVM-only
- [ ] Convert `worker-client/` to JVM-only
- [ ] Enhance `server/` APIs
- [ ] Complete web UI (index.html + all JS modules)
- [ ] Test full workflow end-to-end
- [ ] Update README.md

---

## Estimated Timeline

| Phase | Duration | Deliverable |
|-------|----------|-------------|
| 1. Foundation & Cleanup | 1 week | Multiplatform removed, clean build |
| 2. Server Enhancement | 1 week | Complete API with SSE |
| 3. Web UI Refactoring | 2 weeks | Functional web UI |
| 4. Data Model Migration | 1 week | Clean data flow |
| 5. Testing & Migration | 1 week | All tests passing |
| **Total** | **6 weeks** | **Production-ready web app** |

---

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Breaking existing projects | Server maintains backward-compatible project format |
| Worker communication changes | Worker-client interface stays the same |
| Lost functionality during migration | Document all Compose UI features before removal |
| Performance regression | Canvas-based waveform is already efficient |

---

## Post-Refactor Project Structure

```
ai-music-workstation/
├── build.gradle.kts          # JVM-only build
├── settings.gradle.kts       # 4 modules
├── shared/                   # Models, config, utilities (JVM)
│   └── src/
│       ├── main/kotlin/
│       └── test/kotlin/
├── worker-client/            # Worker IPC (JVM)
│   └── src/
│       ├── main/kotlin/
│       └── test/kotlin/
├── server/                   # HTTP Server + Web UI
│   ├── src/
│   │   ├── main/kotlin/
│   │   │   ├── api/          # REST endpoints
│   │   │   ├── service/      # Business logic
│   │   │   ├── config/       # Server configuration
│   │   │   └── dto/          # Data transfer objects
│   │   └── main/resources/
│   │       └── web/          # Web UI (HTML/CSS/JS)
│   └── build.gradle.kts
├── cli/                      # CLI tool (unchanged)
├── worker/                   # Python worker (unchanged)
└── README.md                 # Updated documentation
```
