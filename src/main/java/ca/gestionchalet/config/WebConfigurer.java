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

/** Ce qui s'applique à toute réponse, y compris avant la chaîne de sécurité et avant l'application. CORS et console H2 de JHipster exclus : même origine, et pas de H2. */
@Configuration
public class WebConfigurer {

    @Bean
    FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilter() {
        FilterRegistrationBean<SecurityHeadersFilter> registration = new FilterRegistrationBean<>(new SecurityHeadersFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registration.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
        return registration;
    }

    /** Après la chaîne de sécurité, dont il consulte le résultat : seul le corps d'une session ouverte est lu. */
    @Bean
    FilterRegistrationBean<RequestBodyLimitFilter> requestBodyLimitFilter(ApplicationProperties properties) {
        FilterRegistrationBean<RequestBodyLimitFilter> registration =
                new FilterRegistrationBean<>(new RequestBodyLimitFilter(Math.toIntExact(properties.corpsRequeteMax().toBytes())));
        registration.setOrder(0);
        return registration;
    }

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> jsonErrorReportValve() {
        return factory -> factory.addContextCustomizers(context ->
                ((StandardHost) context.getParent()).setErrorReportValveClass(JsonErrorReportValve.class.getName()));
    }
}
