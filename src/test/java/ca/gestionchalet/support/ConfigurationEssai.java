package ca.gestionchalet.support;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;

/**
 * Doubles des tests sur serveur réel : une horloge pilotable, et une source de requête choisie par le test (une boucle
 * locale n'offre qu'une seule adresse). L'application, elle, lit l'adresse distante de la requête, sans ce filtre.
 */
@TestConfiguration
public class ConfigurationEssai {

    public static final String MOT_DE_PASSE = "mot-de-passe-d-essai";

    /** bcrypt, coût 10, de {@link #MOT_DE_PASSE}. */
    public static final String CONDENSE = "$2y$10$l1HeZTHlUnXFqBxpDxj2uO40y7JL2DuYKZmGzdDo2T3DyMkbKaIhC";

    @Bean
    RouteEssaiResource routeEssaiResource() {
        return new RouteEssaiResource();
    }

    @Bean
    @Primary
    HorlogeEssai horlogeEssai() {
        return new HorlogeEssai();
    }

    @Bean
    FilterRegistrationBean<Filter> sourceEssaiFilter() {
        Filter filtre = (requete, reponse, suite) -> {
            HttpServletRequest http = (HttpServletRequest) requete;
            String source = http.getHeader(ClientEssai.EN_TETE_SOURCE);
            suite.doFilter(source == null ? http : new HttpServletRequestWrapper(http) {
                @Override
                public String getRemoteAddr() {
                    return source;
                }
            }, reponse);
        };
        FilterRegistrationBean<Filter> enregistrement = new FilterRegistrationBean<>(filtre);
        enregistrement.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return enregistrement;
    }
}
