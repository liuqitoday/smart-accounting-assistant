package com.liuqitech.accountingassistant.dto;

import java.util.List;

/**
 * 更新交易标签请求
 */
public class UpdateTagsRequest {

    private List<Long> tagIds;

    public UpdateTagsRequest() {}

    public UpdateTagsRequest(List<Long> tagIds) {
        this.tagIds = tagIds;
    }

    public List<Long> getTagIds() {
        return tagIds;
    }

    public void setTagIds(List<Long> tagIds) {
        this.tagIds = tagIds;
    }
}
