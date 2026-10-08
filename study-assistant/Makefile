PYTHON ?= python3

.PHONY: test demo build ui clean

test:
	$(PYTHON) -m pytest

demo:
	$(PYTHON) -m study_assistant.cli demo --root var/demo

build: test
	$(PYTHON) -m build --sdist --wheel

ui:
	study-assistant-ui

clean:
	rm -rf build dist .pytest_cache .coverage
