package org.dspace.app.rest.statistics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.RangeFacet;
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
    
    private static final ConfigurationService configurationService
            = DSpaceServicesFactory.getInstance().getConfigurationService();
    
    private static final String SOLR_URL = configurationService.getProperty("solr.server") + "/statistics/";

    private String buildQuery(CustomStatsRequest req) {
        String q = "statistics_type:view";
        if (!req.isIsbot()) {
            q += " AND isBot:false";
        }
        return q;
    }

    private String getGap(int resolution) {
        switch (resolution) {
            case 1: return "+1YEAR";
            case 2: return "+1MONTH";
            default: return "+1DAY";
        }
    }

    // 1. Total views count
    @PostMapping(value = "/total", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> total(@RequestBody CustomStatsRequest req) throws Exception {
        SolrQuery query = new SolrQuery(buildQuery(req));
        query.setRows(0);
        query.addFilterQuery("time:[" + req.getFromdate() + "T00:00:00Z TO " + req.getTodate() + "T23:59:59Z]");

        try (SolrClient solr = new HttpSolrClient.Builder(SOLR_URL).build()) {
            QueryResponse response = solr.query(query);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("total", response.getResults().getNumFound());
            return result;
        }
    }

    // 2. Views by date (line graph)
    @PostMapping(value = "/bydate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> bydate(@RequestBody CustomStatsRequest req) throws Exception {
        SolrQuery query = new SolrQuery(buildQuery(req));
        query.setRows(0);
        query.setFacet(true);
        query.add("facet.range", "time");
        query.add("facet.range.start", req.getFromdate() + "T00:00:00Z");
        query.add("facet.range.end",   req.getTodate()   + "T23:59:59Z");
        query.add("facet.range.gap", getGap(req.getResolution()));

        try (SolrClient solr = new HttpSolrClient.Builder(SOLR_URL).build()) {
            QueryResponse response = solr.query(query);
            List<Map<String, Object>> data = new ArrayList<>();
            for (RangeFacet<?, ?> rf : response.getFacetRanges()) {
                for (RangeFacet.Count c : rf.getCounts()) {
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("date", c.getValue());
                    point.put("count", c.getCount());
                    data.add(point);
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("data", data);
            return result;
        }
    }

    // 3. Views by resource (community/collection/item) - top 50
    @PostMapping(value = "/byresource", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> byresource(@RequestBody CustomStatsRequest req) throws Exception {
        String field;
        switch (req.getResourceType()) {
            case "community": field = "owningComm"; break;
            case "collection": field = "owningColl"; break;
            case "item": field = "owningItem"; break;
            default: throw new IllegalArgumentException("Invalid resourceType");
        }

        SolrQuery query = new SolrQuery(buildQuery(req));
        query.setRows(0);
        query.addFilterQuery("time:[" + req.getFromdate() + "T00:00:00Z TO " + req.getTodate() + "T23:59:59Z]");
        query.setFacet(true);
        query.addFacetField(field);
        query.setFacetLimit(50);
        query.setFacetSort("count");

        try (SolrClient solr = new HttpSolrClient.Builder(SOLR_URL).build()) {
            QueryResponse response = solr.query(query);
            List<Map<String, Object>> data = new ArrayList<>();
            FacetField facet = response.getFacetField(field);
            if (facet != null) {
                for (FacetField.Count c : facet.getValues()) {
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("uuid", c.getName());
                    point.put("count", c.getCount());
                    data.add(point);
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("data", data);
            return result;
        }
    }

    // 4. Views by city (table, top 50 by count)
    @PostMapping(value = "/bycity", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> bycity(@RequestBody CustomStatsRequest req) throws Exception {
        SolrQuery query = new SolrQuery(buildQuery(req));
        query.setRows(0);
        query.addFilterQuery("time:[" + req.getFromdate() + "T00:00:00Z TO " + req.getTodate() + "T23:59:59Z]");
        query.setFacet(true);
        query.addFacetField("city");
        query.setFacetLimit(50);
        query.setFacetSort("count");

        try (SolrClient solr = new HttpSolrClient.Builder(SOLR_URL).build()) {
            QueryResponse response = solr.query(query);
            List<Map<String, Object>> data = new ArrayList<>();
            FacetField facet = response.getFacetField("city");
            if (facet != null) {
                for (FacetField.Count c : facet.getValues()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("city", c.getName());
                    row.put("count", c.getCount());
                    data.add(row);
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("data", data);
            return result;
        }
    }

    // 5. Search by keyword (city/url/dns/resource)
    @PostMapping(value = "/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> search(@RequestBody CustomStatsRequest req) throws Exception {
        String field;
        switch (req.getSearchType()) {
            case "city": field = "city"; break;
            case "url": field = "id"; break;
            case "dns": field = "dns"; break;
            case "community": field = "owningComm"; break;
            case "collection": field = "owningColl"; break;
            case "item": field = "owningItem"; break;
            default: throw new IllegalArgumentException("Invalid searchType");
        }

        String q = buildQuery(req) + " AND " + field + ":*" + req.getKeyword() + "*";
        SolrQuery query = new SolrQuery(q);
        query.setRows(0);
        query.addFilterQuery("time:[" + req.getFromdate() + "T00:00:00Z TO " + req.getTodate() + "T23:59:59Z]");
        query.setFacet(true);
        query.addFacetField(field);
        query.setFacetLimit(50);

        try (SolrClient solr = new HttpSolrClient.Builder(SOLR_URL).build()) {
            QueryResponse response = solr.query(query);
            List<Map<String, Object>> data = new ArrayList<>();
            FacetField facet = response.getFacetField(field);
            if (facet != null) {
                for (FacetField.Count c : facet.getValues()) {
                    if (c.getName().contains(req.getKeyword())) {
                        Map<String, Object> point = new LinkedHashMap<>();
                        point.put("value", c.getName());
                        point.put("count", c.getCount());
                        data.add(point);
                    }
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("data", data);
            return result;
        }
    }
}
