package dev.natan.payments.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.natan.payments.contract.PaymentRequested;

class PaymentSettlementTest {

    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");

    private static final Duration LEASE = Duration.ofSeconds(8);

    private final FakeClock clock = new FakeClock(NOW);
    private final InMemoryProcessedPayments processedPayments = new InMemoryProcessedPayments(clock, LEASE);
    private final SpySettlementNotifier notifier = new SpySettlementNotifier();
    private final PaymentSettlement settlement = new PaymentSettlement(processedPayments, notifier, clock);

    @Test
    void settlesAPaymentAndAnnouncesIt() {
        PaymentRequested payment = payment();

        settlement.settle(payment);

        assertThat(notifier.published()).singleElement().satisfies(settled -> {
            assertThat(settled.paymentId()).isEqualTo(payment.paymentId());
            assertThat(settled.settledAt()).isEqualTo(NOW);
        });
    }

    @Test
    void secondDeliveryOfTheSamePaymentIsIgnored() {
        PaymentRequested payment = payment();

        settlement.settle(payment);
        settlement.settle(payment);

        assertThat(notifier.published()).hasSize(1);
    }

    @Test
    void failedSettlementReleasesTheClaimSoTheRedeliveryCanSettle() {
        PaymentRequested payment = payment();
        notifier.failFromNowOn();

        assertThatThrownBy(() -> settlement.settle(payment)).isInstanceOf(IllegalStateException.class);
        assertThat(processedPayments.isClaimed(payment.paymentId())).isFalse();

        notifier.recover();
        settlement.settle(payment);
        assertThat(notifier.published()).hasSize(1);
    }

    @Test
    void paymentClaimedByAProcessThatDiedIsSettledByTheRedeliveryOnceTheLeaseExpires() {
        PaymentRequested payment = payment();
        notifier.crashOnNextPublish();
        assertThatThrownBy(() -> settlement.settle(payment)).isInstanceOf(SimulatedCrash.class);

        clock.advance(LEASE.plusSeconds(1));
        settlement.settle(payment);

        assertThat(notifier.published()).as("settled by the redelivery").singleElement()
                .satisfies(settled -> assertThat(settled.paymentId()).isEqualTo(payment.paymentId()));
    }

    @Test
    void deliveryThatFindsALiveLeaseIsRetriedNotIgnored() {
        PaymentRequested payment = payment();
        notifier.crashOnNextPublish();
        assertThatThrownBy(() -> settlement.settle(payment)).isInstanceOf(SimulatedCrash.class);

        clock.advance(LEASE.minusSeconds(1));

        assertThatThrownBy(() -> settlement.settle(payment))
                .isInstanceOf(SettlementInProgressException.class)
                .hasMessageContaining(payment.paymentId().toString());
        assertThat(notifier.published()).isEmpty();
    }

    @Test
    void settledPaymentStaysSettledAfterTheLeaseWouldHaveExpired() {
        PaymentRequested payment = payment();
        settlement.settle(payment);

        clock.advance(LEASE.multipliedBy(10));
        settlement.settle(payment);

        assertThat(notifier.published()).hasSize(1);
    }

    private static PaymentRequested payment() {
        return new PaymentRequested(UUID.randomUUID(), "acc-1", "alice@example.com", new BigDecimal("10.00"), NOW);
    }
}
