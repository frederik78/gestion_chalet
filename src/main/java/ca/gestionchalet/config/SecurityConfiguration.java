package ca.gestionchalet.config;

import ca.gestionchalet.security.LoginRateLimiter;
import ca.gestionchalet.web.rest.errors.SecurityErrorHandler;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.util.function.Supplier;
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
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;

/** Chaîne de sécurité explicite, refus par défaut (ADR 0001). */
@Configuration
@EnableConfigurationProperties(SecuriteProperties.class)
public class SecurityConfiguration {

    private static final String ROUTE_CONNEXION = "/api/connexion";
    private static final String SAME_SITE = "Lax";

    /** La santé et les seules ressources nécessaires à la page de connexion, listées une à une. */
    private static final String[] RESSOURCES_PUBLIQUES = {
        "/actuator/health", "/", "/index.html", "/connexion.js", "/commun.js", "/style.css",
        "/manifest.webmanifest", "/sw.js", "/icones/icone-192.png", "/icones/icone-512.png"
    };

    private final SecuriteProperties securiteProperties;

    public SecurityConfiguration(SecuriteProperties securiteProperties) {
        this.securiteProperties = securiteProperties;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, LoginRateLimiter limiteur) {
        RequestMatcher connexion = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, ROUTE_CONNEXION);
        SecurityErrorHandler erreurs = new SecurityErrorHandler(connexion);
        CookieCsrfTokenRepository jetons = CookieCsrfTokenRepository.withHttpOnlyFalse();
        jetons.setCookieCustomizer(cookie -> cookie.secure(securiteProperties.cookieSecurise()).sameSite(SAME_SITE).path("/"));
        http
            .csrf(csrf -> csrf.csrfTokenRepository(jetons).csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
            .authorizeHttpRequests(authz -> authz
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, RESSOURCES_PUBLIQUES).permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(exceptionHandling -> exceptionHandling.authenticationEntryPoint(erreurs).accessDeniedHandler(erreurs))
            .formLogin(formLogin -> formLogin
                .loginPage("/") // alignement JHipster, sans effet mesuré
                .loginProcessingUrl(ROUTE_CONNEXION)
                .passwordParameter("motDePasse")
                .successHandler((request, response, authentication) -> {
                    limiteur.reset(request.getRemoteAddr());
                    response.setStatus(HttpStatus.NO_CONTENT.value());
                })
                .failureHandler(erreurs)) // sans permitAll : il ouvrirait toutes les méthodes sur "/" et la connexion, contre le refus par défaut (ADR 0001)
            .logout(logout -> logout
                .logoutUrl("/api/deconnexion")
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                .permitAll()) // alignement JHipster, sans effet mesuré
            .addFilterBefore(
                new GardeConnexionFilter(connexion, limiteur, securiteProperties.corpsRequeteMax().toBytes()),
                UsernamePasswordAuthenticationFilter.class)
            .headers(AbstractHttpConfigurer::disable) // posés par EnTetesSecuriteFilter, avant le pare-feu HTTP
            .requestCache(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    Clock horloge() {
        return Clock.systemUTC();
    }

    @Bean
    LoginRateLimiter limiteurConnexion(Clock horloge) {
        return new LoginRateLimiter(securiteProperties.echecsAvantBlocage(), securiteProperties.dureeBlocage(), securiteProperties.sourcesSuiviesMax(), horloge);
    }

    /**
     * Le gestionnaire CSRF de JHipster, à un écart près : sans en-tête, il ne lit aucun paramètre. Le corps n'est donc jamais
     * analysé avant le contrôle CSRF ; avec {@code csrf.spa()} ou le code de JHipster, un formulaire au corps non terminé bloque
     * le refus (mesuré : pas de réponse en 5 s, cs02).
     */
    static final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

        private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
        private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
            this.xor.handle(request, response, csrfToken);
            // Charge le jeton pour que son cookie soit posé dès la page de connexion.
            csrfToken.get();
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
            if (StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))) {
                return this.plain.resolveCsrfTokenValue(request, csrfToken);
            }
            return null;
        }
    }
}
