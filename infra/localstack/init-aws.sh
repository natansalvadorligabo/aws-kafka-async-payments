#!/bin/bash
# Runs inside LocalStack (ready.d hook). Shared by docker-compose and the Testcontainers tests.
set -euo pipefail

REGION=us-east-1
VISIBILITY_TIMEOUT_SECONDS=10
MAX_RECEIVE_COUNT=3

awslocal sqs create-queue --queue-name payments-dlq >/dev/null
DLQ_ARN=$(awslocal sqs get-queue-attributes \
  --queue-url "http://sqs.$REGION.localhost.localstack.cloud:4566/000000000000/payments-dlq" \
  --attribute-names QueueArn --query Attributes.QueueArn --output text)

awslocal sqs create-queue --queue-name payments --attributes "{
  \"VisibilityTimeout\": \"$VISIBILITY_TIMEOUT_SECONDS\",
  \"RedrivePolicy\": \"{\\\"deadLetterTargetArn\\\":\\\"$DLQ_ARN\\\",\\\"maxReceiveCount\\\":\\\"$MAX_RECEIVE_COUNT\\\"}\"
}" >/dev/null

# Settled payments fan out through SNS; the audit queue lets tests and the demo observe them.
TOPIC_ARN=$(awslocal sns create-topic --name payment-settled --query TopicArn --output text)
awslocal sqs create-queue --queue-name payment-settled-audit >/dev/null
awslocal sns subscribe --topic-arn "$TOPIC_ARN" --protocol sqs \
  --notification-endpoint "arn:aws:sqs:$REGION:000000000000:payment-settled-audit" \
  --attributes RawMessageDelivery=true >/dev/null

awslocal dynamodb create-table --table-name processed-payments \
  --attribute-definitions AttributeName=paymentId,AttributeType=S \
  --key-schema AttributeName=paymentId,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST >/dev/null

echo "payments infra ready"
