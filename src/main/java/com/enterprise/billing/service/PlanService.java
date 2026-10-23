package com.enterprise.billing.service;

import com.enterprise.billing.domain.Plan;
import com.enterprise.billing.dto.PlanResponse;
import com.enterprise.billing.exception.ResourceNotFoundException;
import com.enterprise.billing.repository.PlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class PlanService {

    private final PlanRepository planRepository;

    public PlanService(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    public List<PlanResponse> getAllPlans() {
        return planRepository.findAll().stream()
                .map(PlanResponse::from)
                .toList();
    }

    public Plan getPlanByName(String name) {
        return planRepository.findByName(name.toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Subscription plan not found: " + name));
    }
}
