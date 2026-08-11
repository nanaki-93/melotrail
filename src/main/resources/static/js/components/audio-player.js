/**
 * Audio Player Component - HTML5 Audio-based playback controller.
 *
 * Features:
 * - Play/pause/stop controls
 * - Seek/scrubbing support
 * - Time tracking with position and duration getters
 * - onTimeUpdate callback fires during playback
 * - onEnded callback fires when playback completes
 * - Integrates with waveform playback position indicator
 *
 * Principles:
 * - Single Responsibility: Only handles audio playback mechanics
 * - Open/Closed: Extensible via callback hooks (onTimeUpdate, onEnded)
 * - Resource Management: Wraps native Audio element with clean lifecycle
 */

/**
 * HTML5 Audio-based player with playback controls and time tracking.
 */
export class AudioPlayer {
    /**
     * Creates a new AudioPlayer instance.
     */
    constructor() {
        this.audio = new Audio();
        this.isPlaying = false;
        this.onTimeUpdate = null;
        this.onEnded = null;

        this.audio.addEventListener('timeupdate', () => {
            if (this.onTimeUpdate) {
                this.onTimeUpdate(this.position, this.duration);
            }
        });

        this.audio.addEventListener('ended', () => {
            this.isPlaying = false;
            if (this.onEnded) {
                this.onEnded();
            }
        });
    }

    /**
     * Sets the audio source URL and loads the media.
     * @param {string} url - The audio file URL.
     */
    setSource(url) {
        this.audio.src = url;
        this.audio.load();
    }

    /**
     * Starts playback.
     */
    play() {
        this.audio.play();
        this.isPlaying = true;
    }

    /**
     * Pauses playback.
     */
    pause() {
        this.audio.pause();
        this.isPlaying = false;
    }

    /**
     * Stops playback and resets position to beginning.
     */
    stop() {
        this.audio.pause();
        this.audio.currentTime = 0;
        this.isPlaying = false;
    }

    /**
     * Seeks to a position based on a percentage of the total duration.
     * @param {number} percent - Seek position as a fraction (0.0 to 1.0).
     */
    seek(percent) {
        this.audio.currentTime = percent * this.audio.duration;
    }

    /**
     * Gets the current playback position in seconds.
     * @returns {number} Current position in seconds.
     */
    get position() {
        return this.audio.currentTime;
    }

    /**
     * Gets the total duration of the audio in seconds.
     * @returns {number} Duration in seconds.
     */
    get duration() {
        return this.audio.duration;
    }
}
