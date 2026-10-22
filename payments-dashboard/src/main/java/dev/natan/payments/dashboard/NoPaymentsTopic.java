package dev.natan.payments.dashboard;

import java.util.List;

/** The API publishes to SQS (BROKER=sqs): there is no topic to show. */
class NoPaymentsTopic implements PaymentsTopic {

    @Override
    public List<PartitionState> partitions() {
        return List.of();
    }

    @Override
    public List<TopicRecord> pollNewRecords() {
        return List.of();
    }
}
