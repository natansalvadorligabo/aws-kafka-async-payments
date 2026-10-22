package dev.natan.payments.dashboard;

import java.util.ArrayList;
import java.util.List;

import dev.natan.payments.contract.PaymentSettled;

/** Stub {@link StackReader}: returns whatever the test queued up, once. */
class StubStackReader implements StackReader {

    private StackCounts counts = new StackCounts(0, 0, 0, 0, 0);
    private final List<PaymentSettled> settled = new ArrayList<>();
    private final List<DeadLetter> deadLetters = new ArrayList<>();

    void willReport(StackCounts counts) {
        this.counts = counts;
    }

    void willReport(PaymentSettled settlement) {
        settled.add(settlement);
    }

    void willReport(DeadLetter deadLetter) {
        deadLetters.add(deadLetter);
    }

    @Override
    public StackCounts counts() {
        return counts;
    }

    @Override
    public List<PaymentSettled> drainSettled() {
        List<PaymentSettled> drained = List.copyOf(settled);
        settled.clear();
        return drained;
    }

    @Override
    public List<DeadLetter> pollNewDeadLetters() {
        List<DeadLetter> polled = List.copyOf(deadLetters);
        deadLetters.clear();
        return polled;
    }
}
