package com.Chris__.pest_control.ui;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PestStatusUiContractTest {

    @Test
    void statusUiResourceContainsExpectedIdsAndTopLeftAnchors() throws Exception {
        try (InputStream in = PestStatusUiContractTest.class.getClassLoader()
                .getResourceAsStream("Common/UI/Custom/Hud/PestControl/Status.ui")) {
            assertNotNull(in, "Missing Pest HUD asset: Common/UI/Custom/Hud/PestControl/Status.ui");

            String uiDoc = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(uiDoc.contains("#PcTitleLabel"), "Status UI must define #PcTitleLabel");
            assertTrue(uiDoc.contains("#PcLine1Label"), "Status UI must define #PcLine1Label");
            assertTrue(uiDoc.contains("#PcLine2Label"), "Status UI must define #PcLine2Label");
            assertTrue(uiDoc.contains("#PcLine3Label"), "Status UI must define #PcLine3Label");

            assertTrue(uiDoc.contains("Top: 20"), "Status UI must anchor to top using Top: 20");
            assertTrue(uiDoc.contains("Left: 20"), "Status UI must offset from left using Left: 20");
        }
    }
}
