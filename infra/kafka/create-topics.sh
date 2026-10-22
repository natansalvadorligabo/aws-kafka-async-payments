#!/bin/bash
# Runs inside the Kafka container. Shared by docker-compose (make up BROKER=kafka) and the Testcontainers tests.
# Usage: create-topics.sh <bootstrap-server reachable from inside the container>
set -euo pipefail

BOOTSTRAP=$1
# Records with the same key (accountId) always land on the same partition, so each account keeps its order.
PARTITIONS=6

/opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP" --create --if-not-exists \
  --topic payments --partitions "$PARTITIONS" --replication-factor 1

echo "kafka topics ready"
