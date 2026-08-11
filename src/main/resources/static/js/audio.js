// js/audio.js - Audio playback controller
export class AudioPlayer {
    constructor() {
        this.audio = new Audio();
        this.isPlaying = false;
        this.source = null;

        this.audio.addEventListener('timeupdate', () => {
            this.updateTimeDisplay();
            this.updateSeekbar();
            window.dispatchEvent(new CustomEvent('audio:timeupdate', {
                detail: { current: this.audio.currentTime, duration: this.audio.duration }
            }));
        });

        this.audio.addEventListener('ended', () => {
            this.isPlaying = false;
            window.dispatchEvent(new CustomEvent('audio:ended'));
        });
    }

    setSource(url) {
        this.source = url;
        this.audio.src = url;
    }

    play() {
        if (this.audio.src) {
            this.audio.play();
            this.isPlaying = true;
        }
    }

    pause() {
        this.audio.pause();
        this.isPlaying = false;
    }

    stop() {
        this.audio.pause();
        this.audio.currentTime = 0;
        this.isPlaying = false;
    }

    seek(percent) {
        if (this.audio.duration) {
            this.audio.currentTime = percent * this.audio.duration;
        }
    }

    updateTimeDisplay() {
        const display = document.getElementById('time-display');
        if (display && this.audio.duration) {
            display.textContent = `${this.formatTime(this.audio.currentTime)} / ${this.formatTime(this.audio.duration)}`;
        }
    }

    updateSeekbar() {
        const seekBar = document.getElementById('seek-bar');
        if (seekBar && this.audio.duration) {
            seekBar.value = (this.audio.currentTime / this.audio.duration) * 100;
        }
    }

    formatTime(seconds) {
        const mins = Math.floor(seconds / 60);
        const secs = Math.floor(seconds % 60);
        return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
    }
}
