package com.Chris__.pest_control.ui;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PestUiDocumentsFormatTest {

    @Test
    void queueAndShopDocumentsAreValidMarkup() throws Exception {
        assertMarkupUi("Common/UI/Custom/Pages/PestControl/Queue.ui");
        assertMarkupUi("Common/UI/Custom/Pages/PestControl/Shop.ui");
    }

    private static void assertMarkupUi(String resourcePath) throws Exception {
        try (InputStream in = PestUiDocumentsFormatTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(in, "Missing UI document: " + resourcePath);
            String uiDoc = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            String trimmedLeading = uiDoc.stripLeading();
            assertFalse(trimmedLeading.startsWith("{"),
                    "UI document must be markup, not JSON-style content: " + resourcePath);
            assertTrue(uiDoc.contains("Group #"),
                    "UI document must include at least one Group node: " + resourcePath);
        }
    }
}
