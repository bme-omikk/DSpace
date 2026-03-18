package org.dspace.app.rest.statistics;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/statistics")
public class CustomStatsController {

    @PostMapping(value = "/graph", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> graph(@RequestBody CustomStatsRequest req) {
        return Map.of(
            "fromdate", req.getFromdate(),
            "todate", req.getTodate(),
            "isbot", req.isIsbot(),
            "resolution", req.getResolution(),
            "data", List.of(
                Map.of("date", "2026-03-01", "views", 42),
                Map.of("date", "2026-03-02", "views", 37),
                Map.of("date", "2026-03-03", "views", 55)
            )
        );
    }

    @PostMapping(value = "/bycity", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> bycity(@RequestBody CustomStatsRequest req) {
        return Map.of(
            "fromdate", req.getFromdate(),
            "todate", req.getTodate(),
            "isbot", req.isIsbot(),
            "resolution", req.getResolution(),
            "data", List.of(
                Map.of("city", "Budapest", "views", 120),
                Map.of("city", "Vienna", "views", 85),
                Map.of("city", "Prague", "views", 60)
            )
        );
    }
}
