/**
 * ProjectView - Individual project workspace with tracks, waveform, and controls.
 *
 * Principles:
 * - Single Responsibility: Manages only the project workspace UI
 * - Reactive: Subscribes to state changes for live updates
 * - Resource Management: Proper cleanup via destroy() pattern
 * - Data Loading: Fetches project data from API on mount
 */

import { api } from '../api.js';
import { store } from '../state.js';
import { navigate, goBack } from '../router.js';

/**
 * Workspace view for an individual project with tracks, waveform, and controls.
 */
export class ProjectView {
    /**
     * Creates a new ProjectView instance.
     * @param {string} projectId - The project ID to display.
     */
    constructor(projectId) {
        this.projectId = projectId;
        this.element = null;
        this.unsubscribeProject = null;
        this.unsubscribeTracks = null;
        this.unsubscribeWaveform = null;
        this.unsubscribePlayback = null;
        this.projectData = null;
        this.tracks = [];
    }

    /**
     * Renders the project workspace view and binds events.
     * @param {string} projectId - Optional project ID override.
     * @returns {HTMLElement} The rendered view element.
     */
    render(projectId) {
        if (projectId) {
            this.projectId = projectId;
        }

        this.element = document.createElement('div');
        this.element.className = 'project-view';

        this.renderContent();
        this.bindEvents();
        this.subscribeToState();
        this.loadProjectData();

        return this.element;
    }

    /**
     * Renders the main content of the project view.
     * @private
     */
    renderContent() {
        this.element.innerHTML = `
            <div class="project-view-header">
                <div class="project-view-header-left">
                    <button id="btn-back" class="btn btn-secondary btn-back" title="Back to projects">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" width="16" height="16">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                  d="M15 19l-7-7 7-7"/>
                        </svg>
                        Back
                    </button>
                    <div class="project-view-title-group">
                        <h1 id="project-title" class="project-view-title">Loading...</h1>
                        <p id="project-artist" class="project-view-artist"></p>
                    </div>
                </div>
                <div class="project-view-header-right">
                    <button id="btn-add-track" class="btn btn-primary">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" width="14" height="14">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                  d="M12 4v16m8-8H4"/>
                        </svg>
                        Add Track
                    </button>
                    <button id="btn-upload-audio" class="btn">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" width="14" height="14">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                  d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12"/>
                        </svg>
                        Upload Audio
                    </button>
                    <input id="audio-file-input" type="file" accept="audio/*" style="display: none;">
                </div>
            </div>

            <div class="project-view-body">
                <div class="project-view-main">
                    <!-- Waveform Section -->
                    <div class="waveform-section card">
                        <div class="waveform-section-header">
                            <h3>Waveform</h3>
                            <div class="waveform-zoom-controls">
                                <span>Zoom</span>
                                <input id="waveform-zoom" type="range" min="1" max="10" value="1" class="range">
                            </div>
                        </div>
                        <div class="waveform-canvas-wrapper">
                            <canvas id="waveform-canvas"></canvas>
                        </div>
                        <div class="waveform-controls">
                            <button id="btn-play" class="transport-btn" title="Play">▶</button>
                            <button id="btn-pause" class="transport-btn" title="Pause">⏸</button>
                            <button id="btn-stop" class="transport-btn" title="Stop">⏹</button>
                            <input id="seek-bar" type="range" min="0" max="100" value="0" class="seek-bar">
                            <span id="time-display" class="time-display">0:00 / 0:00</span>
                        </div>
                    </div>

                    <!-- Tracks Section -->
                    <div class="tracks-section card">
                        <div class="tracks-section-header">
                            <h3>Tracks</h3>
                            <span id="track-count" class="badge badge-info">0 tracks</span>
                        </div>
                        <div id="tracks-list" class="tracks-list">
                            <!-- Tracks rendered here -->
                        </div>
                        <div id="tracks-empty" class="tracks-empty" style="display: none;">
                            <p class="empty-text">No tracks in this project. Add a track or upload audio.</p>
                        </div>
                    </div>
                </div>

                <!-- Analysis Panel -->
                <div class="project-view-sidebar">
                    <div class="analysis-section card">
                        <div class="analysis-section-header">
                            <h3>Analysis</h3>
                        </div>
                        <div id="analysis-content" class="analysis-content">
                            <div class="analysis-loading">
                                <p>Loading analysis data...</p>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }

    /**
     * Binds click event handlers to the view.
     * @private
     */
    bindEvents() {
        // Back navigation
        this.element?.querySelector('#btn-back')?.addEventListener('click', () => {
            goBack();
        });

        // Add track button
        this.element?.querySelector('#btn-add-track')?.addEventListener('click', () => {
            this.showAddTrackModal();
        });

        // Upload audio button
        this.element?.querySelector('#btn-upload-audio')?.addEventListener('click', () => {
            const fileInput = this.element?.querySelector('#audio-file-input');
            fileInput?.click();
        });

        // File input change
        this.element?.querySelector('#audio-file-input')?.addEventListener('change', (e) => {
            this.handleFileUpload(e);
        });

        // Transport controls
        this.element?.querySelector('#btn-play')?.addEventListener('click', () => {
            this.play();
        });

        this.element?.querySelector('#btn-pause')?.addEventListener('click', () => {
            this.pause();
        });

        this.element?.querySelector('#btn-stop')?.addEventListener('click', () => {
            this.stop();
        });

        // Seek bar
        this.element?.querySelector('#seek-bar')?.addEventListener('input', (e) => {
            const percent = e.target.value / 100;
            this.seek(percent);
        });

        // Waveform canvas click for seeking
        const canvas = this.element?.querySelector('#waveform-canvas');
        canvas?.addEventListener('click', (event) => {
            if (!this.waveformData || this.waveformData.isEmpty) return;

            const rect = canvas.getBoundingClientRect();
            const x = event.clientX - rect.left;
            const percent = x / rect.width;
            this.seek(percent);
        });

        // Waveform zoom
        this.element?.querySelector('#waveform-zoom')?.addEventListener('input', (e) => {
            const zoom = e.target.value;
            this.renderWaveform(zoom);
        });
    }

    /**
     * Subscribes to state changes for reactive updates.
     * @private
     */
    subscribeToState() {
        this.unsubscribeProject = store.subscribe('currentProject', (project) => {
            if (project?.id === this.projectId) {
                this.updateProjectTitle(project);
            }
        });

        this.unsubscribeTracks = store.subscribe('tracks', (tracks) => {
            this.updateTracksList(tracks);
        });

        this.unsubscribeWaveform = store.subscribe('waveform', (waveform) => {
            if (waveform && waveform.projectId === this.projectId) {
                this.waveformData = waveform;
                this.renderWaveform();
            }
        });

        this.unsubscribePlayback = store.subscribe('playback', (playback) => {
            this.updatePlaybackDisplay(playback);
        });
    }

    /**
     * Loads project data from the API.
     * @private
     */
    async loadProjectData() {
        try {
            // Load project details
            this.projectData = await api.getProject(this.projectId);
            store.set('currentProject', this.projectData);
            this.updateProjectTitle(this.projectData);

            // Load tracks
            this.tracks = await api.getProjectTracks(this.projectId);
            store.set('tracks', this.tracks);
            this.updateTracksList(this.tracks);

            // Load waveform for the main track
            const mainTrack = this.tracks.find(t => t.name === 'main') || this.tracks[0];
            if (mainTrack) {
                try {
                    const waveform = await api.getWaveform(this.projectId, mainTrack.id);
                    if (waveform) {
                        waveform.projectId = this.projectId;
                        store.set('waveform', waveform);
                        this.waveformData = waveform;
                    }
                } catch (error) {
                    console.warn('[ProjectView] No waveform data available:', error.message);
                }
            }

            // Load analysis
            try {
                const analysis = await api.getProjectAnalysis(this.projectId);
                store.set('analysis', analysis);
                this.updateAnalysisDisplay(analysis);
            } catch (error) {
                console.warn('[ProjectView] No analysis data available:', error.message);
            }
        } catch (error) {
            console.error('[ProjectView] Failed to load project data:', error);
            this.showError('Failed to load project data. Please try again.');
        }
    }

    /**
     * Updates the project title and artist display.
     * @param {object} project - Project data object.
     * @private
     */
    updateProjectTitle(project) {
        const titleEl = this.element?.querySelector('#project-title');
        const artistEl = this.element?.querySelector('#project-artist');

        if (titleEl) {
            titleEl.textContent = project?.title || 'Untitled Project';
        }
        if (artistEl) {
            artistEl.textContent = project?.artist ? `by ${project.artist}` : '';
        }
    }

    /**
     * Updates the tracks list display.
     * @param {Array} tracks - Array of track objects.
     * @private
     */
    updateTracksList(tracks) {
        this.tracks = tracks || [];
        const list = this.element?.querySelector('#tracks-list');
        const empty = this.element?.querySelector('#tracks-empty');
        const count = this.element?.querySelector('#track-count');

        if (!list || !empty) return;

        // Update count badge
        if (count) {
            count.textContent = `${tracks.length} track${tracks.length !== 1 ? 's' : ''}`;
        }

        if (tracks.length === 0) {
            list.style.display = 'none';
            empty.style.display = 'block';
            return;
        }

        list.style.display = 'flex';
        empty.style.display = 'none';

        list.innerHTML = tracks.map(track => `
            <div class="track-item" data-track-id="${track.id}">
                <div class="track-info">
                    <span class="track-name">${this.escapeHtml(track.name || 'Unnamed')}</span>
                    <span class="track-type badge">${this.escapeHtml(track.type || 'audio')}</span>
                </div>
                <div class="track-actions">
                    <button class="btn-icon btn-select-track" data-track-id="${track.id}" title="Select track">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" width="14" height="14">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                  d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"/>
                        </svg>
                    </button>
                    <button class="btn-icon btn-delete-track" data-track-id="${track.id}" title="Delete track">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" width="14" height="14">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                  d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/>
                        </svg>
                    </button>
                </div>
            </div>
        `).join('');

        this.bindTrackEvents();
    }

    /**
     * Binds events to track list items.
     * @private
     */
    bindTrackEvents() {
        // Select track buttons
        this.element?.querySelectorAll('.btn-select-track').forEach(btn => {
            btn.addEventListener('click', async () => {
                const trackId = btn.dataset.trackId;
                await this.selectTrack(trackId);
            });
        });

        // Delete track buttons
        this.element?.querySelectorAll('.btn-delete-track').forEach(btn => {
            btn.addEventListener('click', async () => {
                const trackId = btn.dataset.trackId;
                await this.deleteTrack(trackId);
            });
        });
    }

    /**
     * Updates the playback display (time, seek bar).
     * @param {object} playback - Playback state object.
     * @private
     */
    updatePlaybackDisplay(playback) {
        const timeDisplay = this.element?.querySelector('#time-display');
        const seekBar = this.element?.querySelector('#seek-bar');

        if (timeDisplay && playback) {
            timeDisplay.textContent = `${this.formatTime(playback.position)} / ${this.formatTime(playback.duration)}`;
        }

        if (seekBar && playback?.duration > 0) {
            seekBar.value = (playback.position / playback.duration) * 100;
        }
    }

    /**
     * Renders the waveform on the canvas.
     * @param {number} zoom - Zoom level (1-10).
     * @private
     */
    renderWaveform(zoom = 1) {
        const canvas = this.element?.querySelector('#waveform-canvas');
        if (!canvas || !this.waveformData) return;

        const ctx = canvas.getContext('2d');
        const dpr = window.devicePixelRatio || 1;
        const rect = canvas.parentElement.getBoundingClientRect();

        canvas.width = rect.width * dpr;
        canvas.height = 120 * dpr;
        canvas.style.width = rect.width + 'px';
        canvas.style.height = '120px';
        ctx.scale(dpr, dpr);

        const width = rect.width;
        const height = 120;
        const centerY = height / 2;
        const data = this.waveformData;

        ctx.clearRect(0, 0, width, height);

        if (data.isEmpty) {
            ctx.strokeStyle = 'rgba(255,255,255,0.2)';
            ctx.lineWidth = 1;
            ctx.beginPath();
            ctx.moveTo(0, centerY);
            ctx.lineTo(width, centerY);
            ctx.stroke();
            return;
        }

        // Apply zoom
        const samplesPerPixel = Math.max(1, Math.floor(data.leftChannel.length / (width * zoom)));
        const startSample = 0;
        const endSample = Math.min(data.leftChannel.length, startSample + width * samplesPerPixel);

        // Draw waveform
        for (let x = 0; x < width; x++) {
            const sampleStart = startSample + x * samplesPerPixel;
            const sampleEnd = Math.min(sampleStart + samplesPerPixel, endSample);

            let minVal = 0;
            let maxVal = 0;

            for (let i = sampleStart; i < sampleEnd; i++) {
                const leftVal = data.leftChannel[i] ?? 0;
                const rightVal = data.rightChannel?.[i] ?? leftVal;

                minVal = Math.min(minVal, leftVal, rightVal);
                maxVal = Math.max(maxVal, leftVal, rightVal);
            }

            const top = centerY + minVal * centerY * 0.9;
            const bottom = centerY + maxVal * centerY * 0.9;

            ctx.strokeStyle = '#e94560';
            ctx.lineWidth = 1;
            ctx.beginPath();
            ctx.moveTo(x, top);
            ctx.lineTo(x, bottom);
            ctx.stroke();
        }

        // Playback position indicator
        const playback = store.get('playback');
        if (playback?.duration > 0 && playback.position >= 0) {
            const posX = (playback.position / playback.duration) * width;
            ctx.strokeStyle = 'white';
            ctx.lineWidth = 2;
            ctx.beginPath();
            ctx.moveTo(posX, 0);
            ctx.lineTo(posX, height);
            ctx.stroke();
        }
    }

    /**
     * Updates the analysis display panel.
     * @param {object|null} analysis - Analysis data.
     * @private
     */
    updateAnalysisDisplay(analysis) {
        const container = this.element?.querySelector('#analysis-content');
        if (!container) return;

        if (!analysis) {
            container.innerHTML = '<p class="empty-text">No analysis data available.</p>';
            return;
        }

        let html = '<div class="analysis-data">';

        if (analysis.bpm) {
            html += `
                <div class="analysis-item">
                    <span class="analysis-label">BPM</span>
                    <span class="analysis-value">${analysis.bpm}</span>
                </div>
            `;
        }

        if (analysis.key) {
            html += `
                <div class="analysis-item">
                    <span class="analysis-label">Key</span>
                    <span class="analysis-value">${analysis.key}</span>
                </div>
            `;
        }

        if (analysis.duration) {
            html += `
                <div class="analysis-item">
                    <span class="analysis-label">Duration</span>
                    <span class="analysis-value">${this.formatTime(analysis.duration)}</span>
                </div>
            `;
        }

        if (analysis.tracks) {
            html += `
                <div class="analysis-item">
                    <span class="analysis-label">Tracks</span>
                    <span class="analysis-value">${analysis.tracks.length}</span>
                </div>
            `;
        }

        html += '</div>';
        container.innerHTML = html;
    }

    /**
     * Shows the add track modal.
     * @private
     */
    showAddTrackModal() {
        const trackName = prompt('Enter track name:');
        if (!trackName) return;

        this.createTrack(trackName);
    }

    /**
     * Creates a new track for the project.
     * @param {string} name - Track name.
     * @private
     */
    async createTrack(name) {
        try {
            const track = await api.createTrack(this.projectId, {
                name,
                type: 'audio'
            });

            const tracks = store.get('tracks') || [];
            tracks.push(track);
            store.set('tracks', tracks);
        } catch (error) {
            console.error('[ProjectView] Failed to create track:', error);
            alert('Failed to create track: ' + error.message);
        }
    }

    /**
     * Deletes a track from the project.
     * @param {string} trackId - Track ID to delete.
     * @private
     */
    async deleteTrack(trackId) {
        if (!confirm('Are you sure you want to delete this track?')) {
            return;
        }

        try {
            await api.deleteTrack(this.projectId, trackId);

            const tracks = store.get('tracks') || [];
            const filtered = tracks.filter(t => t.id !== trackId);
            store.set('tracks', filtered);
        } catch (error) {
            console.error('[ProjectView] Failed to delete track:', error);
            alert('Failed to delete track: ' + error.message);
        }
    }

    /**
     * Selects a track for playback.
     * @param {string} trackId - Track ID to select.
     * @private
     */
    async selectTrack(trackId) {
        try {
            store.set('currentTrack', trackId);

            // Load waveform
            const waveform = await api.getWaveform(this.projectId, trackId);
            if (waveform) {
                waveform.projectId = this.projectId;
                store.set('waveform', waveform);
                this.waveformData = waveform;
                this.renderWaveform();
            }

            // Update audio source
            const audioUrl = api.getAudioURL(this.projectId, trackId);
            if (window.audioPlayer) {
                window.audioPlayer.setSource(audioUrl);
            }
        } catch (error) {
            console.error('[ProjectView] Failed to select track:', error);
        }
    }

    /**
     * Handles audio file upload.
     * @param {Event} event - File input change event.
     * @private
     */
    async handleFileUpload(event) {
        const file = event.target.files?.[0];
        if (!file) return;

        const tracks = store.get('tracks') || [];
        const trackId = tracks[0]?.id || null;

        try {
            await api.uploadAudio(this.projectId, file, trackId);
            alert('Audio file uploaded successfully!');

            // Reload project data
            await this.loadProjectData();
        } catch (error) {
            console.error('[ProjectView] Failed to upload audio:', error);
            alert('Failed to upload audio: ' + error.message);
        }

        // Reset file input
        event.target.value = '';
    }

    /**
     * Starts playback.
     * @private
     */
    play() {
        if (window.audioPlayer) {
            window.audioPlayer.play();
        }
    }

    /**
     * Pauses playback.
     * @private
     */
    pause() {
        if (window.audioPlayer) {
            window.audioPlayer.pause();
        }
    }

    /**
     * Stops playback.
     * @private
     */
    stop() {
        if (window.audioPlayer) {
            window.audioPlayer.stop();
        }
    }

    /**
     * Seeks to a position in the audio.
     * @param {number} percent - Position as fraction (0.0 to 1.0).
     * @private
     */
    seek(percent) {
        if (window.audioPlayer) {
            window.audioPlayer.seek(percent);
        }
    }

    /**
     * Shows an error message in the view.
     * @param {string} message - Error message to display.
     * @private
     */
    showError(message) {
        const body = this.element?.querySelector('.project-view-body');
        if (body) {
            body.innerHTML = `
                <div class="error-message">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" width="48" height="48">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                              d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"/>
                    </svg>
                    <h2>Error</h2>
                    <p>${this.escapeHtml(message)}</p>
                    <button id="btn-retry" class="btn btn-primary">Retry</button>
                </div>
            `;

            this.element?.querySelector('#btn-retry')?.addEventListener('click', () => {
                this.loadProjectData();
            });
        }
    }

    /**
     * Escapes HTML to prevent XSS attacks.
     * @param {string} text - Text to escape.
     * @returns {string} Escaped text.
     * @private
     */
    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    /**
     * Formats seconds to mm:ss display.
     * @param {number} seconds - Time in seconds.
     * @returns {string} Formatted time string.
     * @private
     */
    formatTime(seconds) {
        if (!seconds || isNaN(seconds)) return '0:00';
        const mins = Math.floor(seconds / 60);
        const secs = Math.floor(seconds % 60);
        return `${mins}:${secs.toString().padStart(2, '0')}`;
    }

    /**
     * Cleans up event listeners and subscriptions.
     */
    destroy() {
        this.unsubscribeProject?.();
        this.unsubscribeTracks?.();
        this.unsubscribeWaveform?.();
        this.unsubscribePlayback?.();
        this.element = null;
        this.projectData = null;
        this.waveformData = null;
    }
}
