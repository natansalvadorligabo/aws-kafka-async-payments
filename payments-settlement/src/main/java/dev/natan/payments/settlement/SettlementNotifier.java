package dev.natan.payments.settlement;

import dev.natan.payments.contract.PaymentSettled;

/**
 * Tells the rest of the system a payment was settled.
 *
 * <pre>{@code
 * settlementNotifier.publish(PaymentSettled.of(payment, clock.instant()));
 * }</pre>
 */
public interface SettlementNotifier {

    void publish(PaymentSettled settled);
}
