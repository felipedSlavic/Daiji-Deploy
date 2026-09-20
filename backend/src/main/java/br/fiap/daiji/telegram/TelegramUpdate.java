package br.fiap.daiji.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdate(@JsonProperty("update_id") Long updateId, Message message) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(Chat chat, String text) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chat(Long id, String type) {}
}
