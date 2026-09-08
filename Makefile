# Makefile for Hify development and packaging.
# Targets: help start stop restart build-backend build-frontend build clean package
# Run from the project root. start/stop delegate to start.sh / stop.sh.
#
# Note: the frontend package.json also has a "build" script — invoking
# `make build` from inside hify-web/ targets that script, not this file.

SHELL := /bin/bash
ROOT  := $(CURDIR)

# Project version, extracted from the root pom.xml (com.hify groupId block).
# Override on the command line: make package VERSION=1.0.0
VERSION := $(shell grep -A2 '<groupId>com.hify</groupId>' $(ROOT)/pom.xml | sed -n 's:.*<version>\([^<]*\)</version>.*:\1:p' | head -1)

PKG_DIR := $(ROOT)/.package
TARBALL := $(ROOT)/hify-$(VERSION).tar.gz

.PHONY: help start stop restart build-backend build-frontend build clean package

help: ## print available targets
	@echo "Hify make targets:"
	@echo "  make start    启动本地开发环境（检查 MySQL/Redis，后台起后端+前端）"
	@echo "  make stop     优雅停止后端与前端（按 PID 文件，SIGTERM -> SIGKILL）"
	@echo "  make restart  重启：先 stop 再 start"
	@echo "  make build    构建后端（Maven） + 前端（Vite）"
	@echo "  make clean    清理构建产物（mvn clean + 删除 dist/）"
	@echo "  make package  构建后打包可分发 tar.gz：$(notdir $(TARBALL))"

start: ## launch the local dev environment (start.sh)
	$(ROOT)/start.sh

stop: ## gracefully stop backend & frontend (stop.sh)
	$(ROOT)/stop.sh

restart: stop start ## stop, then start

build-backend: ## build the Spring Boot backend, tests skipped
	cd $(ROOT) && mvn -q -B -DskipTests package

build-frontend: ## install deps and build the Vite frontend
	cd $(ROOT)/hify-web && npm install --no-audit --no-fund && npm run build

build: build-backend build-frontend ## build backend and frontend

clean: ## remove build artifacts (maven target/ dirs and frontend dist/)
	cd $(ROOT) && mvn -q clean
	rm -rf $(ROOT)/hify-web/dist
	@echo "cleaned: backend target/ dirs and frontend dist/"

package: build ## build, then pack a distributable tarball
	rm -rf $(PKG_DIR)
	mkdir -p $(PKG_DIR)/hify/backend $(PKG_DIR)/hify/frontend
	cp $(ROOT)/hify-app/target/hify-app-$(VERSION).jar $(PKG_DIR)/hify/backend/
	cp -r $(ROOT)/hify-web/dist/. $(PKG_DIR)/hify/frontend/
	@printf 'Hify %s\n\nbackend : java -jar backend/hify-app-%s.jar\n' '$(VERSION)' '$(VERSION)' > $(PKG_DIR)/hify/README.txt
	@printf 'frontend: serve frontend/ as static files (e.g. nginx); proxy /api to the backend port\n' >> $(PKG_DIR)/hify/README.txt
	@printf '          and enable proxy_buffering off for SSE responses.\n' >> $(PKG_DIR)/hify/README.txt
	tar -czf $(TARBALL) -C $(PKG_DIR) hify
	rm -rf $(PKG_DIR)
	@echo "package created: $(TARBALL)"
