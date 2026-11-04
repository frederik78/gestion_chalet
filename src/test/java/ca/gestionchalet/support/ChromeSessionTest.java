package ca.gestionchalet.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Le pilote des tests d'écran, avec un faux navigateur : aucun Chrome requis. */
class ChromeSessionTest {

    private static final String FAUX_CHROME = """
            #!/bin/sh
            for argument in "$@"; do
              case "$argument" in --user-data-dir=*) profil="${argument#--user-data-dir=}" ;; esac
            done
            printf '%%s\\n' "$$" > "$profil/pid"
            printf '%d\\n' > "$profil/DevToolsActivePort"
            exec sleep 60
            """;

    /** Comme un Chrome réel, le principal lance un enfant qui écrit dans le profil et lui survit s'il n'est pas arrêté. */
    private static final String FAUX_CHROME_AVEC_ENFANT = """
            #!/bin/sh
            for argument in "$@"; do
              case "$argument" in --user-data-dir=*) profil="${argument#--user-data-dir=}" ;; esac
            done
            sh -c 'trap "" TERM; while :; do : > "$0/enfant.$$"; rm -f "$0/enfant.$$"; done' "$profil" &
            printf '%%s\\n' "$!" > "$profil/pid"
            printf '%d\\n' > "$profil/DevToolsActivePort"
            exec sleep 60
            """;

    @TempDir
    Path dossier;

    private static int portFerme() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private Path fauxChrome(int port) throws IOException {
        Path binaire = dossier.resolve("faux-chrome");
        Files.writeString(binaire, FAUX_CHROME.formatted(port));
        Files.setPosixFilePermissions(binaire, PosixFilePermissions.fromString("rwx------"));
        return binaire;
    }

    private static void verifierQueChromeEstArrete(Path profil) throws IOException {
        long pid = Long.parseLong(Files.readString(profil.resolve("pid")).strip());
        Optional<ProcessHandle> processus = ProcessHandle.of(pid).filter(ProcessHandle::isAlive);
        processus.ifPresent(ProcessHandle::destroyForcibly);
        assertThat(processus).isEmpty();
    }

    /** Délai d'une seconde au lieu des 20 s du pilote, pour un test court ; au-delà de la borne du test, le constructeur est bloqué. */
    private void verifierQueLeConstructeurEchoueSansBloquer(int port) throws IOException {
        Path binaire = fauxChrome(port);
        Path profil = Files.createDirectory(dossier.resolve("profil"));

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> assertThatThrownBy(
                () -> new ChromeSession(binaire.toString(), profil, Duration.ofSeconds(1))).isInstanceOf(IOException.class));

        verifierQueChromeEstArrete(profil);
    }

    @Test
    void chromeEstArreteQuandLaConnexionEchoueDansLeConstructeur() throws IOException {
        Path binaire = fauxChrome(portFerme());
        Path profil = Files.createDirectory(dossier.resolve("profil"));

        assertThatThrownBy(() -> new ChromeSession(binaire.toString(), profil)).isInstanceOf(ConnectException.class);

        verifierQueChromeEstArrete(profil);
    }

    @Test
    void unPortQuiAccepteSansRepondreNeBloquePasLeConstructeur() throws IOException {
        // Sans accept() : le système accepte la connexion, personne ne répond.
        try (ServerSocket muet = new ServerSocket(0)) {
            verifierQueLeConstructeurEchoueSansBloquer(muet.getLocalPort());
        }
    }

    @Test
    void uneOuvertureDeWebSocketSansReponseNeBloquePasLeConstructeur() throws IOException {
        try (ServerSocket muet = new ServerSocket(0)) {
            String cibles = "[{\"type\":\"page\",\"webSocketDebuggerUrl\":\"ws://127.0.0.1:" + muet.getLocalPort() + "/\"}]";
            HttpServer devTools = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            devTools.createContext("/json/list", echange -> {
                byte[] corps = cibles.getBytes(StandardCharsets.UTF_8);
                echange.sendResponseHeaders(200, corps.length);
                echange.getResponseBody().write(corps);
                echange.close();
            });
            devTools.start();
            try {
                verifierQueLeConstructeurEchoueSansBloquer(devTools.getAddress().getPort());
            } finally {
                devTools.stop(0);
            }
        }
    }

    @Test
    void laFermetureAttendLesEnfantsDeChromeEtLeProfilPeutEtreSupprime() throws IOException {
        Path binaire = dossier.resolve("faux-chrome");
        Files.writeString(binaire, FAUX_CHROME_AVEC_ENFANT.formatted(portFerme()));
        Files.setPosixFilePermissions(binaire, PosixFilePermissions.fromString("rwx------"));
        Path profil = Files.createDirectory(dossier.resolve("profil"));

        // La connexion échoue : le constructeur ferme la session, avec l'enfant encore en train d'écrire.
        assertThatThrownBy(() -> new ChromeSession(binaire.toString(), profil)).isInstanceOf(ConnectException.class);

        long pid = Long.parseLong(Files.readString(profil.resolve("pid")).strip());
        Optional<ProcessHandle> enfant = ProcessHandle.of(pid).filter(ProcessHandle::isAlive);
        enfant.ifPresent(ProcessHandle::destroyForcibly);
        assertThat(enfant).isEmpty();
        try (Stream<Path> chemins = Files.walk(profil)) {
            for (Path chemin : chemins.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(chemin);
            }
        }
        assertThat(profil).doesNotExist();
    }
}
