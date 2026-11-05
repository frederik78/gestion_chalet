package ca.gestionchalet.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.gestionchalet.client.RecuExtractionException.Failure;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.StructuredOutputChatOptions;

/** D-64 : le classement des pannes ne connaît aucun fournisseur ; il lit la chaîne des causes d'un {@code ChatModel} doublé. */
class ChatModelRecuExtractorTest {

    private static final String TEMOIN = "FOURNISSEUR-TEMOIN";

    /** Double de {@code ChatModel} : les options par défaut sont celles de l'interface commune, la réponse vient de la fonction donnée. */
    private static ChatModel modele(Function<Prompt, ChatResponse> reponse) {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return reponse.apply(prompt);
            }

            @Override
            public ChatOptions getDefaultOptions() {
                return StructuredOutputChatOptions.builder().model("modele-essai").maxTokens(1024).build();
            }
        };
    }

    private static Failure echec(Function<Prompt, ChatResponse> reponse) {
        RecuExtractionException e = (RecuExtractionException) catchThrowable(modele(reponse));
        assertThat(e.getMessage()).doesNotContain(TEMOIN);
        return e.failure();
    }

    private static Throwable catchThrowable(ChatModel modele) {
        return org.assertj.core.api.Assertions.catchThrowable(() -> new ChatModelRecuExtractor(modele).extract(List.of(new byte[] {1}), List.of("Fournitures")));
    }

    private static Function<Prompt, ChatResponse> leve(RuntimeException e) {
        return prompt -> {
            throw e;
        };
    }

    static List<Throwable> causesDeJoignabilite() {
        return List.of(new IOException(TEMOIN), new ConnectException(TEMOIN), new SocketTimeoutException(TEMOIN),
                new HttpTimeoutException(TEMOIN), new TimeoutException(TEMOIN));
    }

    @ParameterizedTest
    @MethodSource("causesDeJoignabilite")
    void ca04_uneCauseReseauOuUnDelaiDonneInjoignable(Throwable cause) {
        assertThat(echec(leve(new IllegalStateException(TEMOIN, cause)))).isEqualTo(Failure.UNREACHABLE);
        assertThat(echec(leve(new IllegalStateException(TEMOIN, new RuntimeException(TEMOIN, cause))))).isEqualTo(Failure.UNREACHABLE);
    }

    @Test
    void ca04_uneAutreErreurDeLAppelDonneErreurDeService() {
        assertThat(echec(leve(new IllegalStateException(TEMOIN)))).isEqualTo(Failure.SERVICE_ERROR);
        assertThat(echec(leve(new IllegalStateException(TEMOIN, new IllegalArgumentException(TEMOIN))))).isEqualTo(Failure.SERVICE_ERROR);
    }

    @Test
    void ca04_uneReponseVideDonneReponseInvalide() {
        assertThat(echec(prompt -> null)).isEqualTo(Failure.INVALID_RESPONSE);
        assertThat(echec(prompt -> new ChatResponse(List.of()))).isEqualTo(Failure.INVALID_RESPONSE);
        assertThat(echec(prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage(null)))))).isEqualTo(Failure.INVALID_RESPONSE);
    }

    @Test
    void ca04_unTexteIllisibleDonneReponseInvalide() {
        assertThat(echec(prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage(TEMOIN + " pas du JSON")))))).isEqualTo(Failure.INVALID_RESPONSE);
    }

    @Test
    void ca08_leSchemaEtLesReglagesPartentParLInterfaceCommune() {
        List<Prompt> recus = new java.util.ArrayList<>();
        new ChatModelRecuExtractor(modele(prompt -> {
            recus.add(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("{\"tps\":null}"))));
        })).extract(List.of(new byte[] {1}), List.of("Fournitures"));

        ChatOptions options = recus.getFirst().getOptions();
        assertThat(options.getModel()).isEqualTo("modele-essai");
        assertThat(options.getMaxTokens()).isEqualTo(1024);
        assertThat(options.getTemperature()).isNull();
        assertThat(options.getTopP()).isNull();
        assertThat(options.getTopK()).isNull();
        assertThat(((StructuredOutputChatOptions) options).getOutputSchema()).contains("montantTotal", "numeroInscriptionTvq", "\"additionalProperties\":false");
    }

    @Test
    void d64_desOptionsSansSchemaImposeFontEchouerLeDemarrage() {
        ChatModel sansSchema = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw new AssertionError("jamais appelé");
            }

            @Override
            public ChatOptions getDefaultOptions() {
                return ChatOptions.builder().model("modele-essai").maxTokens(1024).build();
            }
        };

        assertThatThrownBy(() -> new ChatModelRecuExtractor(sansSchema)).isInstanceOf(IllegalStateException.class);
    }
}
