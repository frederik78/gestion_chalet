package ca.gestionchalet.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Le pilote des tests d'écran (CA76, CA79, CA81), avec un faux navigateur : aucun Chrome requis. */
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

    @TempDir
    Path dossier;

    private static int portFerme() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    void ca76_chromeEstArreteQuandLaConnexionEchoueDansLeConstructeur() throws IOException {
        Path binaire = dossier.resolve("faux-chrome");
        Files.writeString(binaire, FAUX_CHROME.formatted(portFerme()));
        Files.setPosixFilePermissions(binaire, PosixFilePermissions.fromString("rwx------"));
        Path profil = Files.createDirectory(dossier.resolve("profil"));

        assertThatThrownBy(() -> new ChromeSession(binaire.toString(), profil)).isInstanceOf(ConnectException.class);

        long pid = Long.parseLong(Files.readString(profil.resolve("pid")).strip());
        Optional<ProcessHandle> processus = ProcessHandle.of(pid).filter(ProcessHandle::isAlive);
        processus.ifPresent(ProcessHandle::destroyForcibly);
        assertThat(processus).isEmpty();
    }
}
