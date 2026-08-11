/**
 * Worker Panel Component - AI command submission and job monitoring.
 *
 * Features:
 * - Command selection with parameter configuration
 * - Real-time job progress updates via SSE
 * - Job history with status indicators
 * - Worker status monitoring
 * - Error display for failed jobs
 *
 * Principles:
 * - Single Responsibility: Manages only the worker command panel UI
 * - Open/Closed: Extensible via event system without modifying internals
 * - Resource Management: Proper cleanup via destroy() pattern
 * - Progress Tracking: Manages SSE subscriptions for active jobs
 */

import { store } from '../state.js';
import { api } from '../api.js';

/**
 * Available AI commands with their display names and descriptions.
 * @readonly
 * @enum {string}
 */
const AI_COMMANDS = [
    { value: 'analyze', label: 'Analyze', description: 'Analyze audio (BPM, key, structure)' },
    { value: 'separate', label: 'Stem Separation', description: 'Separate audio into stems' },
    { value: 'master', label: 'Mastering', description: 'Apply AI mastering' },
    { value: 'apply_dsp', label: 'DSP Effects', description: 'Apply DSP effects chain' },
    { value: 'repair', label: 'Audio Repair', description: 'Repair audio quality issues' }
];

/**
 * Job status constants for UI rendering.
 * @readonly
 */
const JOB_STATUSES = {
    PENDING: 'pending',
    RUNNING: 'running',
    COMPLETED: 'completed',
    FAILED: 'failed'
};

/**
 * Worker panel component for submitting AI commands and monitoring job progress.
 */
export class WorkerPanel {
    /**
     * Creates a new WorkerPanel instance.
     */
    constructor() {
        this.element = null;
        this.unsubscribe = null;
        this.activeSubscriptions = new Map();
        this.jobs = [];
        this.currentJobId = null;
    }

    /**
     * Renders the worker panel element and binds events.
     * @returns {HTMLElement} The rendered worker panel element.
     */
    render() {
        this.element = document.createElement('div');
        this.element.className = 'worker-panel';
        this.element.innerHTML = `
            <div class="worker-panel-header">
                <h3>AI Operations</h3>
                <span id="worker-status" class="status-badge">Worker: Stopped</span>
            </div>

            <div class="worker-panel-body">
                <!-- Command Selection -->
                <div class="command-section">
                    <label for="command-select" class="section-label">Command</label>
                    <select id="command-select" class="select">
                        ${AI_COMMANDS.map(cmd =>
                            `<option value="${cmd.value}">${cmd.label}</option>`
                        ).join('')}
                    </select>
                    <p id="command-description" class="command-description">
                        ${AI_COMMANDS[0].description}
                    </p>
                </div>

                <!-- Parameters Section (expandable) -->
                <div class="params-section">
                    <button id="btn-toggle-params" class="btn btn-secondary btn-full">
                        <span>Parameters</span>
                        <span id="params-toggle-icon" class="params-toggle-icon">▼</span>
                    </button>
                    <div id="params-container" class="params-container" style="display: none;">
                        <div id="params-fields" class="params-fields">
                            <!-- Dynamic parameter fields rendered here -->
                        </div>
                    </div>
                </div>

                <!-- Submit Button -->
                <button id="btn-submit" class="btn btn-primary btn-full" disabled>
                    Submit Command
                </button>

                <!-- Progress Section -->
                <div id="job-progress" class="job-progress" style="display: none;">
                    <div class="job-progress-header">
                        <span id="progress-job-name" class="progress-job-name">Processing...</span>
                        <span id="progress-percent" class="progress-percent">0%</span>
                    </div>
                    <div class="progress-bar">
                        <div id="progress-fill" class="progress-fill" style="width: 0%"></div>
                    </div>
                    <p id="progress-status" class="progress-status"></p>
                </div>

                <!-- Error Display -->
                <div id="job-error" class="job-error" style="display: none;">
                    <div class="job-error-header">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" width="16" height="16">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                  d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"/>
                        </svg>
                        <span>Job Failed</span>
                    </div>
                    <p id="error-message" class="error-text"></p>
                </div>

                <!-- Job History -->
                <div class="job-history-section">
                    <div class="job-history-header">
                        <h4>Job History</h4>
                        <button id="btn-refresh-history" class="btn btn-secondary btn-sm" title="Refresh">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" width="14" height="14">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                      d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"/>
                            </svg>
                        </button>
                    </div>
                    <div id="job-history-list" class="job-history-list">
                        <div class="job-history-empty">
                            <p>No jobs yet</p>
                        </div>
                    </div>
                </div>
            </div>
        `;

        this.bindEvents();
        this.subscribeToState();
        this.loadJobHistory();

        return this.element;
    }

    /**
     * Binds click event handlers to panel elements.
     * @private
     */
    bindEvents() {
        // Command selection change
        this.element?.querySelector('#command-select')?.addEventListener('change', (e) => {
            this.onCommandChange(e.target.value);
        });

        // Parameters toggle
        this.element?.querySelector('#btn-toggle-params')?.addEventListener('click', () => {
            this.toggleParams();
        });

        // Submit command
        this.element?.querySelector('#btn-submit')?.addEventListener('click', () => {
            this.submitCommand();
        });

        // Refresh job history
        this.element?.querySelector('#btn-refresh-history')?.addEventListener('click', () => {
            this.loadJobHistory();
        });
    }

    /**
     * Subscribes to worker state changes for real-time status updates.
     * @private
     */
    subscribeToState() {
        this.unsubscribe = store.subscribe('worker', (worker) => {
            this.updateWorkerStatus(worker);
            this.updateCurrentJob(worker.currentJob);
        });
    }

    /**
     * Updates the worker status badge based on worker state.
     * @param {object} worker - Worker state object.
     * @private
     */
    updateWorkerStatus(worker) {
        const statusBadge = this.element?.querySelector('#worker-status');
        const submitBtn = this.element?.querySelector('#btn-submit');

        if (!statusBadge) return;

        const isRunning = worker?.isRunning ?? false;

        if (isRunning) {
            statusBadge.textContent = 'Worker: Running';
            statusBadge.className = 'status-badge healthy';
            submitBtn?.removeAttribute('disabled');
        } else {
            statusBadge.textContent = 'Worker: Stopped';
            statusBadge.className = 'status-badge unhealthy';
            submitBtn?.setAttribute('disabled', '');
        }
    }

    /**
     * Updates the current job display when state changes.
     * @param {object|null} currentJob - The current job object.
     * @private
     */
    updateCurrentJob(currentJob) {
        if (!currentJob) {
            this.hideProgress();
            return;
        }

        this.showProgress();
        this.updateProgress(currentJob);
    }

    /**
     * Handles command selection change - updates description and parameters.
     * @param {string} command - Selected command value.
     * @private
     */
    onCommandChange(command) {
        const commandInfo = AI_COMMANDS.find(c => c.value === command);
        const descriptionEl = this.element?.querySelector('#command-description');
        const paramsFields = this.element?.querySelector('#params-fields');

        if (descriptionEl && commandInfo) {
            descriptionEl.textContent = commandInfo.description;
        }

        // Render command-specific parameters
        if (paramsFields) {
            paramsFields.innerHTML = this.renderParameters(command);
        }
    }

    /**
     * Renders parameter fields for a given command type.
     * @param {string} command - Command type.
     * @returns {string} HTML string for parameter fields.
     * @private
     */
    renderParameters(command) {
        const params = this.getCommandParameters(command);

        if (params.length === 0) {
            return '<p class="params-hint">No parameters required for this command.</p>';
        }

        return params.map(param => `
            <div class="param-field">
                <label for="param-${param.id}" class="param-label">${param.label}</label>
                ${this.renderParamInput(param)}
            </div>
        `).join('');
    }

    /**
     * Renders an input element based on parameter type.
     * @param {object} param - Parameter definition.
     * @returns {string} HTML string for the input element.
     * @private
     */
    renderParamInput(param) {
        switch (param.type) {
            case 'range':
                return `
                    <div class="param-range">
                        <input type="range" id="param-${param.id}" min="${param.min}" max="${param.max}" value="${param.default}" class="range">
                        <span id="param-${param.id}-value" class="param-value">${param.default}</span>
                    </div>
                `;
            case 'select':
                return `
                    <select id="param-${param.id}" class="select">
                        ${param.options.map(opt =>
                            `<option value="${opt.value}" ${opt.value === param.default ? 'selected' : ''}>${opt.label}</option>`
                        ).join('')}
                    </select>
                `;
            case 'checkbox':
                return `
                    <label class="checkbox">
                        <input type="checkbox" id="param-${param.id}" ${param.default ? 'checked' : ''}>
                        <span>${param.label}</span>
                    </label>
                `;
            default:
                return `<input type="text" id="param-${param.id}" value="${param.default || ''}" class="input" placeholder="${param.placeholder || ''}">`;
        }
    }

    /**
     * Returns parameter definitions for a given command.
     * @param {string} command - Command type.
     * @returns {Array} Array of parameter definitions.
     * @private
     */
    getCommandParameters(command) {
        const params = {
            analyze: [
                { id: 'detail', label: 'Analysis Detail', type: 'select', options: [
                    { value: 'basic', label: 'Basic' },
                    { value: 'detailed', label: 'Detailed' }
                ], default: 'detailed' }
            ],
            separate: [
                { id: 'stems', label: 'Number of Stems', type: 'select', options: [
                    { value: '2', label: '2 (e.g., Vocals/Other)' },
                    { value: '4', label: '4 (e.g., Vocals/Bass/Drums/Other)' },
                    { value: '5', label: '5 (e.g., Vocals/Bass/Drums/Piano/Other)' }
                ], default: '4' },
                { id: 'quality', label: 'Output Quality', type: 'select', options: [
                    { value: 'standard', label: 'Standard' },
                    { value: 'high', label: 'High' }
                ], default: 'high' }
            ],
            master: [
                { id: 'style', label: 'Mastering Style', type: 'select', options: [
                    { value: 'balanced', label: 'Balanced' },
                    { value: 'loud', label: 'Loud' },
                    { value: 'warm', label: 'Warm' },
                    { value: 'transparent', label: 'Transparent' }
                ], default: 'balanced' },
                { id: 'target_loudness', label: 'Target Loudness (LUFS)', type: 'range', min: -20, max: -5, default: -14 }
            ],
            apply_dsp: [
                { id: 'effects', label: 'Effects Chain', type: 'select', options: [
                    { value: 'reverb', label: 'Reverb' },
                    { value: 'delay', label: 'Delay' },
                    { value: 'chorus', label: 'Chorus' },
                    { value: 'compressor', label: 'Compressor' }
                ], default: 'reverb' },
                { id: 'intensity', label: 'Intensity', type: 'range', min: 0, max: 100, default: 50 }
            ],
            repair: [
                { id: 'issues', label: 'Target Issues', type: 'select', options: [
                    { value: 'noise', label: 'Noise Reduction' },
                    { value: 'clicks', label: 'Click Removal' },
                    { value: 'hum', label: 'Hum Removal' },
                    { value: 'all', label: 'All Issues' }
                ], default: 'all' }
            ]
        };

        return params[command] || [];
    }

    /**
     * Toggles the parameters section visibility.
     * @private
     */
    toggleParams() {
        const container = this.element?.querySelector('#params-container');
        const icon = this.element?.querySelector('#params-toggle-icon');

        if (!container) return;

        const isVisible = container.style.display !== 'none';
        container.style.display = isVisible ? 'none' : 'block';
        if (icon) icon.textContent = isVisible ? '▼' : '▲';
    }

    /**
     * Collects parameter values from the form.
     * @returns {object} Collected parameters.
     * @private
     */
    collectParameters() {
        const params = {};
        const fields = this.element?.querySelectorAll('.param-field');

        fields?.forEach(field => {
            const input = field.querySelector('input, select');
            if (!input) return;

            const id = input.id?.replace('param-', '');
            if (!id) return;

            if (input.type === 'checkbox') {
                params[id] = input.checked;
            } else if (input.type === 'range') {
                params[id] = parseFloat(input.value);
            } else {
                params[id] = input.value;
            }
        });

        return params;
    }

    /**
     * Submits the selected command to the worker.
     * @private
     */
    async submitCommand() {
        const commandSelect = this.element?.querySelector('#command-select');
        const command = commandSelect?.value;

        if (!command) {
            this.showError('Please select a command.');
            return;
        }

        const params = this.collectParameters();

        try {
            // Show loading state
            this.showProgress();
            this.hideError();

            const submitBtn = this.element?.querySelector('#btn-submit');
            submitBtn?.setAttribute('disabled', '');
            if (submitBtn) submitBtn.textContent = 'Submitting...';

            // Submit command
            const job = await api.submitCommand(command, params);
            this.currentJobId = job.jobId;

            // Update UI
            const jobNameEl = this.element?.querySelector('#progress-job-name');
            const commandInfo = AI_COMMANDS.find(c => c.value === command);
            if (jobNameEl && commandInfo) {
                jobNameEl.textContent = `${commandInfo.label} - Job ${job.jobId.slice(0, 8)}`;
            }

            // Subscribe to SSE progress updates
            this.subscribeToProgress(job.jobId);

            // Add to jobs list
            this.addJobToList(job);

        } catch (error) {
            console.error('[WorkerPanel] Failed to submit command:', error);
            this.showError(error.message || 'Failed to submit command.');

            // Reset submit button
            const submitBtn = this.element?.querySelector('#btn-submit');
            submitBtn?.removeAttribute('disabled');
            if (submitBtn) submitBtn.textContent = 'Submit Command';
        }
    }

    /**
     * Subscribes to SSE progress events for a job.
     * @param {string} jobId - Job ID to subscribe to.
     * @private
     */
    subscribeToProgress(jobId) {
        // Clean up any existing subscription for this job
        this.activeSubscriptions.get(jobId)?.();

        const cleanup = api.onJobProgress(
            jobId,
            (progress) => this.handleProgressUpdate(progress),
            (result) => this.handleJobComplete(result),
            (error) => this.handleJobError(error)
        );

        this.activeSubscriptions.set(jobId, cleanup);
    }

    /**
     * Handles SSE progress update events.
     * @param {object} progress - Progress data from server.
     * @private
     */
    handleProgressUpdate(progress) {
        const percent = progress.progress ?? 0;
        const status = progress.status ?? '';

        this.updateProgress({
            progress: percent,
            status: status
        });
    }

    /**
     * Handles job completion events.
     * @param {object} result - Job completion result.
     * @private
     */
    handleJobComplete(result) {
        this.updateJobStatus(this.currentJobId, JOB_STATUSES.COMPLETED, result);
        this.hideProgress();

        // Reset submit button
        const submitBtn = this.element?.querySelector('#btn-submit');
        submitBtn?.removeAttribute('disabled');
        if (submitBtn) submitBtn.textContent = 'Submit Command';

        // Reload job history
        this.loadJobHistory();

        // Clean up subscription
        const cleanup = this.activeSubscriptions.get(this.currentJobId);
        if (cleanup) {
            cleanup();
            this.activeSubscriptions.delete(this.currentJobId);
        }
    }

    /**
     * Handles job error events.
     * @param {string|object} error - Error information.
     * @private
     */
    handleJobError(error) {
        const errorMessage = typeof error === 'object' ? error.message : error;
        this.showError(errorMessage);
        this.updateJobStatus(this.currentJobId, JOB_STATUSES.FAILED, { error: errorMessage });

        // Reset submit button
        const submitBtn = this.element?.querySelector('#btn-submit');
        submitBtn?.removeAttribute('disabled');
        if (submitBtn) submitBtn.textContent = 'Submit Command';

        // Clean up subscription
        const cleanup = this.activeSubscriptions.get(this.currentJobId);
        if (cleanup) {
            cleanup();
            this.activeSubscriptions.delete(this.currentJobId);
        }
    }

    /**
     * Updates the progress bar and status display.
     * @param {object} data - Progress data.
     * @private
     */
    updateProgress(data) {
        const fill = this.element?.querySelector('#progress-fill');
        const percent = this.element?.querySelector('#progress-percent');
        const status = this.element?.querySelector('#progress-status');

        const progressPercent = Math.min(100, Math.max(0, data.progress ?? 0));

        if (fill) {
            fill.style.width = `${progressPercent}%`;
        }
        if (percent) {
            percent.textContent = `${Math.round(progressPercent)}%`;
        }
        if (status) {
            status.textContent = data.status || '';
        }
    }

    /**
     * Shows the progress section.
     * @private
     */
    showProgress() {
        const progressEl = this.element?.querySelector('#job-progress');
        if (progressEl) {
            progressEl.style.display = 'block';
        }
    }

    /**
     * Hides the progress section.
     * @private
     */
    hideProgress() {
        const progressEl = this.element?.querySelector('#job-progress');
        if (progressEl) {
            progressEl.style.display = 'none';
        }
    }

    /**
     * Shows an error message.
     * @param {string} message - Error message to display.
     * @private
     */
    showError(message) {
        const errorEl = this.element?.querySelector('#job-error');
        const errorText = this.element?.querySelector('#error-message');

        if (errorEl) {
            errorEl.style.display = 'block';
        }
        if (errorText) {
            errorText.textContent = message;
        }
    }

    /**
     * Hides the error display.
     * @private
     */
    hideError() {
        const errorEl = this.element?.querySelector('#job-error');
        if (errorEl) {
            errorEl.style.display = 'none';
        }
    }

    /**
     * Updates a job's status in the jobs list and state.
     * @param {string} jobId - Job ID to update.
     * @param {string} status - New job status.
     * @param {object} result - Job result data.
     * @private
     */
    updateJobStatus(jobId, status, result) {
        const jobIndex = this.jobs.findIndex(j => j.id === jobId);

        if (jobIndex !== -1) {
            this.jobs[jobIndex].status = status;
            this.jobs[jobIndex].result = result;
            this.jobs[jobIndex].completedAt = new Date().toISOString();
        }

        // Update store
        const worker = store.get('worker');
        if (worker?.currentJob?.id === jobId) {
            store.partialUpdate('worker', {
                currentJob: null,
                progress: status === JOB_STATUSES.COMPLETED ? 100 : worker.progress
            });
        }

        this.renderJobHistory();
    }

    /**
     * Adds a new job to the jobs list.
     * @param {object} job - Job object from API.
     * @private
     */
    addJobToList(job) {
        const commandInfo = AI_COMMANDS.find(c => c.value === job.commandType) ||
            { label: job.commandType };

        const newJob = {
            id: job.jobId,
            command: job.commandType,
            commandLabel: commandInfo.label,
            status: JOB_STATUSES.RUNNING,
            createdAt: new Date().toISOString(),
            result: null
        };

        this.jobs.unshift(newJob);
        this.renderJobHistory();
    }

    /**
     * Loads job history from the API.
     * @private
     */
    async loadJobHistory() {
        try {
            const recentJobs = await api.getRecentJobs();

            if (recentJobs?.length > 0) {
                this.jobs = recentJobs.map(job => ({
                    id: job.jobId || job.id,
                    command: job.commandType,
                    commandLabel: this.getCommandLabel(job.commandType),
                    status: job.status || JOB_STATUSES.COMPLETED,
                    createdAt: job.createdAt || job.startedAt || new Date().toISOString(),
                    completedAt: job.completedAt,
                    result: job.result
                }));

                this.renderJobHistory();
            }
        } catch (error) {
            console.error('[WorkerPanel] Failed to load job history:', error);
        }
    }

    /**
     * Gets the display label for a command type.
     * @param {string} commandType - Command type string.
     * @returns {string} Display label.
     * @private
     */
    getCommandLabel(commandType) {
        const commandInfo = AI_COMMANDS.find(c => c.value === commandType);
        return commandInfo?.label || commandType;
    }

    /**
     * Renders the job history list.
     * @private
     */
    renderJobHistory() {
        const list = this.element?.querySelector('#job-history-list');
        if (!list) return;

        if (this.jobs.length === 0) {
            list.innerHTML = `
                <div class="job-history-empty">
                    <p>No jobs yet</p>
                </div>
            `;
            return;
        }

        list.innerHTML = this.jobs.map(job => this.renderJobItem(job)).join('');
        this.bindJobHistoryEvents();
    }

    /**
     * Renders a single job history item.
     * @param {object} job - Job object.
     * @returns {string} HTML string for the job item.
     * @private
     */
    renderJobItem(job) {
        const statusClass = this.getJobStatusClass(job.status);
        const statusLabel = this.getJobStatusLabel(job.status);
        const timeStr = this.formatTime(job.createdAt);

        return `
            <div class="job-history-item ${job.status === JOB_STATUSES.RUNNING ? 'job-running' : ''}"
                 data-job-id="${job.id}">
                <div class="job-item-info">
                    <span class="job-item-command">${this.escapeHtml(job.commandLabel)}</span>
                    <span class="job-item-time">${timeStr}</span>
                </div>
                <span class="status-badge ${statusClass}">${statusLabel}</span>
            </div>
        `;
    }

    /**
     * Binds events to job history items.
     * @private
     */
    bindJobHistoryEvents() {
        // Click to view job details (could expand to show more info)
        this.element?.querySelectorAll('.job-history-item').forEach(item => {
            item.addEventListener('click', () => {
                this.onJobItemClick(item.dataset.jobId);
            });
        });
    }

    /**
     * Handles job history item click.
     * @param {string} jobId - Clicked job ID.
     * @private
     */
    onJobItemClick(jobId) {
        // Could expand to show job details
        console.log('[WorkerPanel] Job clicked:', jobId);
    }

    /**
     * Returns CSS class for a job status.
     * @param {string} status - Job status.
     * @returns {string} CSS class name.
     * @private
     */
    getJobStatusClass(status) {
        switch (status) {
            case JOB_STATUSES.COMPLETED:
                return 'badge-success';
            case JOB_STATUSES.FAILED:
                return 'badge-danger';
            case JOB_STATUSES.RUNNING:
                return 'badge-info';
            default:
                return 'badge-warning';
        }
    }

    /**
     * Returns display label for a job status.
     * @param {string} status - Job status.
     * @returns {string} Status label.
     * @private
     */
    getJobStatusLabel(status) {
        switch (status) {
            case JOB_STATUSES.COMPLETED:
                return 'Completed';
            case JOB_STATUSES.FAILED:
                return 'Failed';
            case JOB_STATUSES.RUNNING:
                return 'Running';
            default:
                return 'Pending';
        }
    }

    /**
     * Formats a timestamp to relative time string.
     * @param {string} timestamp - ISO timestamp string.
     * @returns {string} Relative time string.
     * @private
     */
    formatTime(timestamp) {
        if (!timestamp) return '';

        const date = new Date(timestamp);
        const now = new Date();
        const diffMs = now - date;
        const diffMins = Math.floor(diffMs / 60000);
        const diffHours = Math.floor(diffMs / 3600000);
        const diffDays = Math.floor(diffMs / 86400000);

        if (diffMins < 1) return 'Just now';
        if (diffMins < 60) return `${diffMins}m ago`;
        if (diffHours < 24) return `${diffHours}h ago`;
        if (diffDays < 7) return `${diffDays}d ago`;

        return date.toLocaleDateString();
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
     * Cleans up event listeners and subscriptions.
     */
    destroy() {
        // Clean up all active SSE subscriptions
        this.activeSubscriptions.forEach(cleanup => cleanup());
        this.activeSubscriptions.clear();

        // Clean up state subscription
        this.unsubscribe?.();

        this.element = null;
        this.jobs = [];
        this.currentJobId = null;
    }
}
