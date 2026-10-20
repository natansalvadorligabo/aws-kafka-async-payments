# Who settles the payments queue: the Lambda (default) or the Spring Boot consumer.
CONSUMER ?= lambda
export CONSUMER

.PHONY: build up down demo test

build:
	./mvnw -q -B package -DskipTests

up: build
	docker compose --profile $(CONSUMER) up -d --build --wait
ifeq ($(CONSUMER),lambda)
	docker compose exec -T localstack /opt/payments/deploy-lambda.sh /opt/payments/payments-lambda.zip
endif

down:
	docker compose --profile spring down -v

demo:
	./scripts/demo.sh

test:
	./mvnw -B verify
