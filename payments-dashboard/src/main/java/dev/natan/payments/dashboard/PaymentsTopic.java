package dev.natan.payments.dashboard;

import java.util.List;

/**
 * Observes the Kafka payments topic without joining a consumer group, so it never moves anyone's offsets.
 *
 * <pre>{@code
 * List<PartitionState> partitions = paymentsTopic.partitions();
 * }</pre>
 */
public interface PaymentsTopic {

    List<PartitionState> partitions();

    /** Records appended since the last call (all of them on the first call). */
    List<TopicRecord> pollNewRecords();
}
