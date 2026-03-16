package com.flux.gateway.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {

    @Test
    void health_returnsStatusUp() {
        HealthController controller = new HealthController();

        var result = controller.health().block();

        assertThat(result).containsEntry("status", "UP");
    }
}
