package com.riseup;

import com.riseup.ai.LLMClient;
import com.riseup.ai.LLMProvider;
import com.riseup.ai.LLMProviderError;
import com.riseup.ai.MockProvider;
import com.riseup.verification.LlmObjectDetector;
import com.riseup.verification.ObjectDetectionTask;
import com.riseup.verification.VerificationResponse;
import com.riseup.verification.VerificationResult;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class LlmObjectDetectorTest {

    /** Vision-capable stub that returns a fixed reply (or fails) and records the prompt. */
    static final class StubVision implements LLMProvider {
        private final String reply;
        String lastPrompt;

        StubVision(String reply) {
            this.reply = reply;
        }

        public String getName() { return "stub"; }
        public String getModel() { return "stub-vision"; }
        public String complete(String prompt, String system) { return reply; }
        public boolean supportsVision() { return true; }

        public String completeWithImage(String prompt, String system, byte[] image, String mediaType) {
            lastPrompt = prompt;
            if (reply == null) {
                throw new LLMProviderError("boom");
            }
            return reply;
        }
    }

    private static LlmObjectDetector detector(LLMProvider vision) {
        Map<String, LLMProvider> providers = new LinkedHashMap<>();
        providers.put("mock", new MockProvider("normal"));
        providers.put("stub", vision);
        return new LlmObjectDetector(new LLMClient(providers, Map.of("vision", "stub"), 0));
    }

    private static VerificationResult verify(LlmObjectDetector d, String target, String imageB64) {
        VerificationResponse r = new VerificationResponse();
        r.setImageBase64(imageB64);
        return new ObjectDetectionTask(target, d).validate(r);
    }

    private static String noisyJpeg() throws Exception {
        BufferedImage img = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Random rng = new Random(7);
        for (int x = 0; x < 200; x++) {
            for (int y = 0; y < 200; y++) {
                img.setRGB(x, y, rng.nextInt(0xFFFFFF));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    @Test
    void acceptsWhenLlmSeesTargetConfidently() throws Exception {
        StubVision vision = new StubVision("{\"present\": true, \"confidence\": 0.92, \"seen\": \"a mug on a desk\"}");
        VerificationResult result = verify(detector(vision), "coffee mug", noisyJpeg());
        assertThat(result.isSuccess()).isTrue();
        assertThat(vision.lastPrompt).contains("coffee mug");
    }

    @Test
    void rejectsWrongObjectAndExplainsWhatWasSeen() throws Exception {
        StubVision vision = new StubVision("```json\n{\"present\": false, \"confidence\": 0.95, \"seen\": \"a waterfall\"}\n```");
        VerificationResult result = verify(detector(vision), "coffee mug", noisyJpeg());
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("waterfall");
    }

    @Test
    void rejectsLowConfidenceMatch() throws Exception {
        StubVision vision = new StubVision("{\"present\": true, \"confidence\": 0.3, \"seen\": \"blurry shape\"}");
        assertThat(verify(detector(vision), "toothbrush", noisyJpeg()).isSuccess()).isFalse();
    }

    @Test
    void blankImageRejectedBeforeCallingLlm() throws Exception {
        StubVision vision = new StubVision("{\"present\": true, \"confidence\": 1.0}");
        BufferedImage img = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        VerificationResult result = verify(detector(vision), "window", Base64.getEncoder().encodeToString(out.toByteArray()));
        assertThat(result.isSuccess()).isFalse();
        assertThat(vision.lastPrompt).isNull();
    }

    @Test
    void fallsBackToBasicCheckWhenVisionFails() throws Exception {
        VerificationResult result = verify(detector(new StubVision(null)), "front door", noisyJpeg());
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("AI vision unavailable");
    }

    @Test
    void fallsBackWhenNoVisionProviderConfigured() throws Exception {
        Map<String, LLMProvider> providers = new LinkedHashMap<>();
        providers.put("mock", new MockProvider("normal"));
        LlmObjectDetector d = new LlmObjectDetector(new LLMClient(providers, Map.of("vision", "claude"), 0));
        VerificationResult result = verify(d, "kitchen tap", noisyJpeg());
        assertThat(result.getMessage()).contains("AI vision unavailable");
        assertThat(result.isSuccess()).isTrue();
    }
}
