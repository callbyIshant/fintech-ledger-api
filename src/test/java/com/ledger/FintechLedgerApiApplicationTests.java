package com.ledger;

import com.ledger.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Application Context Load Test")
class FintechLedgerApiApplicationTests extends AbstractIntegrationTest {

    @Test
    @DisplayName("Application context loads successfully with Testcontainers")
    void contextLoads() {
    }
}
