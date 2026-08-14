SHELL := /bin/bash
GRADLE := ./gradlew
# Python 3.12 is installed by default on the supported macOS setup. Override
# this if a different compatible interpreter is required, e.g.
# `make worker PYTHON=python3.11`.
PYTHON ?= python3.12
VENV ?= .venv
VENV_PYTHON := $(VENV)/bin/python
WORKER_REQUIREMENTS := worker/requirements.txt
WORKER_DEPS_STAMP := $(VENV)/.worker-deps-installed
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
	@echo "  make worker                        Set up and start the Python worker on :8081"
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

worker: $(WORKER_DEPS_STAMP)
	$(VENV_PYTHON) -m worker.main --host $(WORKER_HOST) --port $(WORKER_PORT)

frontend:
	$(PYTHON) tools/frontend_server.py --host $(FRONTEND_HOST) --port $(FRONTEND_PORT)

python-install: $(WORKER_DEPS_STAMP)

$(VENV_PYTHON):
	$(PYTHON) -m venv $(VENV)

$(WORKER_DEPS_STAMP): $(VENV_PYTHON) $(WORKER_REQUIREMENTS)
	$(VENV_PYTHON) -m pip install --upgrade pip
	$(VENV_PYTHON) -m pip install -r $(WORKER_REQUIREMENTS)
	touch $(WORKER_DEPS_STAMP)

clean:
	$(GRADLE) clean
