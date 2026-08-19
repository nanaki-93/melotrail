SHELL := /bin/bash
GRADLE := ./gradlew
# The unified worker, including Basic Pitch transcription, requires Python 3.11.
# With pyenv, run `pyenv local 3.11` once; `python` will then resolve correctly.
# An isolated environment name avoids reusing a prior Python 3.12 `.venv`.
PYTHON ?= python
REQUIRED_PYTHON_VERSION := 3.11
VENV ?= .venv-worker
VENV_PYTHON := $(VENV)/bin/python
WORKER_REQUIREMENTS := worker/requirements.txt worker/requirements-transcription.txt
WORKER_DEPS_STAMP := $(VENV)/.worker-deps-installed
WORKER_HOST ?= 127.0.0.1
WORKER_PORT ?= 8081

.PHONY: help build test check desktop worker python-install verify-worker-python clean

help:
	@echo "Melotrail"
	@echo ""
	@echo "Kotlin/Compose:"
	@echo "  make build                         Build the application"
	@echo "  make test                          Run tests"
	@echo "  make check                         Run all verification tasks"
	@echo "  make desktop                       Start the Compose Desktop application"
	@echo ""
	@echo "Python services:"
	@echo "  make worker                        Set up and start the complete Python 3.11 worker on :8081"
	@echo "  make python-install                Install all worker dependencies, including Basic Pitch"
	@echo ""
build:
	$(GRADLE) build

test:
	$(GRADLE) test

check:
	$(GRADLE) check

desktop:
	$(GRADLE) :desktopApp:run

worker: $(WORKER_DEPS_STAMP)
	$(VENV_PYTHON) -m worker.main --host $(WORKER_HOST) --port $(WORKER_PORT)

python-install: $(WORKER_DEPS_STAMP)

verify-worker-python:
	@version="$$($(PYTHON) -c 'import sys; print(".".join(map(str, sys.version_info[:2])))')"; \
	if [ "$$version" != "$(REQUIRED_PYTHON_VERSION)" ]; then \
		echo "Basic Pitch requires Python $(REQUIRED_PYTHON_VERSION), but $(PYTHON) is Python $$version."; \
		echo "Set it with pyenv local $(REQUIRED_PYTHON_VERSION) (or run make worker PYTHON=python3.11)."; \
		exit 1; \
	fi

$(VENV_PYTHON):
	$(PYTHON) -m venv $(VENV)

$(WORKER_DEPS_STAMP): $(VENV_PYTHON) $(WORKER_REQUIREMENTS) | verify-worker-python
	@version="$$($(VENV_PYTHON) -c 'import sys; print(".".join(map(str, sys.version_info[:2])))')"; \
	if [ "$$version" != "$(REQUIRED_PYTHON_VERSION)" ]; then \
		echo "$(VENV) uses Python $$version, not Python $(REQUIRED_PYTHON_VERSION). Remove $(VENV) and rerun make."; \
		exit 1; \
	fi
	$(VENV_PYTHON) -m pip install --upgrade pip
	$(VENV_PYTHON) -m pip install $(foreach requirement,$(WORKER_REQUIREMENTS),-r $(requirement))
	touch $(WORKER_DEPS_STAMP)

clean:
	$(GRADLE) clean
