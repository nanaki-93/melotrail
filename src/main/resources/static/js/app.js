/**
 * Application Entry Point - Main entry point that assembles all components
 * and initializes the application.
 *
 * Architecture:
 * - Header (top): Navigation, worker controls, status display
 * - Sidebar (left): Project list with CRUD operations
 * - Main content (right): Dynamic views rendered by the router
 * - Worker panel (right): AI operations and job monitoring
 *
 * Principles:
 * - Single Responsibility: Only orchestrates component initialization
 * - Open/Closed: Easy to add new components or views
 * - Resource Management: Proper cleanup via component destroy() pattern
 * - Error Resilience: Graceful degradation when API calls fail
 */

import { store } from './state.js';
import { api } from './api.js';
import { Header } from './components/header.js';
import { Sidebar } from './components/sidebar.js';
import { WorkerPanel } from './components/worker-panel.js';
import { navigate, initRouter } from './router.js';

/**
 * Application entry point that assembles all components and initializes the app.
 */
class App {
    /**
     * Creates a new App instance.
     */
    constructor() {
        /** @type {Header|null} */
        this.header = null;
        /** @type {Sidebar|null} */
        this.sidebar = null;
        /** @type {WorkerPanel|null} */
        this.workerPanel = null;
        /** @type {number} */
        this._loadingCount = 0;
    }

    /**
     * Initializes the application: creates layout, mounts components,
     * loads initial data, and sets up navigation.
     */
    async init() {
        this._showLoading();
        try {
            this._createLayout();
            this._mountComponents();
            await this._loadInitialData();
            this._setupNavigation();
            this._subscribeToState();
        } catch (error) {
            console.error('[App] Failed to initialize:', error);
        } finally {
            this._hideLoading();
        }
    }

    /**
     * Creates the application layout DOM structure.
     * @private
     */
    _createLayout() {
        const app = document.getElementById('app');
        app.innerHTML = `
            <div class="app-layout">
                <div class="app-header-container"></div>
                <div class="app-body">
                    <div class="app-sidebar-container"></div>
                    <div class="app-content">
                        <main class="app-main"></main>
                        <div class="app-worker-panel-container"></div>
                    </div>
                </div>
            </div>
            <div class="app-loading" style="display: none;">
                <div class="loading-spinner"></div>
                <span class="loading-text">Loading...</span>
            </div>
        `;
    }

    /**
     * Mounts all component instances into their respective layout containers.
     * @private
     */
    _mountComponents() {
        this.header = new Header();
        this.sidebar = new Sidebar();
        this.workerPanel = new WorkerPanel();

        this._getContainer('.app-header-container').appendChild(this.header.render());
        this._getContainer('.app-sidebar-container').appendChild(this.sidebar.render());
        this._getContainer('.app-worker-panel-container').appendChild(this.workerPanel.render());
    }

    /**
     * Loads initial application data from the API.
     * @private
     */
    async _loadInitialData() {
        try {
            const [projects, workerHealth] = await Promise.all([
                api.getProjects(),
                api.getWorkerHealth().catch(() => null)
            ]);

            store.set('projects', projects);
            store.set('worker', {
                isRunning: workerHealth?.status === 'running',
                jobs: []
            });
        } catch (error) {
            console.error('[App] Failed to load initial data:', error);
            store.set('projects', []);
            store.set('worker', {
                isRunning: false,
                jobs: []
            });
        }
    }

    /**
     * Sets up client-side navigation with the router,
     * ensuring views render inside the main content area.
     * @private
     */
    _setupNavigation() {
        const app = document.getElementById('app');
        const mainContainer = app.querySelector('.app-main');

        // Configure router to use the main content area as its container
        initRouter(mainContainer);
    }

    /**
     * Subscribes to state changes for cross-component communication.
     * @private
     */
    _subscribeToState() {
        // Listen for project selection to update worker panel context
        store.subscribe('currentProject', (project) => {
            window.dispatchEvent(new CustomEvent('state:currentProject', {
                detail: project
            }));
        });

        // Listen for worker state changes
        store.subscribe('worker', (worker) => {
            window.dispatchEvent(new CustomEvent('state:worker', {
                detail: worker
            }));
        });
    }

    /**
     * Shows the loading overlay.
     * @private
     */
    _showLoading() {
        this._loadingCount++;
        const loadingEl = this._getContainer('.app-loading');
        if (loadingEl) {
            loadingEl.style.display = 'flex';
        }
    }

    /**
     * Hides the loading overlay when all async operations complete.
     * @private
     */
    _hideLoading() {
        this._loadingCount = Math.max(0, this._loadingCount - 1);
        if (this._loadingCount === 0) {
            const loadingEl = this._getContainer('.app-loading');
            if (loadingEl) {
                loadingEl.style.display = 'none';
            }
        }
    }

    /**
     * Gets a DOM element by selector, falling back to #app.
     * @param {string} selector - CSS selector.
     * @returns {HTMLElement}
     * @private
     */
    _getContainer(selector) {
        return document.querySelector(selector) || document.getElementById('app');
    }

    /**
     * Cleans up all components and subscriptions.
     */
    destroy() {
        this.header?.destroy();
        this.sidebar?.destroy();
        this.workerPanel?.destroy();

        this.header = null;
        this.sidebar = null;
        this.workerPanel = null;
    }
}

// Initialize the application
const app = new App();
app.init().catch(console.error);

// Export for external use (e.g., testing)
export { app, App };
