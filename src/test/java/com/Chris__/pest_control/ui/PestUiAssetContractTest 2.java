package com.Chris__.pest_control.ui;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PestUiAssetContractTest {

    @Test
    void validatesManifestAndRequiredHudAsset() throws Exception {
        ClassLoader cl = PestUiAssetContractTest.class.getClassLoader();
        PestUiAssetContract.ValidationResult result = PestUiAssetContract.validate(cl);

        assertTrue(result.manifestIncludesAssetPack(), "manifest.json must set IncludesAssetPack=true");
        assertTrue(result.missingUiDocuments().isEmpty(),
                "Missing required ui documents: " + String.join(", ", result.missingUiDocuments()));
        assertTrue(PestUiAssetContract.hasUiDocument(cl, PestUiAssetContract.HUD_STATUS),
                "Expected HUD status doc: " + PestUiAssetContract.HUD_STATUS);
    }

    @Test
    void statusHudHasMinimalTopLeftContract() throws Exception {
        String resource = PestUiAssetContract.toClasspathResourcePath(PestUiAssetContract.HUD_STATUS);
        try (InputStream in = PestUiAssetContractTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertTrue(in != null, "Missing resource: " + resource);
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
