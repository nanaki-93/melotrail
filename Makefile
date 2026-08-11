SHELL := /bin/bash
GRADLE := ./gradlew
PYTHON ?= python3
WORKER_HOST ?= 127.0.0.1
WORKER_PORT ?= 8081
FRONTEND_HOST ?= 127.0.0.1
FRONTEND_PORT ?= 3000

.PHONY: help build test check run cli cli-help worker frontend python-install clean

help:
	@echo "AI Music Workstation"
	@echo ""
	@echo "Kotlin/Spring:"
	@echo "  make build                         Build the application"
	@echo "  make test                          Run tests"
	@echo "  make check                         Run all verification tasks"
	@echo "  make run                           Start the Kotlin/Spring server"
	@echo "  make cli ARGS='...'                Run the Kotlin CLI"
	@echo ""
	@echo "Python services:"
	@echo "  make worker                        Start the Python worker on :8081"
	@echo "  make frontend                      Serve frontend pages on :3000"
	@echo "  make python-install                Install Python worker dependencies"
	@echo ""
	@echo "Typical development setup (3 terminals):"
	@echo "  make worker"
	@echo "  make run"
	@echo "  make frontend"

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
	$(PYTHON) -m worker.main --host $(WORKER_HOST) --port $(WORKER_PORT)

frontend:
	$(PYTHON) tools/frontend_server.py --host $(FRONTEND_HOST) --port $(FRONTEND_PORT)

python-install:
	$(PYTHON) -m pip install -r worker/requirements.txt

clean:
	$(GRADLE) clean
