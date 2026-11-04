package ca.gestionchalet.client;

import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.model.ChatModel;

/** Appelle le modèle configuré par Spring AI. */
public class AnthropicRecuExtractor implements RecuExtractor {

    private final ChatModel chatModel;

    public AnthropicRecuExtractor(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public Map<String, Object> extract(List<byte[]> images, List<String> expenseTypes) {
        throw new RecuExtractionException("non branché");
    }
}
