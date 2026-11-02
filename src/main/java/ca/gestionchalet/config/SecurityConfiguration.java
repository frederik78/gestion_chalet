package ca.gestionchalet.config;

import ca.gestionchalet.security.CompteLocal;
import ca.gestionchalet.security.LimiteurConnexion;
import ca.gestionchalet.web.rest.errors.ErreursSecurite;
import jakarta.servlet.DispatcherType;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/** Chaîne de sécurité explicite, refus par défaut (ADR 0001). */
@Configuration
@EnableConfigurationProperties(SecuriteProperties.class)
public class SecurityConfiguration {

    private static final String SAME_SITE = "Lax";

    /** La santé et les seules ressources nécessaires à la page de connexion, listées une à une. */
    private static final String[] RESSOURCES_PUBLIQUES = {
        "/actuator/health", "/", "/index.html", "/connexion.js", "/commun.js", "/style.css",
        "/manifest.webmanifest", "/sw.js", "/icones/icone-192.png", "/icones/icone-512.png"
    };

    @Bean
    SecurityFilterChain chaineDeSecurite(HttpSecurity http, SecuriteProperties proprietes, LimiteurConnexion limiteur) {
        ErreursSecurite erreurs = new ErreursSecurite();
        CookieCsrfTokenRepository jetons = CookieCsrfTokenRepository.withHttpOnlyFalse();
        jetons.setCookieCustomizer(cookie -> cookie.secure(proprietes.cookieSecurise()).sameSite(SAME_SITE).path("/"));
        return http
                .authorizeHttpRequests(acces -> acces
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, RESSOURCES_PUBLIQUES).permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.csrfTokenRepository(jetons).csrfTokenRequestHandler(new JetonCsrfParEnTete()))
                .formLogin(formulaire -> formulaire
                        .loginPage("/")
                        .loginProcessingUrl(ErreursSecurite.ROUTE_CONNEXION)
                        .passwordParameter("motDePasse")
                        .successHandler((requete, reponse, authentification) -> {
                            limiteur.reinitialiser(requete.getRemoteAddr());
                            reponse.setStatus(HttpStatus.NO_CONTENT.value());
                        })
                        .failureHandler(erreurs))
                .logout(deconnexion -> deconnexion
                        .logoutUrl("/api/deconnexion")
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .addFilterBefore(
                        new GardeConnexionFilter(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, ErreursSecurite.ROUTE_CONNEXION),
                                limiteur, proprietes.corpsRequeteMax().toBytes()),
                        UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(erreurs).accessDeniedHandler(erreurs))
                .headers(AbstractHttpConfigurer::disable) // posés par EnTetesSecuriteFilter, avant le pare-feu HTTP
                .requestCache(AbstractHttpConfigurer::disable)
                .build();
    }

    @Bean
    PasswordEncoder encodeurMotDePasse() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    Clock horloge() {
        return Clock.systemUTC();
    }

    @Bean
    LimiteurConnexion limiteurConnexion(SecuriteProperties proprietes, Clock horloge) {
        return new LimiteurConnexion(proprietes.echecsAvantBlocage(), proprietes.dureeBlocage(), proprietes.sourcesSuiviesMax(), horloge);
    }

    /** Déclaré ici et non annoté {@code @Service} : la couche {@code security} n'a pas accès à {@code config} (ADR 0001). */
    @Bean
    CompteLocal compteLocal(SecuriteProperties proprietes) {
        return new CompteLocal(proprietes.condenseMotDePasse());
    }
}
