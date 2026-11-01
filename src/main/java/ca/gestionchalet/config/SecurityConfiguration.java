package ca.gestionchalet.config;

import ca.gestionchalet.security.CompteLocal;
import ca.gestionchalet.security.LimiteurConnexion;
import ca.gestionchalet.security.SessionUtilisateur;
import ca.gestionchalet.web.rest.errors.ErreursSecurite;
import jakarta.servlet.DispatcherType;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/** Chaîne de sécurité explicite, refus par défaut (ADR 0001). */
@Configuration
@EnableConfigurationProperties(SecuriteProperties.class)
public class SecurityConfiguration {

    /** La santé et les seules ressources nécessaires à la page de connexion, listées une à une. */
    private static final String[] RESSOURCES_PUBLIQUES = {
        "/actuator/health", "/", "/index.html", "/connexion.js", "/commun.js", "/style.css",
        "/manifest.webmanifest", "/sw.js", "/icones/icone-192.png", "/icones/icone-512.png"
    };

    @Bean
    SecurityFilterChain chaineDeSecurite(HttpSecurity http, CsrfTokenRepository jetons, SecurityContextRepository contextes) {
        ErreursSecurite erreurs = new ErreursSecurite();
        return http
                .authorizeHttpRequests(acces -> acces
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, RESSOURCES_PUBLIQUES).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/connexion").permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.csrfTokenRepository(jetons).csrfTokenRequestHandler(new JetonCsrfParEnTete()))
                .securityContext(contexte -> contexte.securityContextRepository(contextes))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(erreurs).accessDeniedHandler(erreurs))
                .headers(AbstractHttpConfigurer::disable) // posés par EnTetesSecuriteFilter, avant le pare-feu HTTP
                .requestCache(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .build();
    }

    @Bean
    CsrfTokenRepository jetonsCsrf(SecuriteProperties proprietes) {
        CookieCsrfTokenRepository jetons = CookieCsrfTokenRepository.withHttpOnlyFalse();
        jetons.setCookieCustomizer(cookie -> cookie.secure(proprietes.cookieSecurise()).sameSite("Lax").path("/"));
        return jetons;
    }

    @Bean
    SecurityContextRepository contextesDeSecurite() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SessionUtilisateur sessionUtilisateur(SecurityContextRepository contextes, CsrfTokenRepository jetons, SecuriteProperties proprietes) {
        CsrfAuthenticationStrategy renouvellement = new CsrfAuthenticationStrategy(jetons);
        renouvellement.setRequestHandler(new JetonCsrfParEnTete());
        return new SessionUtilisateur(contextes, jetons, renouvellement, proprietes.inactiviteSession(), proprietes.cookieSecurise());
    }

    @Bean
    Clock horloge() {
        return Clock.systemUTC();
    }

    @Bean
    LimiteurConnexion limiteurConnexion(SecuriteProperties proprietes, Clock horloge) {
        return new LimiteurConnexion(proprietes.echecsAvantBlocage(), proprietes.dureeBlocage(), proprietes.sourcesSuiviesMax(), horloge);
    }

    @Bean
    CompteLocal compteLocal(SecuriteProperties proprietes) {
        return new CompteLocal(proprietes.condenseMotDePasse());
    }
}
