.DEFAULT_GOAL := help

PNPM ?= pnpm
COMPOSE ?= docker compose

.PHONY: help dev up down logs test verify clean

help: ## Show available commands
	@awk 'BEGIN {FS = ":.*## "; printf "Available commands:\n"} /^[a-zA-Z_-]+:.*## / {printf "  %-12s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

dev: ## Build and run the full local stack in the foreground
	$(COMPOSE) up --build

up: ## Build and start the full local stack in the background
	$(COMPOSE) up --build --detach --wait

down: ## Stop local containers without deleting database data
	$(COMPOSE) down --remove-orphans

logs: ## Follow logs from all local services
	$(COMPOSE) logs --follow

test: ## Run backend and frontend tests
	cd apps/api && ./mvnw verify
	cd apps/web && $(PNPM) test --run

verify: ## Run backend, frontend, and Compose validation
	cd apps/api && ./mvnw verify
	cd apps/web && $(PNPM) install --frozen-lockfile
	cd apps/web && $(PNPM) lint
	cd apps/web && $(PNPM) test --run
	cd apps/web && $(PNPM) build
	$(COMPOSE) config --quiet

clean: ## Remove generated build output and stop containers
	cd apps/api && ./mvnw clean
	rm -rf apps/web/dist apps/web/coverage
	$(COMPOSE) down --remove-orphans
