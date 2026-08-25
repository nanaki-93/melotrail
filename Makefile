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
LIVE_E2E_TEST_CLASS := app.melotrail.desktop.LiveLoFiFiveSourceEndToEndTest
LIVE_E2E_INPUTS := intro-C.wav verse-c.wav chorus-C.wav bridge-C.wav outro-C.wav

.PHONY: help build test worker-test check desktop worker live-e2e python-install verify-worker-python clean

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
	@echo "  make worker-test                   Run offline Python worker tests"
	@echo "  make live-e2e                      Build the opt-in five-source C-major lo-fi WAV project in data/audio (does not clean it)"
	@echo ""
build:
	$(GRADLE) build

test:
	$(GRADLE) test

worker-test: $(WORKER_DEPS_STAMP)
	$(VENV_PYTHON) -m unittest discover -s worker/tests

check:
	$(GRADLE) check

desktop:
	$(GRADLE) :desktopApp:run

worker: $(WORKER_DEPS_STAMP)
	$(VENV_PYTHON) -m worker.main --host $(WORKER_HOST) --port $(WORKER_PORT)

# This test owns data/audio after its five immutable input WAVs. Remove a prior
# project/artifacts yourself before rerunning; this target deliberately never
# deletes user audio or known-good candidates.
live-e2e: $(WORKER_DEPS_STAMP)
	@if [ -e data/audio/project.json ]; then \
		echo "data/audio already contains a project. Preserve or manually clean prior E2E artifacts, leaving data/audio/input/*.wav, then retry."; \
		exit 1; \
	fi
	@for source in $(LIVE_E2E_INPUTS); do \
		if [ ! -f "data/audio/input/$$source" ]; then \
			echo "Missing required E2E source: data/audio/input/$$source"; \
			exit 1; \
		fi; \
	done
	@qwen_model="$${QWEN_MODEL:-qwen}"; \
	qwen_endpoint="$${LM_STUDIO_CHAT_COMPLETIONS_URL:-http://127.0.0.1:1234/v1/chat/completions}"; \
	qwen_models_endpoint="$${qwen_endpoint%/chat/completions}/models"; \
	qwen_models="$$(curl --fail --silent --max-time 5 "$$qwen_models_endpoint")" || { \
		echo "Live E2E requires LM Studio with the Qwen model loaded at $$qwen_endpoint."; \
		exit 1; \
	}; \
	if ! printf '%s' "$$qwen_models" | grep -F '"id": "'"$$qwen_model"'"' >/dev/null; then \
		echo "Live E2E requires loaded Qwen model '$$qwen_model'. Load it in LM Studio, then retry."; \
		exit 1; \
	fi
	@renderer="$${SFZ_RENDERER_PATH:-$$(command -v sfizz_render || true)}"; \
	if [ -z "$$renderer" ] || [ ! -x "$$renderer" ]; then \
		echo "Live E2E requires sfizz_render. Set SFZ_RENDERER_PATH to its executable path."; \
		exit 1; \
	fi; \
	worker_pid=""; \
	stop_worker() { if [ -n "$$worker_pid" ]; then kill "$$worker_pid" 2>/dev/null || true; wait "$$worker_pid" 2>/dev/null || true; fi; }; \
	trap stop_worker EXIT INT TERM; \
	if ! curl --fail --silent --max-time 2 "http://127.0.0.1:8081/health" >/dev/null; then \
		mkdir -p build; \
		$(VENV_PYTHON) -m worker.main --host 127.0.0.1 --port 8081 >build/live-e2e-worker.log 2>&1 & \
		worker_pid="$$!"; \
		for attempt in $$(seq 1 30); do \
			if curl --fail --silent --max-time 2 "http://127.0.0.1:8081/health" >/dev/null; then break; fi; \
			if [ "$$attempt" -eq 30 ]; then echo "Worker did not become healthy; see build/live-e2e-worker.log"; exit 1; fi; \
			sleep 1; \
		done; \
	fi; \
	SFZ_RENDERER_PATH="$$renderer" MELOTRAIL_RUN_LIVE_E2E=1 $(GRADLE) :desktopApp:test --tests "$(LIVE_E2E_TEST_CLASS)"

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
