package ca.gestionchalet.client;

import java.util.UUID;

/** Frontière de Google Drive (ADR 0001) : les photos d'un reçu, privées, dans le dossier que l'application y crée (R-28). */
public interface DriveClient {

    /**
     * Dépose une photo, sauf si un fichier portant son identifiant existe déjà : il est alors réutilisé (R-21). Aucun partage, aucun lien public (R-28).
     *
     * @return l'identifiant du fichier, tel que Drive l'a donné
     * @throws TransientGoogleException panne passagère : l'appelant réessaiera
     * @throws BlockedGoogleException refus qui attend une action de l'utilisateur
     */
    String deposit(DrivePhoto photo);

    /**
     * Une photo à déposer : son nom, généré par le serveur, n'est jamais celui du client.
     *
     * @param photoId identifiant de la photo, gardé dans une propriété invisible du fichier (R-21)
     * @param name nom du fichier dans Drive
     * @param mediaType type de média de la photo d'origine, conservée octet pour octet (R-36)
     */
    record DrivePhoto(UUID photoId, String name, String mediaType, byte[] content) {
    }
}
