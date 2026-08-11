/**
 * Sidebar Component - Project list with create/delete functionality.
 *
 * Principles:
 * - Single Responsibility: Manages only the sidebar UI and project list rendering
 * - Open/Closed: Extensible via event system without modifying internals
 * - Resource Management: Proper cleanup via destroy() pattern
 */

import { store } from '../state.js';
import { api } from '../api.js';

/**
 * Sidebar component displaying the project list with CRUD operations.
 */
export class Sidebar {
    /**
     * Creates a new Sidebar instance.
     */
    constructor() {
        this.element = null;
        this.unsubscribe = null;
    }

    /**
     * Renders the sidebar element and binds events.
     * @returns {HTMLElement} The rendered sidebar element.
     */
    render() {
        this.element = document.createElement('aside');
        this.element.className = 'app-sidebar';
        this.element.innerHTML = `
            <div class="sidebar-header">
                <div class="sidebar-header-content">
                    <h2>Projects</h2>
                    <span id="project-count" class="project-count">0</span>
                </div>
                <button id="btn-toggle-sidebar" class="btn-icon sidebar-toggle" title="Toggle Sidebar">×</button>
            </div>
            <div id="project-list" class="project-list"></div>
            <div class="sidebar-footer">
                <button id="btn-new-project-sidebar" class="btn btn-primary btn-full">+ New Project</button>
            </div>
        `;
        this.bindEvents();
        this.subscribeToState();
        return this.element;
    }

    /**
     * Binds click event handlers to sidebar elements.
     * @private
     */
    bindEvents() {
        const toggleBtn = this.element?.querySelector('#btn-toggle-sidebar');
        const newProjectBtn = this.element?.querySelector('#btn-new-project-sidebar');

        toggleBtn?.addEventListener('click', () => {
            this.onToggleSidebar();
        });

        newProjectBtn?.addEventListener('click', () => {
            this.onNewProject();
        });

        this.updateProjectList();
    }

    /**
     * Subscribes to projects state changes for reactive updates.
     * @private
     */
    subscribeToState() {
        this.unsubscribe = store.subscribe('projects', (projects) => {
            this.updateProjectList();
        });
    }

    /**
     * Updates the project list DOM based on current state.
     * @private
     */
    updateProjectList() {
        const projects = store.get('projects') || [];
        const currentProject = store.get('currentProject');
        const list = this.element?.querySelector('#project-list');
        const count = this.element?.querySelector('#project-count');

        if (!list) return;

        // Update project count
        if (count) {
            count.textContent = projects.length;
        }

        // Render project items
        list.innerHTML = projects.map(project => {
            const isActive = currentProject?.id === project.id;
            return `
                <div class="project-item ${isActive ? 'active' : ''}" data-id="${project.id}">
                    <span class="project-title">${this.escapeHtml(project.title || 'Untitled')}</span>
                    <button class="btn-delete" data-id="${project.id}" title="Delete project">×</button>
                </div>
            `;
        }).join('');

        this.bindProjectEvents();
    }

    /**
     * Binds events to project list items (click and delete).
     * @private
     */
    bindProjectEvents() {
        const projectItems = this.element?.querySelectorAll('.project-item');
        const deleteButtons = this.element?.querySelectorAll('.btn-delete');

        // Project click handler - select project
        projectItems?.forEach(item => {
            item.addEventListener('click', (e) => {
                // Don't trigger if clicking delete button
                if (e.target.classList.contains('btn-delete')) return;

                const projectId = item.dataset.id;
                const projects = store.get('projects') || [];
                const project = projects.find(p => p.id === projectId);

                if (project) {
                    this.onSelectProject(project);
                }
            });
        });

        // Delete button handler
        deleteButtons?.forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                const projectId = btn.dataset.id;
                this.onDeleteProject(projectId);
            });
        });
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
     * Handles project selection - sets current project and dispatches event.
     * @param {object} project - The selected project object.
     * @private
     */
    onSelectProject(project) {
        store.set('currentProject', project);
        this.element?.dispatchEvent(new CustomEvent('sidebar:project-select', {
            bubbles: true,
            cancelable: true,
            detail: project
        }));
    }

    /**
     * Handles project deletion via API call.
     * @param {string} projectId - ID of the project to delete.
     * @private
     */
    async onDeleteProject(projectId) {
        if (!confirm('Are you sure you want to delete this project?')) {
            return;
        }

        try {
            await api.deleteProject(projectId);

            // Remove from store
            const projects = store.get('projects') || [];
            const filtered = projects.filter(p => p.id !== projectId);
            store.set('projects', filtered);

            // If deleted project was current, clear current project
            const currentProject = store.get('currentProject');
            if (currentProject?.id === projectId) {
                store.set('currentProject', null);
            }

            // Dispatch delete event
            this.element?.dispatchEvent(new CustomEvent('sidebar:project-delete', {
                bubbles: true,
                cancelable: true,
                detail: { id: projectId }
            }));
        } catch (error) {
            console.error('[Sidebar] Failed to delete project:', error);
        }
    }

    /**
     * Toggles sidebar open/closed state.
     * @private
     */
    onToggleSidebar() {
        const currentOpen = store.get('ui')?.sidebarOpen ?? true;
        store.partialUpdate('ui', { sidebarOpen: !currentOpen });

        // Toggle collapsed class on sidebar
        this.element?.classList.toggle('collapsed', !currentOpen);
    }

    /**
     * Dispatches new project creation event.
     * @private
     */
    onNewProject() {
        this.element?.dispatchEvent(new CustomEvent('sidebar:new-project', {
            bubbles: true,
            cancelable: true
        }));
    }

    /**
     * Sets the collapsed state of the sidebar.
     * @param {boolean} collapsed - Whether the sidebar should be collapsed.
     */
    setCollapsed(collapsed) {
        if (this.element) {
            this.element.classList.toggle('collapsed', collapsed);
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
