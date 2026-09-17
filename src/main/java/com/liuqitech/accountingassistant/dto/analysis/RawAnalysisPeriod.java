package com.liuqitech.accountingassistant.dto.analysis;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class RawAnalysisPeriod {
    @JsonPropertyDescription("CURRENT_DAY, CURRENT_WEEK, CURRENT_MONTH, CURRENT_QUARTER, CURRENT_YEAR, PREVIOUS_MONTH, PREVIOUS_YEAR, LAST_N_MONTHS, EXPLICIT_RANGE")
    private String preset;
    private String start;
    private String end;
    private Integer count;

    public RawAnalysisPeriod() {}

    public String getPreset() { return preset; }
    public void setPreset(String value) { this.preset = value; }
    public String getStart() { return start; }
    public void setStart(String value) { this.start = value; }
    public String getEnd() { return end; }
    public void setEnd(String value) { this.end = value; }
    public Integer getCount() { return count; }
    public void setCount(Integer value) { this.count = value; }
}
