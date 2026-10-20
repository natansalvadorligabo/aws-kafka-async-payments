#!/usr/bin/env bash
# Walks through the SQS flow against the running docker-compose stack (make up).
set -euo pipefail

API=http://localhost:8080
QUEUE_URL=http://localhost:4566/000000000000

awslocal() { docker compose exec -T localstack awslocal "$@"; }
step() { printf '\n\033[1m== %s\033[0m\n' "$*"; }

pay() {
  curl -s -w ' -> HTTP %{http_code}\n' -X POST "$API/payments" \
    -H 'Content-Type: application/json' \
    -d "{\"accountId\": \"$1\", \"pixKey\": \"$2\", \"amount\": $3}"
}

messages_on() {
  awslocal sqs get-queue-attributes --queue-url "$QUEUE_URL/$1" \
    --attribute-names ApproximateNumberOfMessages ApproximateNumberOfMessagesNotVisible \
    --query 'sum(map(&to_number(@), values(Attributes)))' --output text
}

settled_count() {
  awslocal dynamodb scan --table-name processed-payments --select COUNT --query Count --output text
}

until curl -s -o /dev/null "$API/payments"; do sleep 1; done

settled_before=$(settled_count)

step "1. The consumer goes down"
docker compose stop consumer

step "2. The API still answers 202 (and 400 for an invalid request)"
for i in 1 2 3 4 5; do pay "acc-$i" "user$i@example.com" "1$i.90"; done
pay "" "nobody@example.com" -1 || true

step "3. The payments wait on SQS"
waiting=$(messages_on payments)
echo "messages on 'payments': $waiting"

step "4. The consumer comes back"
docker compose start consumer
until [ "$(messages_on payments)" = "0" ]; do sleep 1; done
echo "messages on 'payments': 0"

step "5. Nothing was lost: $waiting waited on SQS, settled +$(( $(settled_count) - settled_before ))"

step "6. A malformed message moves to the DLQ after 3 receives (~30s)"
awslocal sqs send-message --queue-url "$QUEUE_URL/payments" --message-body 'not a payment' >/dev/null
until [ "$(messages_on payments-dlq)" != "0" ]; do printf '.'; sleep 2; done
echo
echo "messages on 'payments-dlq': $(messages_on payments-dlq)"
