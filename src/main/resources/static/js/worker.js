// js/worker.js - Worker command UI
import { api } from './api.js';

export class WorkerUI {
    constructor() {
        this.currentJobId = null;
        this.stopProgressListener = null;
    }

    async startWorker() {
        try {
            await api.startWorker();
            this.updateStatus(true);
        } catch (e) {
            alert('Failed to start worker: ' + e.message);
        }
    }

    async stopWorker() {
        try {
            await api.stopWorker();
            this.updateStatus(false);
        } catch (e) {
            alert('Failed to stop worker: ' + e.message);
        }
    }

    updateStatus(healthy) {
        const badge = document.getElementById('worker-status');
        if (badge) {
            badge.textContent = `Worker: ${healthy ? 'Running' : 'Stopped'}`;
            badge.className = `status-badge ${healthy ? 'healthy' : ''}`;
        }
    }

    showProgress() {
        const progress = document.getElementById('job-progress');
        if (progress) {
            progress.style.display = 'block';
        }
    }

    hideProgress() {
        const progress = document.getElementById('job-progress');
        if (progress) {
            progress.style.display = 'none';
        }
    }

    updateProgress(percent, message) {
        const fill = document.getElementById('progress-fill');
        const msg = document.getElementById('progress-message');
        if (fill) fill.style.width = `${percent}%`;
        if (msg) msg.textContent = message || 'Processing...';
    }

    async runAnalysis() {
        this.showProgress();
        try {
            const response = await api.submitCommand('analyze', {
                detectBPM: document.getElementById('detect-bpm')?.checked,
                detectKey: document.getElementById('detect-key')?.checked,
                detectLoudness: document.getElementById('detect-loudness')?.checked
            });

            this.currentJobId = response.jobId;
            this.listenForProgress();
        } catch (e) {
            alert('Failed to start analysis: ' + e.message);
            this.hideProgress();
        }
    }

    async applyDSP() {
        this.showProgress();
        try {
            const response = await api.submitCommand('apply_dsp', {
                enableLoFi: document.getElementById('enable-lofi')?.checked,
                amount: document.getElementById('dsp-amount')?.value,
                tape: document.getElementById('dsp-tape')?.value,
                vinyl: document.getElementById('dsp-vinyl')?.value,
                noise: document.getElementById('dsp-noise')?.value
            });

            this.currentJobId = response.jobId;
            this.listenForProgress();
        } catch (e) {
            alert('Failed to apply DSP: ' + e.message);
            this.hideProgress();
        }
    }

    async repair() {
        this.showProgress();
        try {
            const repairs = [];
            if (document.getElementById('repair-clipping')?.checked) repairs.push({type: 'clipping'});
            if (document.getElementById('repair-declick')?.checked) repairs.push({type: 'declick'});
            if (document.getElementById('repair-denorm')?.checked) repairs.push({type: 'denorm'});
            if (document.getElementById('repair-normalize')?.checked) repairs.push({type: 'normalize'});

            const response = await api.submitCommand('repair', { repairs });

            this.currentJobId = response.jobId;
            this.listenForProgress();
        } catch (e) {
            alert('Failed to start repair: ' + e.message);
            this.hideProgress();
        }
    }

    async master() {
        this.showProgress();
        try {
            const response = await api.submitCommand('master', {
                targetLUFS: document.getElementById('master-lufs')?.value,
                truePeak: document.getElementById('master-peak')?.value
            });

            this.currentJobId = response.jobId;
            this.listenForProgress();
        } catch (e) {
            alert('Failed to start mastering: ' + e.message);
            this.hideProgress();
        }
    }

    listenForProgress() {
        if (this.stopProgressListener) {
            this.stopProgressListener();
        }

        this.stopProgressListener = api.onJobProgress(
            this.currentJobId,
            (data) => {
                this.updateProgress(data.progress, `Status: ${data.status}`);
            },
            (error) => {
                this.hideProgress();
                alert('Job failed: ' + error);
            },
            () => {
                this.hideProgress();
            }
        );
    }
}
