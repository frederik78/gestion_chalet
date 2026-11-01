package ca.gestionchalet.config;

import jakarta.servlet.DispatcherType;
import java.util.EnumSet;
import org.apache.catalina.core.StandardHost;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Ce qui s'applique à toute réponse, y compris avant la chaîne de sécurité et avant l'application. */
@Configuration
public class ServeurWebConfiguration {

    @Bean
    FilterRegistrationBean<EnTetesSecuriteFilter> enTetesSecuriteFilter() {
        FilterRegistrationBean<EnTetesSecuriteFilter> enregistrement = new FilterRegistrationBean<>(new EnTetesSecuriteFilter());
        enregistrement.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        enregistrement.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
        return enregistrement;
    }

    /** Après la chaîne de sécurité : une requête sans session ou sans jeton n'est jamais lue. */
    @Bean
    FilterRegistrationBean<CorpsBorneFilter> corpsBorneFilter(SecuriteProperties proprietes) {
        FilterRegistrationBean<CorpsBorneFilter> enregistrement =
                new FilterRegistrationBean<>(new CorpsBorneFilter(Math.toIntExact(proprietes.corpsRequeteMax().toBytes())));
        enregistrement.setOrder(0);
        return enregistrement;
    }

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> valveErreurServeur() {
        return usine -> usine.addContextCustomizers(contexte ->
                ((StandardHost) contexte.getParent()).setErrorReportValveClass(ErreurServeurValve.class.getName()));
    }
}
