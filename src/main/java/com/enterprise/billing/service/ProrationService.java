package com.enterprise.billing.service;

import com.enterprise.billing.domain.Plan;
import com.enterprise.billing.dto.ProrationPreviewResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * Pure deterministic calculation engine for day-based proration billing math.
 * Strictly adheres to BigDecimal standards (scale 2, RoundingMode.HALF_UP) with zero floating-point drift.
 */
@Service
public class ProrationService {

    public ProrationPreviewResponse calculate(
            Plan oldPlan,
            Plan newPlan,
            Instant currentPeriodStart,
            Instant currentPeriodEnd
    ) {
        return calculate(oldPlan, newPlan, currentPeriodStart, currentPeriodEnd, Instant.now());
    }

    public ProrationPreviewResponse calculate(
            Plan oldPlan,
            Plan newPlan,
            Instant currentPeriodStart,
            Instant currentPeriodEnd,
            Instant calculationTime
    ) {
        LocalDate startDate = currentPeriodStart.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endDate = currentPeriodEnd.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate calcDate = calculationTime.atZone(ZoneOffset.UTC).toLocalDate();

        long totalDays = Math.max(1, ChronoUnit.DAYS.between(startDate, endDate));
        long daysRemaining = Math.max(0, ChronoUnit.DAYS.between(calcDate, endDate));
        daysRemaining = Math.min(totalDays, daysRemaining);

        BigDecimal unusedFraction = BigDecimal.valueOf(daysRemaining)
                .divide(BigDecimal.valueOf(totalDays), 10, RoundingMode.HALF_UP);

        BigDecimal oldPlanPrice = oldPlan.getMonthlyPrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal newPlanPrice = newPlan.getMonthlyPrice().setScale(2, RoundingMode.HALF_UP);

        BigDecimal proratedCredit = oldPlanPrice.multiply(unusedFraction).setScale(2, RoundingMode.HALF_UP);
        BigDecimal newPlanCharge = newPlanPrice.multiply(unusedFraction).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netDue = newPlanCharge.subtract(proratedCredit).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        return new ProrationPreviewResponse(
                oldPlan.getName(),
                newPlan.getName(),
                daysRemaining,
                totalDays,
                oldPlanPrice,
                proratedCredit,
                newPlanPrice,
                newPlanCharge,
                netDue
        );
    }

    public long calculateDaysRemaining(Instant currentPeriodEnd, Instant calculationTime) {
        LocalDate endDate = currentPeriodEnd.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate calcDate = calculationTime.atZone(ZoneOffset.UTC).toLocalDate();
        return Math.max(0, ChronoUnit.DAYS.between(calcDate, endDate));
    }
}
