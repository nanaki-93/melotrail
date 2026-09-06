SHELL := /bin/bash
GRADLE := ./gradlew

.PHONY: help build test check desktop clean

help:
	@echo "Melotrail"
	@echo ""
	@echo "Kotlin/Compose:"
	@echo "  make build                         Build the application"
	@echo "  make test                          Run tests"
	@echo "  make check                         Run all verification tasks"
	@echo "  make desktop                       Start the Compose Desktop application"
	@echo ""
build:
	$(GRADLE) build

test:
	$(GRADLE) test

check:
	$(GRADLE) check

desktop:
	$(GRADLE) :desktopApp:run

clean:
	$(GRADLE) clean
