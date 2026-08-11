/**
 * ProjectsView - Displays all projects with create, delete, and select actions.
 *
 * Principles:
 * - Single Responsibility: Manages only the projects list UI
 * - Reactive: Subscribes to state changes for live updates
 * - Resource Management: Proper cleanup via destroy() pattern
 * - XSS Prevention: All user content is escaped before rendering
 */

import { api } from '../api.js';
import { store } from '../state.js';
import { navigate } from '../router.js';

/**
 * View for displaying the project list with CRUD operations.
 */
export class ProjectsView {
    /**
     * Creates a new ProjectsView instance.
     */
    constructor() {
        this.element = null;
        this.unsubscribeProjects = null;
        this.unsubscribeCurrentProject = null;
    }

    /**
     * Renders the projects view and binds events.
     * @returns {HTMLElement} The rendered view element.
     */
    render() {
        this.element = document.createElement('div');
        this.element.className = 'projects-view';

        this.renderContent();
        this.bindEvents();
        this.subscribeToState();
        this.loadProjects();

        return this.element;
    }

    /**
     * Renders the main content of the projects view.
     * @private
     */
    renderContent() {
        const projects = store.get('projects') || [];

        this.element.innerHTML = `
            <div class="projects-view-header">
                <h1 class="view-title">My Projects</h1>
                <button id="btn-create-project" class="btn btn-primary btn-create">
                    <span class="btn-icon-plus">+</span>
                    New Project
                </button>
            </div>
            <div id="projects-grid" class="projects-grid">
                ${this.renderProjectCards(projects)}
            </div>
            <div id="projects-empty" class="projects-empty" style="display: none;">
                <div class="empty-state">
                    <svg class="empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
                              d="M9 19V6l12-3v13M9 19c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zm12-3c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zM9 10l12-3"/>
                    </svg>
                    <h2>No projects yet</h2>
                    <p>Create your first project to get started with AI-powered music production.</p>
                    <button id="btn-create-empty" class="btn btn-primary">Create Project</button>
                </div>
            </div>
        `;

        this.updateEmptyState(projects);
    }

    /**
     * Renders project cards as HTML string.
     * @param {Array} projects - Array of project objects.
     * @returns {string} HTML string of project cards.
     * @private
     */
    renderProjectCards(projects) {
        if (!projects || projects.length === 0) {
            return '';
        }

        return projects.map(project => `
            <div class="project-card" data-id="${project.id}">
                <div class="project-card-content">
                    <h3 class="project-card-title">${this.escapeHtml(project.title || 'Untitled')}</h3>
                    <div class="project-card-meta">
                        <span class="project-card-artist">${this.escapeHtml(project.artist || 'Unknown Artist')}</span>
                        ${project.createdAt ? `<span class="project-card-date">${this.formatDate(project.createdAt)}</span>` : ''}
                    </div>
                </div>
                <div class="project-card-actions">
                    <button class="btn-icon btn-open-project" data-id="${project.id}" title="Open project">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" width="16" height="16">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                  d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/>
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                  d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"/>
                        </svg>
                    </button>
                    <button class="btn-icon btn-delete-project" data-id="${project.id}" title="Delete project">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" width="16" height="16">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                  d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/>
                        </svg>
                    </button>
                </div>
            </div>
        `).join('');
    }

    /**
     * Updates the empty state visibility based on project count.
     * @param {Array} projects - Array of project objects.
     * @private
     */
    updateEmptyState(projects) {
        const grid = this.element?.querySelector('#projects-grid');
        const empty = this.element?.querySelector('#projects-empty');

        if (!grid || !empty) return;

        const hasProjects = projects && projects.length > 0;
        grid.style.display = hasProjects ? 'grid' : 'none';
        empty.style.display = hasProjects ? 'none' : 'block';
    }

    /**
     * Binds click event handlers to the view.
     * @private
     */
    bindEvents() {
        // Create project buttons
        const createBtn = this.element?.querySelector('#btn-create-project');
        const createEmptyBtn = this.element?.querySelector('#btn-create-empty');

        const handleCreate = () => {
            this.element?.dispatchEvent(new CustomEvent('view:create-project', {
                bubbles: true,
                cancelable: true
            }));
        };

        createBtn?.addEventListener('click', handleCreate);
        createEmptyBtn?.addEventListener('click', handleCreate);

        // Open project buttons
        this.element?.querySelectorAll('.btn-open-project').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                const projectId = btn.dataset.id;
                this.openProject(projectId);
            });
        });

        // Delete project buttons
        this.element?.querySelectorAll('.btn-delete-project').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                const projectId = btn.dataset.id;
                this.deleteProject(projectId);
            });
        });

        // Project card clicks
        this.element?.querySelectorAll('.project-card').forEach(card => {
            card.addEventListener('click', () => {
                const projectId = card.dataset.id;
                this.openProject(projectId);
            });
        });
    }

    /**
     * Subscribes to state changes for reactive updates.
     * @private
     */
    subscribeToState() {
        this.unsubscribeProjects = store.subscribe('projects', (projects) => {
            this.updateProjectList(projects);
        });

        this.unsubscribeCurrentProject = store.subscribe('currentProject', () => {
            // Trigger re-render to update active states
            const projects = store.get('projects') || [];
            this.updateProjectList(projects);
        });
    }

    /**
     * Updates the project list DOM with new data.
     * @param {Array} projects - Array of project objects.
     * @private
     */
    updateProjectList(projects) {
        const grid = this.element?.querySelector('#projects-grid');
        const empty = this.element?.querySelector('#projects-empty');

        if (!grid || !empty) return;

        const hasProjects = projects && projects.length > 0;
        grid.innerHTML = this.renderProjectCards(projects);
        this.updateEmptyState(projects);

        // Re-bind events for new elements
        this.rebindProjectEvents();
    }

    /**
     * Re-binds events to project cards after content update.
     * @private
     */
    rebindProjectEvents() {
        // Open project buttons
        this.element?.querySelectorAll('.btn-open-project').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                const projectId = btn.dataset.id;
                this.openProject(projectId);
            });
        });

        // Delete project buttons
        this.element?.querySelectorAll('.btn-delete-project').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                const projectId = btn.dataset.id;
                this.deleteProject(projectId);
            });
        });

        // Project card clicks
        this.element?.querySelectorAll('.project-card').forEach(card => {
            card.addEventListener('click', () => {
                const projectId = card.dataset.id;
                this.openProject(projectId);
            });
        });
    }

    /**
     * Loads projects from the API.
     * @private
     */
    async loadProjects() {
        try {
            const projects = await api.getProjects();
            store.set('projects', projects);
        } catch (error) {
            console.error('[ProjectsView] Failed to load projects:', error);
            store.set('projects', []);
        }
    }

    /**
     * Opens a project by navigating to its workspace view.
     * @param {string} projectId - The project ID to open.
     * @private
     */
    openProject(projectId) {
        navigate(`/project/${projectId}`);
    }

    /**
     * Deletes a project after confirmation.
     * @param {string} projectId - The project ID to delete.
     * @private
     */
    async deleteProject(projectId) {
        if (!confirm('Are you sure you want to delete this project? This action cannot be undone.')) {
            return;
        }

        try {
            await api.deleteProject(projectId);

            // Update store
            const projects = store.get('projects') || [];
            const filtered = projects.filter(p => p.id !== projectId);
            store.set('projects', filtered);

            // If deleted project was current, clear it
            const currentProject = store.get('currentProject');
            if (currentProject?.id === projectId) {
                store.set('currentProject', null);
            }

            // Navigate back to projects list if we were on this project
            if (currentProject?.id === projectId) {
                navigate('/');
            }
        } catch (error) {
            console.error('[ProjectsView] Failed to delete project:', error);
            alert('Failed to delete project: ' + error.message);
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
     * Formats a date string for display.
     * @param {string|number} date - Date string or timestamp.
     * @returns {string} Formatted date string.
     * @private
     */
    formatDate(date) {
        try {
            const d = new Date(date);
            return d.toLocaleDateString('en-US', {
                year: 'numeric',
                month: 'short',
                day: 'numeric'
            });
        } catch {
            return '';
        }
    }

    /**
     * Cleans up event listeners and subscriptions.
     */
    destroy() {
        this.unsubscribeProjects?.();
        this.unsubscribeCurrentProject?.();
        this.element = null;
    }
}
