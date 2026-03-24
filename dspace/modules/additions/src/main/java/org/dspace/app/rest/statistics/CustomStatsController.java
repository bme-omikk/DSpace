package org.dspace.app.rest.statistics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.RangeFacet;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrDocument;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/statistics")
public class CustomStatsController {

    private static final Logger log = LogManager.getLogger(CustomStatsController.class);
    
    private static final ConfigurationService configurationService
            = DSpaceServicesFactory.getInstance().getConfigurationService();
    
    private static final String SOLR_URL = configurationService.getProperty("solr.server") + "/statistics/";

    @PostMapping(value = "/bydate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> graph(@RequestBody CustomStatsRequest req) throws Exception {
        String gap;
        switch (req.getResolution()) {
            case 1: gap = "+1YEAR";  break;
            case 2: gap = "+1MONTH"; break;
            default: gap = "+1DAY";  break;
        }

        String q = "statistics_type:view";
        if (!req.isIsbot()) {
            q += " AND isBot:false";
        }

        SolrQuery query = new SolrQuery(q);
        query.setRows(0);
        query.setFacet(true);
        query.add("facet.range", "time");
        query.add("facet.range.start", req.getFromdate() + "T00:00:00Z");
        query.add("facet.range.end",   req.getTodate()   + "T23:59:59Z");
        query.add("facet.range.gap", gap);

        try (SolrClient solr = new HttpSolrClient.Builder(SOLR_URL).build()) {
            QueryResponse response = solr.query(query);
            List<Map<String, Object>> data = new ArrayList<>();
            for (RangeFacet<?, ?> rf : response.getFacetRanges()) {
                for (RangeFacet.Count c : rf.getCounts()) {
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("x", c.getValue());
                    point.put("y", c.getCount());
                    data.add(point);
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("data", data);
            return result;
        }
    }

    @PostMapping(value = "/bycity", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> bycity(@RequestBody CustomStatsRequest req) throws Exception{
        String gap;
        switch (req.getResolution()) {
            case 1: gap = "+1YEAR";  break;
            case 2: gap = "+1MONTH"; break;
            default: gap = "+1DAY";  break;
        }

        String q = "statistics_type:view";
        if (!req.isIsbot()) {
            q += " AND isBot:false";
        }

        SolrQuery query = new SolrQuery(q);
        query.setRows(0);
        query.setFacet(true);
        query.add("facet.range", "time");
        query.add("facet.range.start", req.getFromdate() + "T00:00:00Z");
        query.add("facet.range.end",   req.getTodate()   + "T23:59:59Z");
        query.add("facet.range.gap", gap);

        try (SolrClient solr = new HttpSolrClient.Builder(SOLR_URL).build()) {
            QueryResponse response = solr.query(query);
            SolrDocumentList sdl = response.getResults();
            for (SolrDocument sd : response.getResults()) {
                log.info("!!!TEST!!! solr document " + sd.toString());
            }
            
            List<Map<String, Object>> data = new ArrayList<>();
            /*for (RangeFacet<?, ?> rf : response.getFacetRanges()) {
                for (RangeFacet.Count c : rf.getCounts()) {
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("x", c.getValue());
                    point.put("y", c.getCount());
                    data.add(point);
                }
            }
            */
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("data", data);
            return result;
        }
    }
}
