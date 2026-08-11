/**
 * Waveform Renderer Component - HTML5 Canvas-based waveform visualization.
 *
 * Features:
 * - Left/right channel waveforms with distinct colors
 * - Playback position indicator (vertical line)
 * - Responsive to container resize
 * - HiDPI/Retina display support
 * - Empty state when no waveform data
 *
 * Principles:
 * - Single Responsibility: Only handles waveform canvas rendering
 * - Open/Closed: Extensible via update() method without modifying internals
 * - Resource Management: Proper cleanup via destroy() pattern
 */

/**
 * Waveform data structure expected by the renderer.
 * @typedef {Object} WaveformData
 * @property {number[]} leftChannel - Left channel amplitude samples (-1 to 1).
 * @property {number[]} rightChannel - Right channel amplitude samples (-1 to 1).
 * @property {number} duration - Total duration in seconds.
 * @property {boolean} isEmpty - Whether the waveform is empty/no data.
 */

/**
 * Canvas-based waveform renderer for audio visualization.
 */
export class WaveformRenderer {
    /**
     * Creates a new WaveformRenderer instance.
     * @param {HTMLCanvasElement} canvas - The canvas element to render on.
     * @param {WaveformData|null} data - Initial waveform data.
     * @param {number} playbackPosition - Current playback position in seconds.
     * @param {Function|null} onSeek - Callback for seek events (x, position).
     */
    constructor(canvas, data, playbackPosition, onSeek) {
        this.canvas = canvas;
        this.ctx = canvas.getContext('2d');
        this.data = data;
        this.playbackPosition = playbackPosition;
        this.onSeek = onSeek;
        this.width = 0;
        this.height = 0;
        this.resizeObserver = null;

        this.resize();
        this.bindCanvasClick();
        this.observeResize();
    }

    /**
     * Sets up a ResizeObserver to handle container size changes.
     * @private
     */
    observeResize() {
        if (typeof ResizeObserver !== 'undefined' && this.canvas.parentElement) {
            this.resizeObserver = new ResizeObserver(() => {
                this.resize();
            });
            this.resizeObserver.observe(this.canvas.parentElement);
        }
    }

    /**
     * Handles canvas click events for seeking.
     * @private
     */
    bindCanvasClick() {
        this.canvas.addEventListener('click', (event) => {
            if (!this.onSeek || !this.data || this.data.isEmpty) return;

            const rect = this.canvas.getBoundingClientRect();
            const x = event.clientX - rect.left;
            const position = (x / rect.width) * this.data.duration;
            this.onSeek(x, position);
        });
    }

    /**
     * Resizes the canvas to fit its container with HiDPI support.
     */
    resize() {
        const rect = this.canvas.parentElement?.getBoundingClientRect();
        if (!rect || rect.width === 0 || rect.height === 0) return;

        const dpr = window.devicePixelRatio || 1;
        this.canvas.width = rect.width * dpr;
        this.canvas.height = 120 * dpr;
        this.ctx.scale(dpr, dpr);
        this.width = rect.width;
        this.height = 120;
        this.draw();
    }

    /**
     * Draws the waveform on the canvas.
     */
    draw() {
        const ctx = this.ctx;
        const { width, height } = this;
        const centerY = height / 2;

        ctx.clearRect(0, 0, width, height);

        // Empty state
        if (!this.data || this.data.isEmpty) {
            ctx.strokeStyle = 'rgba(255,255,255,0.3)';
            ctx.lineWidth = 1;
            ctx.beginPath();
            ctx.moveTo(0, centerY);
            ctx.lineTo(width, centerY);
            ctx.stroke();
            return;
        }

        // Draw waveform channels
        const sampleWidth = width / this.data.leftChannel.length;
        for (let i = 0; i < this.data.leftChannel.length; i++) {
            const x = i * sampleWidth;
            const top = centerY + this.data.leftChannel[i] * centerY;
            const rightVal = this.data.rightChannel?.[i] ?? this.data.leftChannel[i];
            const bottom = centerY + rightVal * centerY;

            ctx.strokeStyle = '#e94560';
            ctx.lineWidth = 1;
            ctx.beginPath();
            ctx.moveTo(x, top);
            ctx.lineTo(x + sampleWidth, bottom);
            ctx.stroke();
        }

        // Playback position indicator
        if (this.data.duration > 0 && this.playbackPosition >= 0) {
            const posX = (this.playbackPosition / this.data.duration) * width;
            ctx.strokeStyle = 'white';
            ctx.lineWidth = 2;
            ctx.beginPath();
            ctx.moveTo(posX, 0);
            ctx.lineTo(posX, height);
            ctx.stroke();
        }
    }

    /**
     * Updates waveform data and playback position for live updates.
     * @param {WaveformData|null} data - New waveform data.
     * @param {number} playbackPosition - New playback position in seconds.
     */
    update(data, playbackPosition) {
        this.data = data;
        this.playbackPosition = playbackPosition;
        this.draw();
    }

    /**
     * Cleans up observers and event listeners.
     */
    destroy() {
        if (this.resizeObserver) {
            this.resizeObserver.disconnect();
            this.resizeObserver = null;
        }
        this.canvas.removeEventListener('click', this._clickHandler);
        this.canvas = null;
        this.ctx = null;
        this.onSeek = null;
    }
}
