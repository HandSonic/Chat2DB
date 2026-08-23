package ai.chat2db.community.domain.core.impl.sys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import ai.chat2db.community.domain.api.config.Environment;
import org.junit.jupiter.api.Test;

class SysEnvironmentServiceImplTest {

    @Test
    void listAllReturnsTestDevReleaseInDisplayOrder() {
        List<Environment> environments = new SysEnvironmentServiceImpl().listAll();

        assertEquals(List.of("TEST", "DEV", "RELEASE"),
                environments.stream().map(Environment::getName).toList());
        assertEquals(List.of(1L, 3L, 2L),
                environments.stream().map(Environment::getId).toList());
    }

    @Test
    void everyEnvironmentCarriesAnIdentityColor() {
        // The web client types Environment.color as non-null and lowercases it
        // for badge styling; DEV was shipped without a color by mistake.
        for (Environment environment : new SysEnvironmentServiceImpl().listAll()) {
            assertNotNull(environment.getColor(), environment.getName() + " must define a color");
            assertFalse(environment.getColor().isBlank(), environment.getName() + " must define a color");
        }
    }
}
