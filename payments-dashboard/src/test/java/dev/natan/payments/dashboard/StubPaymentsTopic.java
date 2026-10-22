package dev.natan.payments.dashboard;

import java.util.ArrayList;
import java.util.List;

/** Stub {@link PaymentsTopic}: fixed partitions, and records the test queued up, returned once. */
class StubPaymentsTopic implements PaymentsTopic {

    private final List<PartitionState> partitions = new ArrayList<>();
    private final List<TopicRecord> records = new ArrayList<>();

    void willReport(PartitionState partition) {
        partitions.add(partition);
    }

    void willReport(TopicRecord record) {
        records.add(record);
    }

    @Override
    public List<PartitionState> partitions() {
        return List.copyOf(partitions);
    }

    @Override
    public List<TopicRecord> pollNewRecords() {
        List<TopicRecord> polled = List.copyOf(records);
        records.clear();
        return polled;
    }
}
