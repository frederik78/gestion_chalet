package ca.gestionchalet.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class CompteLocalTest {

    private static final String MESSAGE = "Condensé du mot de passe absent ou trop faible : bcrypt de coût 10 au moins requis (recus.securite.condense-mot-de-passe).";

    private static String bcrypt(int cout, String motDePasse) {
        return new BCryptPasswordEncoder(cout).encode(motDePasse);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",
            "   ",
            "motdepasse",
            "{noop}motdepasse",
            "5f4dcc3b5aa765d61d8327deb882cf99",
            "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8",
            "$1$abcdefgh$abcdefghijklmnopqrstuv",
            "$2a$10$tropcourt",
            "$2a$04$abcdefghijklmnopqrstuuabcdefghijklmnopqrstuvwxyz01234",
            "$2a$09$abcdefghijklmnopqrstuuabcdefghijklmnopqrstuvwxyz01234",
    })
    void cs07_condenseAbsentOuFaibleRefuseLaCreationDuCompte(String condense) {
        assertThatThrownBy(() -> new CompteLocal(condense))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(MESSAGE);
    }

    @Test
    void cs07_coutDixAcceptePourLaLimite() {
        CompteLocal compte = new CompteLocal(bcrypt(10, "secret"));

        assertThat(compte.verifier("secret")).isTrue();
        assertThat(compte.verifier("autre")).isFalse();
    }

    @Test
    void cs04_motDePasseDeMilleCaracteresEstRefuseSansErreur() {
        CompteLocal compte = new CompteLocal(bcrypt(10, "secret"));

        assertThat(compte.verifier("x".repeat(1000))).isFalse();
    }
}
