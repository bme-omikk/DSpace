package org.dspace.app.rest.statistics;

public class CustomStatsRequest {
    private String fromdate;
    private String todate;
    private boolean isbot;
    private int resolution;
    private String resourceType;
    private String searchType;
    private String keyword;

    public String getFromdate() { return fromdate; }
    public void setFromdate(String fromdate) { this.fromdate = fromdate; }

    public String getTodate() { return todate; }
    public void setTodate(String todate) { this.todate = todate; }

    public boolean isIsbot() { return isbot; }
    public void setIsbot(boolean isbot) { this.isbot = isbot; }

    public int getResolution() { return resolution; }
    public void setResolution(int resolution) { this.resolution = resolution; }

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public String getSearchType() { return searchType; }
    public void setSearchType(String searchType) { this.searchType = searchType; }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
}
