package dev.natan.payments.settlement;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.natan.payments.contract.PaymentRequested;
import dev.natan.payments.contract.PaymentSettled;

/**
 * Settles each payment, however many times its message is delivered and even if a delivery dies halfway.
 *
 * <p>No payment is lost; {@link PaymentSettled} is announced at least once. A process that dies after publishing
 * but before {@link ProcessedPayments#markSettled} leaves a {@code PENDING} claim, so the redelivery publishes again.
 * Subscribers deduplicate by paymentId.
 *
 * <pre>{@code
 * paymentSettlement.settle(payment);
 * }</pre>
 */
public class PaymentSettlement {

    private static final Logger log = LoggerFactory.getLogger(PaymentSettlement.class);

    private final ProcessedPayments processedPayments;
    private final SettlementNotifier settlementNotifier;
    private final Clock clock;

    public PaymentSettlement(ProcessedPayments processedPayments, SettlementNotifier settlementNotifier, Clock clock) {
        this.processedPayments = processedPayments;
        this.settlementNotifier = settlementNotifier;
        this.clock = clock;
    }

    public void settle(PaymentRequested payment) {
        switch (processedPayments.claim(payment.paymentId())) {
            case ALREADY_SETTLED -> log.atInfo()
                    .addKeyValue("paymentId", payment.paymentId())
                    .log("duplicate delivery ignored");
            case IN_PROGRESS -> throw new SettlementInProgressException(payment.paymentId());
            case CLAIMED -> settleClaimed(payment);
        }
    }

    private void settleClaimed(PaymentRequested payment) {
        try {
            settlementNotifier.publish(PaymentSettled.of(payment, clock.instant()));
        } catch (RuntimeException e) {
            // Releasing lets the redelivery retry right away instead of waiting for the lease to expire.
            processedPayments.release(payment.paymentId());
            throw e;
        }
        processedPayments.markSettled(payment.paymentId());
        log.atInfo()
                .addKeyValue("paymentId", payment.paymentId())
                .addKeyValue("accountId", payment.accountId())
                .log("payment settled");
    }
}
