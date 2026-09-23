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
	@echo "Video workspace (requires the integrated Video tab):"
	@echo '  make video                         Start Melotrail in Video'
	@echo ""
build:
	$(GRADLE) build

test:
	$(GRADLE) test

check:
	$(GRADLE) check

desktop:
	$(GRADLE) :desktopApp:run

video:
	$(GRADLE) :desktopApp:run --args='--video'

clean:
	$(GRADLE) clean
