// js/api.js - Comprehensive API client wrapper
// Wraps all server endpoints with async/await methods and proper error handling.

const API_BASE = '/api';

/**
 * Custom error class for API errors with structured information.
 */
export class ApiError extends Error {
    /**
     * @param {string} message - Human-readable error message.
     * @param {number} status - HTTP status code.
     * @param {string} endpoint - The API endpoint that failed.
     */
    constructor(message, status, endpoint) {
        super(message);
        this.name = 'ApiError';
        this.status = status;
        this.endpoint = endpoint;
    }
}

/**
 * Parses JSON response body, handling empty or malformed responses.
 * @param {Response} response - Fetch response object.
 * @returns {Promise<any>} Parsed JSON data.
 * @private
 */
async function parseJsonResponse(response) {
    const contentType = response.headers.get('content-type');
    if (contentType && contentType.includes('application/json')) {
        try {
            return await response.json();
        } catch {
            return {};
        }
    }
    return {};
}

/**
 * Core fetch wrapper with unified error handling.
 * @param {string} method - HTTP method (GET, POST, PUT, DELETE).
 * @param {string} path - API path (without base URL).
 * @param {object|null} body - Request body (will be JSON-stringified).
 * @returns {Promise<any|null>} Response data or null for 204.
 * @private
 */
async function request(method, path, body = null) {
    const url = `${API_BASE}${path}`;
    const options = {
        method,
        headers: { 'Content-Type': 'application/json' }
    };

    if (body !== null && body !== undefined) {
        options.body = JSON.stringify(body);
    }

    let response;
    try {
        response = await fetch(url, options);
    } catch (error) {
        throw new ApiError(`Network error: ${error.message}`, 0, url);
    }

    // Handle success cases
    if (response.status === 204 || response.status === 205) {
        return null;
    }

    if (!response.ok) {
        const data = await parseJsonResponse(response);
        const message = data.error || response.statusText || `Request failed with status ${response.status}`;
        throw new ApiError(message, response.status, url);
    }

    return parseJsonResponse(response);
}

/**
 * SSE (Server-Sent Events) subscription manager.
 * Returns a cleanup function to close the connection.
 */
export class SseSubscription {
    /**
     * @param {string} url - EventSource URL.
     * @param {Function} onProgress - Called for each progress event.
     * @param {Function} onComplete - Called when job completes successfully.
     * @param {Function} onError - Called on error or completion failure.
     */
    constructor(url, onProgress, onComplete, onError) {
        this._eventSource = new EventSource(url);
        this._onProgress = onProgress;
        this._onComplete = onComplete;
        this._onError = onError;
        this._closed = false;

        this._setupListeners();
    }

    _setupListeners() {
        this._eventSource.onmessage = (event) => {
            if (this._closed) return;

            try {
                const data = JSON.parse(event.data);
                if (data.error) {
                    this._onError(data.error);
                } else if (data.status === 'completed' || data.status === 'failed') {
                    this._onComplete(data);
                } else {
                    this._onProgress(data);
                }
            } catch (error) {
                console.error('[SSE] Failed to parse event data:', error);
                this._onError('Failed to parse server response');
            }
        };

        this._eventSource.onerror = () => {
            if (this._closed) return;
            this._eventSource.close();
            this._closed = true;
            this._onError('Connection lost');
        };
    }

    /**
     * Closes the SSE connection and cleans up.
     */
    close() {
        if (this._closed) return;
        this._closed = true;
        this._eventSource.close();
    }
}

/**
 * Comprehensive API client wrapping all server endpoints.
 * Provides async/await methods with proper error handling.
 */
export const api = {
    // ==================== Project Endpoints ====================

    /**
     * List all projects.
     * @returns {Promise<Array>} Array of project objects.
     */
    async getProjects() {
        return request('GET', '/projects');
    },

    /**
     * Create a new project.
     * @param {string} title - Project title.
     * @param {string} artist - Project artist.
     * @returns {Promise<object>} Created project object.
     */
    async createProject(title, artist) {
        return request('POST', '/projects', { title, artist });
    },

    /**
     * Get a single project by ID.
     * @param {string} id - Project ID.
     * @returns {Promise<object>} Project object.
     */
    async getProject(id) {
        return request('GET', `/projects/${encodeURIComponent(id)}`);
    },

    /**
     * Update an existing project.
     * @param {string} id - Project ID.
     * @param {object} updates - Fields to update (title, artist).
     * @returns {Promise<object>} Updated project object.
     */
    async updateProject(id, updates) {
        return request('PUT', `/projects/${encodeURIComponent(id)}`, updates);
    },

    /**
     * Delete a project by ID.
     * @param {string} id - Project ID.
     * @returns {Promise<null>} Null on success.
     */
    async deleteProject(id) {
        return request('DELETE', `/projects/${encodeURIComponent(id)}`);
    },

    // ==================== Track Endpoints ====================

    /**
     * List tracks in a project.
     * @param {string} projectId - Project ID.
     * @returns {Promise<Array>} Array of track objects.
     */
    async getProjectTracks(projectId) {
        return request('GET', `/projects/${encodeURIComponent(projectId)}/tracks`);
    },

    /**
     * Add a new track to a project.
     * @param {string} projectId - Project ID.
     * @param {object} trackData - Track data (name, type, filePath, etc.).
     * @returns {Promise<object>} Created track object.
     */
    async createTrack(projectId, trackData) {
        return request('POST', `/projects/${encodeURIComponent(projectId)}/tracks`, trackData);
    },

    /**
     * Remove a track from a project.
     * @param {string} projectId - Project ID.
     * @param {string} trackId - Track ID.
     * @returns {Promise<null>} Null on success.
     */
    async deleteTrack(projectId, trackId) {
        return request('DELETE', `/projects/${encodeURIComponent(projectId)}/tracks/${encodeURIComponent(trackId)}`);
    },

    // ==================== Analysis & Provenance Endpoints ====================

    /**
     * Get analysis results for a project.
     * @param {string} projectId - Project ID.
     * @returns {Promise<object>} Analysis data (bpm, key, tracks).
     */
    async getProjectAnalysis(projectId) {
        return request('GET', `/projects/${encodeURIComponent(projectId)}/analysis`);
    },

    /**
     * Get provenance log for a project.
     * @param {string} projectId - Project ID.
     * @returns {Promise<Array>} Provenance entries.
     */
    async getProjectProvenance(projectId) {
        return request('GET', `/projects/${encodeURIComponent(projectId)}/provenance`);
    },

    // ==================== Audio Endpoints ====================

    /**
     * Upload an audio file to a project.
     * @param {string} projectId - Project ID.
     * @param {File} file - Audio file to upload.
     * @param {string} trackId - Target track ID.
     * @returns {Promise<object>} Upload result.
     */
    async uploadAudio(projectId, file, trackId = null) {
        const formData = new FormData();
        formData.append('file', file);
        if (trackId) {
            formData.append('trackId', trackId);
        }

        const url = `${API_BASE}/audio/upload`;
        let response;
        try {
            response = await fetch(url, {
                method: 'POST',
                body: formData
            });
        } catch (error) {
            throw new ApiError(`Network error: ${error.message}`, 0, url);
        }

        if (!response.ok) {
            const data = await parseJsonResponse(response);
            const message = data.error || response.statusText || `Upload failed with status ${response.status}`;
            throw new ApiError(message, response.status, url);
        }

        return parseJsonResponse(response);
    },

    /**
     * Get waveform data for a track.
     * @param {string} projectId - Project ID.
     * @param {string} trackId - Track ID.
     * @returns {Promise<object|null>} Waveform data or null if not found.
     */
    async getWaveform(projectId, trackId) {
        try {
            return await request('GET', `/audio/${encodeURIComponent(projectId)}/${encodeURIComponent(trackId)}/waveform`);
        } catch (error) {
            // 404 means no waveform data available
            if (error instanceof ApiError && error.status === 404) {
                return null;
            }
            throw error;
        }
    },

    /**
     * Get the audio stream URL for a track.
     * @param {string} projectId - Project ID.
     * @param {string} trackId - Track ID.
     * @returns {string} Audio stream URL.
     */
    getAudioURL(projectId, trackId) {
        return `${API_BASE}/audio/${encodeURIComponent(projectId)}/${encodeURIComponent(trackId)}`;
    },

    // ==================== Worker Endpoints ====================

    /**
     * Start the AI worker.
     * @returns {Promise<null>} Null on success.
     */
    async startWorker() {
        return request('POST', '/worker/start');
    },

    /**
     * Stop the AI worker.
     * @returns {Promise<null>} Null on success.
     */
    async stopWorker() {
        return request('POST', '/worker/stop');
    },

    /**
     * Check worker health status.
     * @returns {Promise<object>} Worker health info.
     */
    async getWorkerHealth() {
        return request('GET', '/worker/health');
    },

    /**
     * Submit a command to the worker.
     * @param {string} command - Command type (analyze, apply_dsp, repair, master).
     * @param {object} params - Command parameters.
     * @returns {Promise<object>} Job info with jobId.
     */
    async submitCommand(command, params = {}) {
        return request('POST', '/worker/command', { commandType: command, params });
    },

    /**
     * Get the status of a specific job.
     * @param {string} jobId - Job ID.
     * @returns {Promise<object>} Job status info.
     */
    async getJobStatus(jobId) {
        return request('GET', `/worker/job/${encodeURIComponent(jobId)}`);
    },

    /**
     * Get recent jobs list.
     * @returns {Promise<Array>} Array of recent job objects.
     */
    async getRecentJobs() {
        return request('GET', '/worker/jobs');
    },

    // ==================== SSE Endpoints ====================

    /**
     * Subscribe to job progress events via SSE.
     * Returns a cleanup function to close the connection.
     *
     * @param {string} jobId - Job ID to subscribe to.
     * @param {Function} onProgress - Called with progress data (progress, status).
     * @param {Function} onComplete - Called when job completes successfully.
     * @param {Function} onError - Called on error or failure.
     * @returns {Function} Cleanup function to close the SSE connection.
     */
    onJobProgress(jobId, onProgress, onComplete, onError) {
        const subscription = new SseSubscription(
            `${API_BASE}/worker/job/${encodeURIComponent(jobId)}/progress`,
            onProgress,
            onComplete,
            onError
        );
        return () => subscription.close();
    },

    /**
     * Subscribe to all jobs progress events via SSE (streaming all jobs).
     * Returns a cleanup function to close the connection.
     *
     * @param {Function} onProgress - Called with progress data.
     * @param {Function} onComplete - Called when a job completes.
     * @param {Function} onError - Called on error.
     * @returns {Function} Cleanup function to close the SSE connection.
     */
    onAllJobsProgress(onProgress, onComplete, onError) {
        const subscription = new SseSubscription(
            `${API_BASE}/worker/jobs/progress`,
            onProgress,
            onComplete,
            onError
        );
        return () => subscription.close();
    }
};
