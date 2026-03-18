package org.dspace.app.rest.statistics;

public class CustomStatsRequest {
    private String fromdate;
    private String todate;
    private boolean isbot;
    private int resolution;

    public String getFromdate() { return fromdate; }
    public void setFromdate(String fromdate) { this.fromdate = fromdate; }

    public String getTodate() { return todate; }
    public void setTodate(String todate) { this.todate = todate; }

    public boolean isIsbot() { return isbot; }
    public void setIsbot(boolean isbot) { this.isbot = isbot; }

    public int getResolution() { return resolution; }
    public void setResolution(int resolution) { this.resolution = resolution; }
}
