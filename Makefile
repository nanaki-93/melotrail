SHELL := /bin/bash
GRADLE := ./gradlew

.PHONY: help build test check desktop video clean

help:
	@echo "Melotrail"
	@echo ""
	@echo "Kotlin/Compose:"
	@echo "  make build                         Build the application"
	@echo "  make test                          Run tests"
	@echo "  make check                         Run all verification tasks"
	@echo "  make desktop                       Start the Compose Desktop application"
	@echo ""
	@echo "TABI video companion (macOS 14+ and Xcode command-line tools):"
	@echo '  make video VIDEO_REQUEST="/path/to/composition-request.json"'
	@echo '    Optional: VIDEO_JOBS="/path/to/animation-jobs.json"'
	@echo ""
build:
	$(GRADLE) build

test:
	$(GRADLE) test

check:
	$(GRADLE) check

desktop:
	$(GRADLE) :desktopApp:run

video: export VIDEO_REQUEST := $(VIDEO_REQUEST)
video: export VIDEO_JOBS := $(VIDEO_JOBS)
video:
	@if [ -z "$$VIDEO_REQUEST" ]; then \
		echo 'Usage: make video VIDEO_REQUEST="/path/to/composition-request.json" [VIDEO_JOBS="/path/to/animation-jobs.json"]' >&2; \
		exit 2; \
	fi; \
	args=("$$VIDEO_REQUEST"); \
	if [ -n "$$VIDEO_JOBS" ]; then args+=("$$VIDEO_JOBS"); fi; \
	swift run --package-path companion -c release melotrail-tabi-editor "$${args[@]}"

clean:
	$(GRADLE) clean
