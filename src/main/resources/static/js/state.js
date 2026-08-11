/**
 * Centralized State Management using the Store Pattern.
 * Provides a singleton store with reactive subscriptions for the Web UI.
 * 
 * Principles:
 * - Single Responsibility: Store only manages state and notifications
 * - Open/Closed: Extensible via subscribe mechanism without modifying internals
 * - Immutable Updates: All state changes create new objects (no direct mutation)
 */

class Store {
    /** @type {Record<string, any>} */
    #state;

    /** @type {Map<string, Set<Function>>} */
    #listeners;

    /**
     * Creates a new Store instance with default state.
     */
    constructor() {
        this.#state = {
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
        this.#listeners = new Map();
    }

    /**
     * Gets a state slice by key.
     * @param {string} key - The state key to retrieve.
     * @returns {any} The current state value.
     */
    get(key) {
        return this.#state[key];
    }

    /**
     * Sets a state slice by key with immutable update semantics.
     * Creates a new object reference to ensure subscribers are notified.
     * @param {string} key - The state key to update.
     * @param {any} value - The new state value (will be deep cloned for immutability).
     */
    set(key, value) {
        if (!Object.prototype.hasOwnProperty.call(this.#state, key)) {
            console.warn(`[Store] Unknown state key: ${key}`);
            return;
        }

        // Deep clone to ensure immutable updates
        const immutableValue = this.#deepClone(value);
        this.#state[key] = immutableValue;
        this.#notify(key);
    }

    /**
     * Subscribes to changes for a specific state key.
     * @param {string} key - The state key to subscribe to.
     * @param {Function} callback - Function called with the new state value.
     * @returns {Function} Unsubscribe function.
     */
    subscribe(key, callback) {
        if (typeof callback !== 'function') {
            throw new TypeError('Subscribe callback must be a function');
        }

        if (!this.#listeners.has(key)) {
            this.#listeners.set(key, new Set());
        }

        this.#listeners.get(key).add(callback);

        // Return unsubscribe function
        return () => {
            const keyListeners = this.#listeners.get(key);
            if (keyListeners) {
                keyListeners.delete(callback);
                // Clean up empty listener sets
                if (keyListeners.size === 0) {
                    this.#listeners.delete(key);
                }
            }
        };
    }

    /**
     * Notifies all subscribers for a given state key.
     * @param {string} key - The state key that changed.
     * @private
     */
    #notify(key) {
        const listeners = this.#listeners.get(key);
        if (listeners) {
            // Convert to array to avoid issues with mutations during iteration
            [...listeners].forEach(cb => {
                try {
                    cb(this.#state[key]);
                } catch (error) {
                    console.error(`[Store] Error in subscriber for key "${key}":`, error);
                }
            });
        }
    }

    /**
     * Deep clones a value to ensure immutable state updates.
     * @param {any} value - Value to clone.
     * @returns {any} Cloned value.
     * @private
     */
    #deepClone(value) {
        if (value === null || typeof value !== 'object') {
            return value;
        }
        if (value instanceof Date) {
            return new Date(value.getTime());
        }
        if (Array.isArray(value)) {
            return value.map(item => this.#deepClone(item));
        }
        return Object.fromEntries(
            Object.entries(value).map(([k, v]) => [k, this.#deepClone(v)])
        );
    }

    /**
     * Resets the entire store to initial state.
     */
    reset() {
        const initialState = {
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

        for (const key of Object.keys(initialState)) {
            this.#state[key] = this.#deepClone(initialState[key]);
            this.#notify(key);
        }
    }

    /**
     * Batch updates multiple state keys at once.
     * Only notifies once per changed key.
     * @param {Record<string, any>} updates - Object mapping keys to new values.
     */
    batchUpdate(updates) {
        const changedKeys = new Set();

        for (const [key, value] of Object.entries(updates)) {
            if (Object.prototype.hasOwnProperty.call(this.#state, key)) {
                this.#state[key] = this.#deepClone(value);
                changedKeys.add(key);
            }
        }

        // Notify all changed keys
        changedKeys.forEach(key => this.#notify(key));
    }

    /**
     * Partially updates a state object (deep merge).
     * @param {string} key - The state key to partially update.
     * @param {object} partial - Partial state to merge with existing.
     */
    partialUpdate(key, partial) {
        const current = this.#state[key];
        if (typeof current === 'object' && current !== null && !Array.isArray(current)) {
            const merged = { ...current, ...this.#deepClone(partial) };
            this.set(key, merged);
        } else {
            this.set(key, partial);
        }
    }
}

// Export singleton instance
export const store = new Store();

// Also export the class for testing purposes
export { Store };
