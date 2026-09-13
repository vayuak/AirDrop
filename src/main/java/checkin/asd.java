package checkin;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.huggingface.tokenizers.Encoding;
import com.microsoft.onnxruntime.*;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct; // Use javax.annotation.PostConstruct if on Spring Boot 2.x

import java.nio.LongBuffer;
import java.nio.file.Paths;
import java.util.Map;

@Service
public class ToxicityAIModerator {

    private OrtEnvironment env;
    private OrtSession session;
    private HuggingFaceTokenizer tokenizer;

    @PostConstruct
    public void init() throws Exception {
        // 1. Initialize ONNX Engine
        env = OrtEnvironment.getEnvironment();

        // 2. Load the Model from the resources folder
        String modelPath = Paths.get(getClass().getResource("/model.onnx").toURI()).toString();
        session = env.createSession(modelPath, new OrtSession.SessionOptions());

        // 3. Load the Multi-lingual Tokenizer
        tokenizer = HuggingFaceTokenizer.newInstance(
                Paths.get(getClass().getResource("/tokenizer.json").toURI())
        );
        System.out.println("✅ AI Toxicity Model Loaded Successfully!");
    }

    public boolean isToxic(String text) {
        if (text == null || text.trim().isEmpty()) return false;

        try {
            // Convert text to neural network tokens
            Encoding encoding = tokenizer.encode(text);
            long[] inputIds = encoding.getIds();
            long[] attentionMask = encoding.getAttentionMask();

            // Create tensors with shape [1, sequence_length]
            OnnxTensor inputTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), new long[]{1, inputIds.length});
            OnnxTensor maskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), new long[]{1, attentionMask.length});

            Map<String, OnnxTensor> inputs = Map.of(
                    "input_ids", inputTensor,
                    "attention_mask", maskTensor
            );

            // Run the model
            try (OrtSession.Result results = session.run(inputs)) {
                float[][] logits = (float[][]) results.get(0).getValue();

                float safeScore = logits[0][0];
                float toxicScore = logits[0][1];

                // Calculate probability
                double toxicProbability = Math.exp(toxicScore) / (Math.exp(safeScore) + Math.exp(toxicScore));

                System.out.println("Text: '" + text + "' | Toxicity Probability: " + (toxicProbability * 100) + "%");

                // Flag as toxic if confidence is over 80%
                return toxicProbability > 0.80;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}