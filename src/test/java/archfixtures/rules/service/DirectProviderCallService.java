package archfixtures.rules.service;

import org.springframework.ai.chat.model.ChatModel;

/** Violation : un service externe s'appelle seulement depuis client/. */
public class DirectProviderCallService {
    ChatModel modele;
}
