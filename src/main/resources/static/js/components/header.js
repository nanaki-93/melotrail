/**
 * Header Component - Application header with navigation, worker controls, and status display.
 * 
 * Principles:
 * - Single Responsibility: Manages only the header UI and its state bindings
 * - Open/Closed: Extensible via event system without modifying internals
 * - Resource Management: Proper cleanup via destroy() pattern
 */

import { store } from '../state.js';
import { api } from '../api.js';

/**
 * Application header component with navigation, worker controls, and status display.
 */
export class Header {
    /**
     * Creates a new Header instance.
     */
    constructor() {
        this.element = null;
        this.unsubscribe = null;
    }

    /**
     * Renders the header element and binds events.
     * @returns {HTMLElement} The rendered header element.
     */
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
                <button id="btn-worker-stop" class="btn" disabled>Stop Worker</button>
                <span id="worker-status" class="status-badge">Worker: Stopped</span>
            </nav>
        `;
        this.bindEvents();
        this.subscribeToState();
        return this.element;
    }

    /**
     * Binds click event handlers to header buttons.
     * @private
     */
    bindEvents() {
        const newProjectBtn = this.element?.querySelector('#btn-new-project');
        const startWorkerBtn = this.element?.querySelector('#btn-worker-start');
        const stopWorkerBtn = this.element?.querySelector('#btn-worker-stop');

        newProjectBtn?.addEventListener('click', () => {
            this.onNewProject();
        });

        startWorkerBtn?.addEventListener('click', () => {
            this.onStartWorker();
        });

        stopWorkerBtn?.addEventListener('click', () => {
            this.onStopWorker();
        });
    }

    /**
     * Subscribes to worker state changes for real-time status updates.
     * @private
     */
    subscribeToState() {
        this.unsubscribe = store.subscribe('worker', (worker) => {
            this.updateWorkerStatus(worker);
        });
    }

    /**
     * Updates the worker status badge and button states based on worker state.
     * @param {object} worker - Worker state object.
     * @private
     */
    updateWorkerStatus(worker) {
        const statusBadge = this.element?.querySelector('#worker-status');
        const startBtn = this.element?.querySelector('#btn-worker-start');
        const stopBtn = this.element?.querySelector('#btn-worker-stop');

        if (!statusBadge) return;

        const isRunning = worker?.isRunning ?? false;

        if (isRunning) {
            statusBadge.textContent = 'Worker: Running';
            statusBadge.className = 'status-badge healthy';
            startBtn?.setAttribute('disabled', '');
            stopBtn?.removeAttribute('disabled');
        } else {
            statusBadge.textContent = 'Worker: Stopped';
            statusBadge.className = 'status-badge unhealthy';
            startBtn?.removeAttribute('disabled');
            stopBtn?.setAttribute('disabled', '');
        }
    }

    /**
     * Handles "New Project" button click - dispatches event for modal display.
     * @private
     */
    onNewProject() {
        this.element?.dispatchEvent(new CustomEvent('header:new-project', {
            bubbles: true,
            cancelable: true
        }));
    }

    /**
     * Handles "Start Worker" button click.
     * @private
     */
    async onStartWorker() {
        try {
            await api.startWorker();
        } catch (error) {
            console.error('[Header] Failed to start worker:', error);
        }
    }

    /**
     * Handles "Stop Worker" button click.
     * @private
     */
    async onStopWorker() {
        try {
            await api.stopWorker();
        } catch (error) {
            console.error('[Header] Failed to stop worker:', error);
        }
    }

    /**
     * Cleans up event listeners and subscriptions.
     */
    destroy() {
        this.unsubscribe?.();
        this.element = null;
    }
}
