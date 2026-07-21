package org.dspace.app.rest.statistics;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.RangeFacet;
import org.dspace.content.Community;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CommunityService;
import org.dspace.core.Context;
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
    private static final CommunityService communityService
            = ContentServiceFactory.getInstance().getCommunityService();
    
    private static final String SOLR_URL = configurationService.getProperty("solr.server") + "/statistics/";
    private static final String SEARCH_URL = configurationService.getProperty("solr.server") + "/"
            + configurationService.getProperty("solr.multicorePrefix", "") + "search";

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
        query.setFacetLimit(20);
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
        query.setFacetLimit(10);
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

    // 6. Items submitted in the current year: total + breakdown by top-level community
    @PostMapping(value = "/submissions/byyear", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> submissionsByYear(@RequestBody CustomStatsRequest req) throws Exception {
        int year = req.getYear() > 0 ? req.getYear() : LocalDate.now().getYear();
        String start = year + "-01-01T00:00:00Z";
        String end   = year + "-12-31T23:59:59Z";

        SolrQuery query = new SolrQuery("*:*");
        query.setRows(0);
        query.addFilterQuery("search.resourcetype:Item");
        query.addFilterQuery("dc.date.accessioned_dt:[" + start + " TO " + end + "]");
        query.addFilterQuery("archived:true");
        query.addFilterQuery("withdrawn:false");
        query.setFacet(true);
        query.addFacetField("location.comm");
        query.setFacetLimit(-1);
        query.setFacetMinCount(1);
        query.setFacetSort("location.comm");

        try (SolrClient solr = new HttpSolrClient.Builder(SEARCH_URL).build()) {
            QueryResponse response = solr.query(query);

            // Collect top-level community UUIDs to filter out sub-communities
            Set<String> topLevelUuids;
            try (Context ctx = new Context()) {
                topLevelUuids = communityService.findAllTop(ctx).stream()
                        .map(c -> c.getID().toString())
                        .collect(Collectors.toSet());
            }

            List<Map<String, Object>> communities = new ArrayList<>();
            FacetField facet = response.getFacetField("location.comm");
            if (facet != null) {
                for (FacetField.Count c : facet.getValues()) {
                    if (!topLevelUuids.contains(c.getName())) continue;
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("uuid", c.getName());
                    entry.put("count", c.getCount());
                    communities.add(entry);
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("year", year);
            result.put("total", response.getResults().getNumFound());
            result.put("byCommunity", communities);
            return result;
        }
    }

    // 7. Total items in the archive as of a given date (using dc.date.accessioned_dt)
    @PostMapping(value = "/items/asofdate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> itemsAsOfDate(@RequestBody CustomStatsRequest req) throws Exception {
        // req.getTodate() must be provided in "yyyy-MM-dd" format
        String cutoff = req.getTodate() + "T23:59:59Z";

        SolrQuery query = new SolrQuery("*:*");
        query.setRows(0);
        query.addFilterQuery("search.resourcetype:Item");
        query.addFilterQuery("dc.date.accessioned_dt:[* TO " + cutoff + "]");
        query.addFilterQuery("archived:true");
        query.addFilterQuery("withdrawn:false");

        try (SolrClient solr = new HttpSolrClient.Builder(SEARCH_URL).build()) {
            QueryResponse response = solr.query(query);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("asOfDate", req.getTodate());
            result.put("total", response.getResults().getNumFound());
            return result;
        }
    }
}
