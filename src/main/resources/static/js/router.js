/**
 * Router - Simple client-side routing for the application.
 *
 * Principles:
 * - Single Responsibility: Manages only URL-to-view mapping and navigation
 * - History API: Uses pushState/popstate for proper browser back/forward
 * - Open/Closed: Easy to add new routes without modifying existing logic
 *
 * Routes:
 * - '/' or '' → Projects List View
 * - '/project/:id' → Individual Project Workspace View
 */

import { ProjectsView } from './views/projects-view.js';
import { ProjectView } from './views/project-view.js';

/**
 * Route definitions mapping URL patterns to view instances.
 */
const routes = [
    {
        pattern: /^\/$/,
        render: () => new ProjectsView()
    },
    {
        pattern: /^\/project\/([^/]+)$/,
        render: (match) => new ProjectView(match[1])
    }
];

/**
 * Current active view instance.
 * @type {object|null}
 */
let activeView = null;

/**
 * Optional container element for view rendering.
 * When set, views render inside this element instead of #app.
 * @type {HTMLElement|null}
 */
let _container = null;

/**
 * Navigates to a given path, rendering the appropriate view.
 * @param {string} path - The URL path to navigate to.
 * @param {boolean} pushState - Whether to push to history (true) or replace (false).
 */
export function navigate(path, pushState = true) {
    const target = _container || document.getElementById('app');
    if (!target) {
        console.error('[Router] Target container not found');
        return;
    }

    // Destroy the current view
    if (activeView) {
        activeView.destroy();
        activeView = null;
    }

    // Clear the target container
    if (!_container) {
        target.innerHTML = '';
    } else {
        target.innerHTML = '';
    }

    // Match the path against registered routes
    let matched = false;
    for (const route of routes) {
        const match = path.match(route.pattern);
        if (match) {
            activeView = route.render(match);
            const viewElement = activeView.render();
            target.appendChild(viewElement);
            matched = true;
            break;
        }
    }

    if (!matched) {
        // Fallback: render projects view for unknown routes
        activeView = new ProjectsView();
        target.appendChild(activeView.render());
    }

    // Update browser history
    if (pushState) {
        history.pushState({ path }, '', path);
    }
}

/**
 * Navigates back in browser history.
 */
export function goBack() {
    history.back();
}

/**
 * Gets the current active view instance.
 * @returns {object|null} The active view or null.
 */
export function getActiveView() {
    return activeView;
}

/**
 * Initializes the router by handling the current URL and popstate events.
 * @param {HTMLElement|null} container - Optional container element for view rendering.
 */
export function initRouter(container = null) {
    _container = container;

    // Handle browser back/forward
    window.addEventListener('popstate', (event) => {
        const path = event.state?.path || location.pathname;
        navigate(path, false);
    });

    // Handle link clicks (delegated)
    document.addEventListener('click', (event) => {
        const link = event.target.closest('a[data-navigate]');
        if (link) {
            event.preventDefault();
            const path = link.getAttribute('href');
            navigate(path);
        }
    });

    // Initial navigation
    const initialPath = location.pathname;
    navigate(initialPath, false);
}

// Export views for external use
export { ProjectsView, ProjectView };
