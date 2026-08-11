// js/project.js - Project list management
import { api } from './api.js';

export class ProjectManager {
    constructor() {
        this.onProjectSelect = null;
    }

    renderList(projects) {
        const list = document.getElementById('project-list');
        if (!list) return;

        list.innerHTML = projects.map(p => `
            <li class="project-item" data-id="${p.id}">
                <div class="project-item-title">${p.title}</div>
                <div class="project-item-meta">${p.artist || 'Unknown Artist'}</div>
            </li>
        `).join('');

        // Add click handlers
        list.querySelectorAll('.project-item').forEach(item => {
            item.addEventListener('click', () => {
                const id = item.dataset.id;
                const project = projects.find(p => p.id === id);
                if (project && this.onProjectSelect) {
                    this.onProjectSelect(project);
                }
            });
        });
    }

    selectProject(project) {
        document.querySelectorAll('.project-item').forEach(item => {
            item.classList.toggle('active', item.dataset.id === project.id);
        });
    }

    showNewProjectModal() {
        const modal = document.getElementById('modal-new-project');
        if (modal) {
            modal.style.display = 'flex';
        }
    }

    hideNewProjectModal() {
        const modal = document.getElementById('modal-new-project');
        if (modal) {
            modal.style.display = 'none';
        }
    }
}
