#!/bin/bash
# Runs inside LocalStack. Deploys the settlement Lambda and subscribes it to the payments queue.
# Shared by docker-compose (make up CONSUMER=lambda) and the Testcontainers tests.
# Usage: deploy-lambda.sh <path-to-payments-lambda.zip>
set -euo pipefail

ZIP=$1
REGION=us-east-1
ACCOUNT=000000000000
FUNCTION=settle-payments

awslocal lambda create-function --function-name "$FUNCTION" \
  --runtime java25 \
  --handler dev.natan.payments.lambda.SettlePaymentsHandler::handleRequest \
  --zip-file "fileb://$ZIP" \
  --role "arn:aws:iam::$ACCOUNT:role/$FUNCTION" \
  --timeout 10 \
  --memory-size 512 \
  --environment "Variables={PROCESSED_TABLE=processed-payments,SETTLED_TOPIC_ARN=arn:aws:sns:$REGION:$ACCOUNT:payment-settled}" \
  >/dev/null
awslocal lambda wait function-active-v2 --function-name "$FUNCTION"

# Failed records go back to the queue individually; the rest of the batch is deleted.
MAPPING=$(awslocal lambda create-event-source-mapping --function-name "$FUNCTION" \
  --event-source-arn "arn:aws:sqs:$REGION:$ACCOUNT:payments" \
  --batch-size 10 \
  --function-response-types ReportBatchItemFailures \
  --query UUID --output text)
until [ "$(awslocal lambda get-event-source-mapping --uuid "$MAPPING" --query State --output text)" = "Enabled" ]; do
  sleep 1
done

echo "lambda $FUNCTION deployed"
