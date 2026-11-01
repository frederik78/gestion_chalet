package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Le contexte complet démarre sans clé d'API : Spring AI 2.0 est compatible avec Spring Boot 4.1 (ADR 0001). */
class GestionChaletApplicationTest {

    @Test
    void leContexteDemarreSansCleAnthropic() {
        try (ConfigurableApplicationContext contexte = new SpringApplicationBuilder(GestionChaletApplication.class)
                .web(WebApplicationType.SERVLET)
                .properties("server.port=0",
                        "recus.securite.condense-mot-de-passe=" + new BCryptPasswordEncoder(10).encode("secret"))
                .run()) {
            assertThat(contexte.getBean(ChatModel.class)).isNotNull();
        }
    }
}
