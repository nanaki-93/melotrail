SHELL := /bin/bash
GRADLE := ./gradlew

.PHONY: help build test check run cli cli-help worker clean format

help:
	@echo "AI Music Workstation"
	@echo ""
	@echo "  make build                 Build the application"
	@echo "  make test                  Run tests"
	@echo "  make check                 Run all verification tasks"
	@echo "  make run                   Start Spring Boot server"
	@echo "  make cli ARGS='...'        Run the Kotlin CLI"
	@echo "  make cli-help              Show CLI help"
	@echo "  make worker                Start the Python worker"
	@echo "  make clean                 Remove build outputs"

build:
	$(GRADLE) build

test:
	$(GRADLE) test

check:
	$(GRADLE) check

run:
	$(GRADLE) bootRun

cli:
	$(GRADLE) cliRun --args='$(ARGS)'

cli-help:
	$(GRADLE) cliRun --args='--help'

worker:
	@if [ -f worker/main.py ]; then python3 worker/main.py; else python3 ai-worker/worker.py; fi

clean:
	$(GRADLE) clean
