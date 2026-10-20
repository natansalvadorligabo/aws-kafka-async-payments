.PHONY: build up down demo test

build:
	./mvnw -q -B package -DskipTests

up: build
	docker compose up -d --build --wait

down:
	docker compose down -v

demo:
	./scripts/demo.sh

test:
	./mvnw -B verify
