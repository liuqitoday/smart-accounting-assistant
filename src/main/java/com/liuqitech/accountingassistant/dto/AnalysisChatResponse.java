package com.liuqitech.accountingassistant.dto;

public class AnalysisChatResponse {
    private AnalysisMessageDto userMessage;
    private AnalysisMessageDto assistantMessage;

    public AnalysisChatResponse() {}

    public AnalysisChatResponse(AnalysisMessageDto userMessage, AnalysisMessageDto assistantMessage) {
        this.userMessage = userMessage;
        this.assistantMessage = assistantMessage;
    }

    public AnalysisMessageDto getUserMessage() { return userMessage; }
    public void setUserMessage(AnalysisMessageDto userMessage) { this.userMessage = userMessage; }

    public AnalysisMessageDto getAssistantMessage() { return assistantMessage; }
    public void setAssistantMessage(AnalysisMessageDto assistantMessage) { this.assistantMessage = assistantMessage; }
}
