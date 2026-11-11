package ca.gestionchalet.config;

import ca.gestionchalet.client.DriveClient;
import ca.gestionchalet.client.GoogleDriveClient;
import ca.gestionchalet.client.GoogleSheetClient;
import ca.gestionchalet.client.SheetClient;
import ca.gestionchalet.domain.CopyRules;
import ca.gestionchalet.domain.RegenerationRules;
import ca.gestionchalet.domain.SheetTarget;
import ca.gestionchalet.service.GoogleTokenService;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

/**
 * La copie vers Google : les adaptateurs de Drive et de Sheets, avec les adresses et le délai de {@code recus.copie.*} (les seuls hôtes contactés, CS25), et le jeton d'accès
 * que leur donne {@link GoogleTokenService}. La planification est celle de Spring ({@code @Scheduled}) ; son fil unique est celui que Spring Boot crée.
 */
@Configuration
public class CopyConfiguration {

    private static final int SHUTDOWN_WAIT_SECONDS = 30;

    private final ApplicationProperties.Copy copy;

    public CopyConfiguration(ApplicationProperties applicationProperties) {
        copy = applicationProperties.copie();
    }

    @Bean
    CopyRules copyRules() {
        return new CopyRules(copy.annees().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> new SheetTarget(entry.getValue().fichier(), entry.getValue().onglet()))));
    }

    @Bean
    RegenerationRules regenerationRules() {
        return new RegenerationRules(
                copy.annees().entrySet().stream().filter(entry -> entry.getValue().fichier() != null)
                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().fichier())),
                copy.ongletGenere(), copy.suffixeOngletTemporaire());
    }

    /**
     * Le fil unique de la régénération (R-43) : un exécuteur de Spring, dont l'arrêt est géré par le contexte, qui attend la fin d'une écriture en cours avant d'interrompre. La file tient une
     * demande de plus que celle qui s'exécute : l'état {@code EN_COURS} en refuse déjà toute autre, et une demande rejetée n'est jamais laissée en cours.
     */
    @Bean
    ThreadPoolTaskExecutor regenerationExecutor(ThreadPoolTaskExecutorBuilder builder) {
        return builder.corePoolSize(1).maxPoolSize(1).queueCapacity(1).threadNamePrefix("regeneration-sheet-").awaitTermination(true)
                .awaitTerminationPeriod(Duration.ofSeconds(SHUTDOWN_WAIT_SECONDS)).build();
    }

    /**
     * Un bean {@code Executor} de plus fait disparaître celui que Spring Boot crée de lui-même (condition sur le type) : il est redéclaré ici, tel que Spring Boot le construit, pour que
     * ni Spring MVC ni les tâches asynchrones ne changent d'exécuteur.
     */
    @Bean(name = {"applicationTaskExecutor", "taskExecutor"})
    ThreadPoolTaskExecutor applicationTaskExecutor(ThreadPoolTaskExecutorBuilder builder) {
        return builder.build();
    }

    @Bean
    DriveClient driveClient(GoogleTokenService tokens) {
        return new GoogleDriveClient(restClient(copy.adresseDrive()), tokens::accessToken, copy.dossierDrive());
    }

    @Bean
    SheetClient sheetClient(GoogleTokenService tokens) {
        return new GoogleSheetClient(restClient(copy.adresseSheets()), tokens::accessToken, copy.adresseLienFichier());
    }

    /** Le client HTTP du JDK, comme celui des jetons : en mode flux, {@code HttpURLConnection} perd le corps d'un 401. Sans délai, un Google muet retiendrait la copie sans fin. */
    private RestClient restClient(String address) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(copy.delaiAppel()).build());
        requestFactory.setReadTimeout(copy.delaiAppel());
        return RestClient.builder().requestFactory(requestFactory).baseUrl(address).build();
    }

    /** La planification de Spring, absente quand {@code recus.copie.automatique} est faux : sans elle, aucun fil ne copie. */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "recus.copie.automatique", havingValue = "true", matchIfMissing = true)
    static class AutomaticCopyConfiguration {
    }
}
