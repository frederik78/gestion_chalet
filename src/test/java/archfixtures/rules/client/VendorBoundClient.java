package archfixtures.rules.client;

import org.springframework.ai.anthropic.AnthropicChatOptions;

/** Violation (D-64) : une classe dépend du fournisseur du modèle au lieu de l'interface commune de Spring AI. */
public class VendorBoundClient {
    AnthropicChatOptions options;
}
