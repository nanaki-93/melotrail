// js/waveform.js - Waveform canvas renderer
export class WaveformRenderer {
    constructor() {
        this.canvas = document.getElementById('waveform-canvas');
        this.ctx = this.canvas?.getContext('2d');
        this.samples = [];
    }

    render(waveform) {
        this.samples = waveform.samples;
        this.draw();
    }

    draw() {
        if (!this.ctx || !this.canvas || this.samples.length === 0) return;

        const ctx = this.ctx;
        const canvas = this.canvas;

        // Set canvas size
        const rect = canvas.parentElement.getBoundingClientRect();
        canvas.width = rect.width - 32; // Account for padding
        canvas.height = 120;

        const width = canvas.width;
        const height = canvas.height;
        const midY = height / 2;

        // Clear
        ctx.fillStyle = '#1a1a2e';
        ctx.fillRect(0, 0, width, height);

        // Draw center line
        ctx.strokeStyle = '#2a2a4a';
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(0, midY);
        ctx.lineTo(width, midY);
        ctx.stroke();

        // Draw waveform
        const sliceWidth = width / this.samples.length;
        ctx.fillStyle = '#e94560';

        for (let i = 0; i < this.samples.length; i++) {
            const amplitude = this.samples[i];
            const barHeight = amplitude * (height / 2);
            const x = i * sliceWidth;

            ctx.fillRect(x, midY - barHeight, sliceWidth - 1, barHeight * 2);
        }
    }

    updatePlayhead(position) {
        if (!this.ctx || !this.canvas || this.samples.length === 0) return;

        const ctx = this.ctx;
        const canvas = this.canvas;
        const width = canvas.width;
        const midY = canvas.height / 2;

        // Redraw waveform
        this.draw();

        // Draw playhead
        const playheadX = position * width;
        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.moveTo(playheadX, 0);
        ctx.lineTo(playheadX, canvas.height);
        ctx.stroke();
    }
}
