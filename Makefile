SHELL := /bin/bash
GRADLE := ./gradlew
# The supported macOS setup exposes Python 3.12 as `python`. Override this if
# a different compatible interpreter is required, e.g. `make worker PYTHON=python3.11`.
PYTHON ?= python
VENV ?= .venv
VENV_PYTHON := $(VENV)/bin/python
WORKER_REQUIREMENTS := worker/requirements.txt
WORKER_DEPS_STAMP := $(VENV)/.worker-deps-installed
WORKER_HOST ?= 127.0.0.1
WORKER_PORT ?= 8081

.PHONY: help build test check check-legacy-frontend run cli cli-help worker python-install clean

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
	@echo "  make python-install                Install Python worker dependencies"
	@echo ""
	@echo "Typical API development setup (2 terminals):"
	@echo "  make worker"
	@echo "  make run"

build:
	$(GRADLE) build

test:
	$(GRADLE) test

check:
	$(MAKE) check-legacy-frontend
	$(GRADLE) check

check-legacy-frontend:
	bash tools/check_no_legacy_frontend.sh

run:
	$(GRADLE) bootRun

cli:
	$(GRADLE) cliRun --args='$(ARGS)'

cli-help:
	$(GRADLE) cliRun --args='--help'

worker: $(WORKER_DEPS_STAMP)
	$(VENV_PYTHON) -m worker.main --host $(WORKER_HOST) --port $(WORKER_PORT)

python-install: $(WORKER_DEPS_STAMP)

$(VENV_PYTHON):
	$(PYTHON) -m venv $(VENV)

$(WORKER_DEPS_STAMP): $(VENV_PYTHON) $(WORKER_REQUIREMENTS)
	$(VENV_PYTHON) -m pip install --upgrade pip
	$(VENV_PYTHON) -m pip install -r $(WORKER_REQUIREMENTS)
	touch $(WORKER_DEPS_STAMP)

clean:
	$(GRADLE) clean
