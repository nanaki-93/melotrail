/**
 * Modal Components - Reusable modal dialog utilities.
 *
 * Features:
 * - Base showModal() with overlay, backdrop, and keyboard support
 * - showCreateProjectModal() — Project creation form with validation
 * - showDeleteConfirmModal() — Confirmation for destructive actions
 * - showSettingsModal() — Server configuration dialog
 * - Keyboard accessible (Escape to close, Tab trapping)
 * - Backdrop click to close
 * - Form validation before submission
 *
 * Principles:
 * - Single Responsibility: Each modal function handles only its specific dialog
 * - Open/Closed: Base showModal() is extensible without modification
 * - XSS Prevention: All user content is escaped before rendering
 * - Resource Management: Proper cleanup via overlay removal
 */

import { api } from '../api.js';
import { store } from '../state.js';

// ==================== Modal Trapping ====================

/**
 * Traps keyboard focus within the modal for accessibility.
 * @param {HTMLElement} modal - The modal element to trap focus in.
 * @private
 */
function trapFocus(modal) {
    const focusableElements = modal.querySelectorAll(
        'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
    );
    const firstFocusable = focusableElements[0];
    const lastFocusable = focusableElements[focusableElements.length - 1];

    modal.addEventListener('keydown', (e) => {
        if (e.key !== 'Tab') return;

        if (e.shiftKey) {
            if (document.activeElement === firstFocusable) {
                e.preventDefault();
                lastFocusable.focus();
            }
        } else {
            if (document.activeElement === lastFocusable) {
                e.preventDefault();
                firstFocusable.focus();
            }
        }
    });
}

/**
 * Escapes HTML to prevent XSS attacks.
 * @param {string} text - Text to escape.
 * @returns {string} Escaped text.
 * @private
 */
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// ==================== Base Modal ====================

/**
 * Creates and displays a modal dialog with overlay, header, body, and footer.
 * Handles keyboard (Escape) and backdrop click to close.
 *
 * @param {string} content - HTML content for the modal body.
 * @param {string} title - Modal title (optional, defaults to empty).
 * @returns {HTMLElement} The created overlay element (caller should remove when done).
 */
export function showModal(content, title = '') {
    const overlay = document.createElement('div');
    overlay.className = 'modal-overlay';
    overlay.setAttribute('role', 'dialog');
    overlay.setAttribute('aria-modal', 'true');
    overlay.setAttribute('aria-labelledby', 'modal-title');

    overlay.innerHTML = `
        <div class="modal">
            <div class="modal-header">
                <h2 id="modal-title">${escapeHtml(title)}</h2>
                <button class="modal-close" aria-label="Close dialog">&times;</button>
            </div>
            <div class="modal-body">${content}</div>
        </div>
    `;

    document.body.appendChild(overlay);

    // Trigger animation
    requestAnimationFrame(() => {
        overlay.classList.add('visible');
    });

    // Focus trap for accessibility
    trapFocus(overlay);

    // Close handlers
    const closeModal = () => {
        overlay.classList.remove('visible');
        setTimeout(() => overlay.remove(), 200);
    };

    overlay.querySelector('.modal-close').addEventListener('click', closeModal);

    // Backdrop click to close
    overlay.addEventListener('click', (e) => {
        if (e.target === overlay) {
            closeModal();
        }
    });

    // Escape key to close
    overlay.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            closeModal();
        }
    });

    return overlay;
}

// ==================== Create Project Modal ====================

/**
 * Displays a modal dialog for creating a new project.
 * Includes form validation for title (required) and artist (optional).
 *
 * @param {Function} [onSuccess] - Optional callback invoked on successful creation.
 * @returns {HTMLElement} The created overlay element.
 */
export function showCreateProjectModal(onSuccess) {
    const content = `
        <form id="create-project-form" novalidate>
            <div class="form-group">
                <label for="project-title" class="form-label">
                    Title <span class="required">*</span>
                </label>
                <input
                    type="text"
                    id="project-title"
                    name="title"
                    class="input"
                    placeholder="Enter project title"
                    required
                    maxlength="200"
                    autofocus
                >
                <span class="form-error" id="title-error"></span>
            </div>
            <div class="form-group">
                <label for="project-artist" class="form-label">Artist</label>
                <input
                    type="text"
                    id="project-artist"
                    name="artist"
                    class="input"
                    placeholder="Enter artist name (optional)"
                    maxlength="200"
                >
            </div>
            <div class="modal-footer">
                <button type="button" id="btn-cancel-create" class="btn btn-secondary">Cancel</button>
                <button type="submit" id="btn-submit-create" class="btn btn-primary">Create</button>
            </div>
        </form>
    `;

    const overlay = showModal(content, 'New Project');
    const form = overlay.querySelector('#create-project-form');
    const titleInput = overlay.querySelector('#project-title');
    const titleError = overlay.querySelector('#title-error');

    // Cancel button
    overlay.querySelector('#btn-cancel-create').addEventListener('click', () => {
        overlay.remove();
    });

    /**
     * Validates the create project form fields.
     * @returns {boolean} Whether the form is valid.
     */
    function validateForm() {
        const title = titleInput.value.trim();
        let isValid = true;

        if (!title) {
            titleError.textContent = 'Title is required';
            titleInput.classList.add('input-error');
            isValid = false;
        } else if (title.length < 2) {
            titleError.textContent = 'Title must be at least 2 characters';
            titleInput.classList.add('input-error');
            isValid = false;
        } else {
            titleError.textContent = '';
            titleInput.classList.remove('input-error');
        }

        return isValid;
    }

    // Real-time validation on input
    titleInput.addEventListener('input', () => {
        if (titleInput.classList.contains('input-error')) {
            validateForm();
        }
    });

    // Form submission
    form.addEventListener('submit', async (e) => {
        e.preventDefault();

        if (!validateForm()) {
            titleInput.focus();
            return;
        }

        const formData = new FormData(form);
        const title = formData.get('title').trim();
        const artist = formData.get('artist').trim();
        const submitBtn = overlay.querySelector('#btn-submit-create');

        // Show loading state
        submitBtn.disabled = true;
        submitBtn.textContent = 'Creating...';

        try {
            const project = await api.createProject(title, artist);

            // Update store
            const projects = store.get('projects') || [];
            projects.unshift(project);
            store.set('projects', projects);

            // Dispatch event for external listeners
            document.dispatchEvent(new CustomEvent('modal:create-project', {
                bubbles: true,
                cancelable: true,
                detail: project
            }));

            // Call success callback if provided
            if (typeof onSuccess === 'function') {
                onSuccess(project);
            }

            overlay.remove();
        } catch (error) {
            console.error('[Modal] Failed to create project:', error);
            titleError.textContent = error.message || 'Failed to create project';
            titleError.classList.add('form-error-visible');

            // Reset button state
            submitBtn.disabled = false;
            submitBtn.textContent = 'Create';
        }
    });

    return overlay;
}

// ==================== Delete Confirmation Modal ====================

/**
 * Displays a confirmation modal for destructive actions.
 *
 * @param {object} options - Configuration options.
 * @param {string} options.title - Modal title (e.g., "Delete Project").
 * @param {string} options.message - Confirmation message.
 * @param {string} [options.itemName] - Name of the item being deleted (for clarity).
 * @param {Function} options.onConfirm - Callback invoked when user confirms.
 * @param {Function} [options.onCancel] - Optional callback invoked when user cancels.
 * @returns {HTMLElement} The created overlay element.
 */
export function showDeleteConfirmModal(options) {
    const { title, message, itemName, onConfirm, onCancel } = options;

    const itemInfo = itemName
        ? `<p class="delete-item-name">"${escapeHtml(itemName)}"</p>`
        : '';

    const content = `
        <div class="delete-confirm">
            <svg class="delete-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" width="48" height="48">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
                      d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"/>
            </svg>
            <p class="delete-message">${escapeHtml(message)}</p>
            ${itemInfo}
            <div class="modal-footer">
                <button type="button" id="btn-cancel-delete" class="btn btn-secondary">Cancel</button>
                <button type="button" id="btn-confirm-delete" class="btn btn-danger">Delete</button>
            </div>
        </div>
    `;

    const overlay = showModal(content, title);
    const confirmBtn = overlay.querySelector('#btn-confirm-delete');
    const cancelBtn = overlay.querySelector('#btn-cancel-delete');

    // Cancel handler
    cancelBtn.addEventListener('click', () => {
        if (typeof onCancel === 'function') {
            onCancel();
        }
        overlay.remove();
    });

    // Confirm handler
    confirmBtn.addEventListener('click', async () => {
        confirmBtn.disabled = true;
        confirmBtn.textContent = 'Deleting...';

        try {
            await onConfirm();

            // Dispatch event for external listeners
            document.dispatchEvent(new CustomEvent('modal:delete-confirm', {
                bubbles: true,
                cancelable: true,
                detail: { itemName }
            }));

            overlay.remove();
        } catch (error) {
            console.error('[Modal] Delete confirmation failed:', error);
            confirmBtn.disabled = false;
            confirmBtn.textContent = 'Delete';
            throw error;
        }
    });

    // Auto-focus the confirm button for quick destructive action
    confirmBtn.focus();

    return overlay;
}

// ==================== Settings Modal ====================

/**
 * Displays a modal dialog for server/API configuration settings.
 * Allows editing base API URL and worker endpoint configuration.
 *
 * @param {object} [initialSettings] - Pre-filled settings object.
 * @param {string} [initialSettings.apiUrl] - Current API base URL.
 * @param {string} [initialSettings.workerUrl] - Current worker URL.
 * @param {Function} [onSave] - Optional callback invoked on save.
 * @returns {HTMLElement} The created overlay element.
 */
export function showSettingsModal(initialSettings, onSave) {
    const apiUrl = initialSettings?.apiUrl || '/api';
    const workerUrl = initialSettings?.workerUrl || '';

    const content = `
        <form id="settings-form" novalidate>
            <div class="form-group">
                <label for="settings-api-url" class="form-label">API Base URL</label>
                <input
                    type="url"
                    id="settings-api-url"
                    name="apiUrl"
                    class="input"
                    value="${escapeHtml(apiUrl)}"
                    placeholder="http://localhost:8080/api"
                    required
                >
                <span class="form-hint">The base URL for all API requests</span>
            </div>
            <div class="form-group">
                <label for="settings-worker-url" class="form-label">Worker URL</label>
                <input
                    type="url"
                    id="settings-worker-url"
                    name="workerUrl"
                    class="input"
                    value="${escapeHtml(workerUrl)}"
                    placeholder="http://localhost:8081"
                >
                <span class="form-hint">URL for the AI worker service (optional)</span>
            </div>
            <div class="form-group">
                <label for="settings-timeout" class="form-label">Request Timeout (ms)</label>
                <input
                    type="number"
                    id="settings-timeout"
                    name="timeout"
                    class="input"
                    value="30000"
                    min="1000"
                    max="300000"
                    step="1000"
                >
                <span class="form-hint">Timeout in milliseconds (1000 - 300000)</span>
            </div>
            <div class="modal-footer">
                <button type="button" id="btn-cancel-settings" class="btn btn-secondary">Cancel</button>
                <button type="submit" id="btn-save-settings" class="btn btn-primary">Save</button>
            </div>
        </form>
    `;

    const overlay = showModal(content, 'Settings');
    const form = overlay.querySelector('#settings-form');
    const apiUrlInput = overlay.querySelector('#settings-api-url');
    const workerUrlInput = overlay.querySelector('#settings-worker-url');
    const timeoutInput = overlay.querySelector('#settings-timeout');
    const saveBtn = overlay.querySelector('#btn-save-settings');

    // Cancel button
    overlay.querySelector('#btn-cancel-settings').addEventListener('click', () => {
        overlay.remove();
    });

    /**
     * Validates the settings form fields.
     * @returns {boolean} Whether the form is valid.
     */
    function validateForm() {
        let isValid = true;

        // Validate API URL format (must be relative path or valid URL)
        const apiUrl = apiUrlInput.value.trim();
        if (!apiUrl) {
            isValid = false;
        }

        // Validate timeout is within range
        const timeout = parseInt(timeoutInput.value, 10);
        if (isNaN(timeout) || timeout < 1000 || timeout > 300000) {
            isValid = false;
        }

        // Validate URLs if provided
        const urls = [
            { input: apiUrlInput, name: 'API Base URL' },
            { input: workerUrlInput, name: 'Worker URL' }
        ];

        for (const { input, name } of urls) {
            const value = input.value.trim();
            if (value && !isValidUrl(value)) {
                input.classList.add('input-error');
                isValid = false;
            } else {
                input.classList.remove('input-error');
            }
        }

        return isValid;
    }

    /**
     * Checks if a string is a valid URL or relative path.
     * @param {string} str - String to validate.
     * @returns {boolean} Whether the string is a valid URL or relative path.
     */
    function isValidUrl(str) {
        try {
            new URL(str);
            return true;
        } catch {
            // Allow relative paths like /api
            return str.startsWith('/');
        }
    }

    // Real-time validation
    [apiUrlInput, workerUrlInput, timeoutInput].forEach(input => {
        input.addEventListener('input', () => {
            if (input.classList.contains('input-error')) {
                validateForm();
            }
        });
    });

    // Form submission
    form.addEventListener('submit', async (e) => {
        e.preventDefault();

        if (!validateForm()) {
            apiUrlInput.focus();
            return;
        }

        const settings = {
            apiUrl: apiUrlInput.value.trim(),
            workerUrl: workerUrlInput.value.trim(),
            timeout: parseInt(timeoutInput.value, 10)
        };

        saveBtn.disabled = true;
        saveBtn.textContent = 'Saving...';

        try {
            // Save settings to localStorage for persistence
            localStorage.setItem('ai-music-workstation-settings', JSON.stringify(settings));

            // Dispatch event for external listeners
            document.dispatchEvent(new CustomEvent('modal:settings-save', {
                bubbles: true,
                cancelable: true,
                detail: settings
            }));

            // Call save callback if provided
            if (typeof onSave === 'function') {
                onSave(settings);
            }

            overlay.remove();
        } catch (error) {
            console.error('[Modal] Failed to save settings:', error);
            saveBtn.disabled = false;
            saveBtn.textContent = 'Save';
        }
    });

    // Focus the first input
    apiUrlInput.focus();

    return overlay;
}

// ==================== Export Utility ====================

/**
 * Closes all currently open modals.
 */
export function closeAllModals() {
    const overlays = document.querySelectorAll('.modal-overlay');
    overlays.forEach(overlay => {
        overlay.classList.remove('visible');
        setTimeout(() => overlay.remove(), 200);
    });
}
