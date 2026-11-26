package com.web3task.webhook.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class WebhookDispatchRequest {

    @NotBlank(message = "destinationUrl must not be blank")
    private String destinationUrl;

    @NotBlank(message = "eventId must not be blank")
    private String eventId;

    @NotNull(message = "payload must not be null")
    private JsonNode payload;

    public WebhookDispatchRequest() {
    }

    public WebhookDispatchRequest(String destinationUrl, String eventId, JsonNode payload) {
        this.destinationUrl = destinationUrl;
        this.eventId = eventId;
        this.payload = payload;
    }

    public String getDestinationUrl() {
        return destinationUrl;
    }

    public void setDestinationUrl(String destinationUrl) {
        this.destinationUrl = destinationUrl;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public void setPayload(JsonNode payload) {
        this.payload = payload;
    }
}
