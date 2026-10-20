package dev.natan.payments.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import dev.natan.payments.contract.PaymentRequested;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Body of {@code POST /payments}. */
record PaymentRequest(
        @NotBlank String accountId,
        @NotBlank String pixKey,
        @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal amount) {

    PaymentRequested toEvent(UUID paymentId, Instant requestedAt) {
        return new PaymentRequested(paymentId, accountId, pixKey, amount, requestedAt);
    }
}
