package archfixtures.regles.service;

import org.springframework.ai.chat.model.ChatModel;

/** Violation : un service externe s'appelle seulement depuis client/. */
public class AppelDirectFournisseurService {
    ChatModel modele;
}
