package dev.natan.payments.consumer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import dev.natan.payments.contract.PaymentSettled;

/** Spy {@link SettlementNotifier}: records what was published, and can be told to fail. */
class SpySettlementNotifier implements SettlementNotifier {

    private final List<PaymentSettled> published = new CopyOnWriteArrayList<>();
    private volatile boolean failing;

    @Override
    public void publish(PaymentSettled settled) {
        if (failing) {
            throw new IllegalStateException("notifier down, expected to be up for " + settled.paymentId());
        }
        published.add(settled);
    }

    void failFromNowOn() {
        failing = true;
    }

    void recover() {
        failing = false;
    }

    List<PaymentSettled> published() {
        return List.copyOf(published);
    }
}
