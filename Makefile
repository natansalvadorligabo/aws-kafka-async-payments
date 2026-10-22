# Who settles the payments queue: the Lambda (default) or the Spring Boot consumer.
CONSUMER ?= lambda
# Where the API publishes payments: sqs (default) or kafka.
BROKER ?= sqs
export CONSUMER BROKER

.PHONY: build up down demo test

build:
	./mvnw -q -B package -DskipTests

up: build
	docker compose --profile $(CONSUMER) --profile $(BROKER) up -d --build --wait
ifeq ($(CONSUMER),lambda)
	docker compose exec -T localstack /opt/payments/deploy-lambda.sh /opt/payments/payments-lambda.zip
endif
ifeq ($(BROKER),kafka)
	docker compose exec -T kafka /opt/payments/create-topics.sh kafka:29092
endif

down:
	docker compose --profile spring --profile kafka down -v

demo:
	./scripts/demo.sh

test:
	./mvnw -B verify
