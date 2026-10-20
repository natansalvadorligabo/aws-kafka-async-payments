package dev.natan.payments.consumer;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import dev.natan.payments.contract.PaymentRequested;
import dev.natan.payments.contract.PaymentSettled;

/**
 * Settles each payment at most once, however many times its message is delivered.
 *
 * <pre>{@code
 * paymentSettlement.settle(payment);
 * }</pre>
 */
@Component
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
        if (!processedPayments.claim(payment.paymentId())) {
            log.atInfo().addKeyValue("paymentId", payment.paymentId()).log("duplicate delivery ignored");
            return;
        }
        try {
            settlementNotifier.publish(PaymentSettled.of(payment, clock.instant()));
        } catch (RuntimeException e) {
            // Without the release, the redelivery would look like a duplicate and the payment would never settle.
            processedPayments.release(payment.paymentId());
            throw e;
        }
        log.atInfo()
                .addKeyValue("paymentId", payment.paymentId())
                .addKeyValue("accountId", payment.accountId())
                .log("payment settled");
    }
}
