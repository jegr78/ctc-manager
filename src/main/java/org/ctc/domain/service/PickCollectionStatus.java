package org.ctc.domain.service;

import org.springframework.stereotype.Component;

@Component
public class PickCollectionStatus {
    public boolean isConfidentialityReady() {
        return true;
    }

    public boolean isAvailable() {
        return isConfidentialityReady();
    }
}
