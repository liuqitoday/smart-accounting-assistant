package com.liuqitech.accountingassistant.enums;

public enum AnalysisQueryType {
    AGGREGATE,  // 聚合：总额、笔数、均值
    TOP_N,      // 排行：最大N笔
    GROUP_BY,   // 分组：按分类/账户/商家/标签
    TREND,      // 趋势：按月/日
    LIST        // 明细：筛选条件下的交易列表
}
