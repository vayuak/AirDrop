package checkin;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ToxicityAIModeratorTest {

    private static ToxicityAIModerator moderator;

    @BeforeAll
    static void setUp() throws Exception {
        // Instantiates and loads ONNX model directly without starting Spring Security
        moderator = new ToxicityAIModerator();
        moderator.init();
    }

    @Test
    @DisplayName("Should ALLOW safe multi-lingual text")
    void testSafeContent() {
        assertFalse(moderator.isToxic("Hello brother, welcome to Delhi!"), "Safe English failed");
        assertFalse(moderator.isToxic("Bhai achha khana kahan milega?"), "Safe Hinglish failed");
        assertFalse(moderator.isToxic("Kemon achen dada?"), "Safe Bengali failed");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "i will kill you",
            "tu madarchod hai",
            "bokachoda",
            "f_u_c_k you",
            "bhosdike"
    })
    @DisplayName("Should BLOCK toxic content across English, Hinglish, and Bengali")
    void testToxicContent(String toxicInput) {
        assertTrue(moderator.isToxic(toxicInput), "Failed to detect toxicity for: " + toxicInput);
    }
}