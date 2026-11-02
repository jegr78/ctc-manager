package org.ctc.domain.service;

import org.springframework.stereotype.Component;

@Component
public class PickCollectionStatus {
    public boolean isConfidentialityReady() {
        return false;
    }

    public boolean isAvailable() {
        // Collection stays unavailable until the Gateway and confidentiality acceptance are delivered.
        return false;
    }
}
