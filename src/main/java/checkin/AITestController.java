package checkin;

import com.AirDrop.Spherical.Services.ToxicityAIModerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class AITestController {

    @Autowired
    private ToxicityAIModerator aiModerator;

    @GetMapping("/api/test-ai")
    public Map<String, Object> testModeration(@RequestParam String text) {
        long startTime = System.currentTimeMillis();

        boolean isToxic = aiModerator.isToxic(text);

        long endTime = System.currentTimeMillis();

        Map<String, Object> response = new HashMap<>();
        response.put("text_analyzed", text);
        response.put("is_toxic", isToxic);
        response.put("processing_time_ms", (endTime - startTime));

        return response;
    }
}