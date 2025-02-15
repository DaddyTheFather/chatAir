package com.theokanning.openai.completion.chat.anthropic;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Created by flyun on 2024/4/4.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatAMessage {

   /**
    * You may use {@link ChatAMessageType} enum.
    */
   String type;
   String text;

   @JsonProperty("source")
   ChatAContentSource source;
}
