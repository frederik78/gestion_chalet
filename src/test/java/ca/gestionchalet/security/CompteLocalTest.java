package ca.gestionchalet.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class CompteLocalTest {

    private static final String MESSAGE = "Condensé du mot de passe absent ou trop faible : bcrypt de coût 10 à 31 requis (recus.securite.condense-mot-de-passe).";

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
            "$2a$32$abcdefghijklmnopqrstuuabcdefghijklmnopqrstuvwxyz01234",
            "$2a$99$abcdefghijklmnopqrstuuabcdefghijklmnopqrstuvwxyz01234",
    })
    void cs07_condenseAbsentOuFaibleRefuseLaCreationDuCompte(String condense) {
        assertThatThrownBy(() -> new CompteLocal(condense))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(MESSAGE);
    }

    @Test
    void cs07_coutDixAcceptePourLaLimite() {
        String condense = bcrypt(10, "secret");

        assertThat(new CompteLocal(condense).loadUserByUsername("").getPassword()).isEqualTo(condense);
    }

    /** Spring Security efface le mot de passe de l'objet renvoyé après la connexion : la connexion suivante doit en recevoir un intact. */
    @Test
    void cs07_chaqueLectureRendUnCompteIntactQuelQueSoitLeNom() {
        CompteLocal compte = new CompteLocal(bcrypt(10, "secret"));
        UserDetails premier = compte.loadUserByUsername("");
        ((CredentialsContainer) premier).eraseCredentials();

        UserDetails second = compte.loadUserByUsername("autre");

        assertThat(second.getPassword()).startsWith("$2a$10$");
        assertThat(second.getUsername()).isEqualTo("proprietaire");
    }

    /** Le coût maximal n'est pas calculé : à 31, une seule vérification durerait des jours. Seule la création du compte est testée. */
    @Test
    void cs07_coutTrenteEtUnAcceptePourLaLimiteHaute() {
        assertThatCode(() -> new CompteLocal("$2a$31$abcdefghijklmnopqrstuuabcdefghijklmnopqrstuvwxyz01234")).doesNotThrowAnyException();
    }
}
