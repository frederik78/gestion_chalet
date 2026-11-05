'use strict';

function lireCookie(nom) {
  const prefixe = nom + '=';
  const cookie = document.cookie.split('; ').find((valeur) => valeur.startsWith(prefixe));
  return cookie ? cookie.substring(prefixe.length) : '';
}

/** Appel en écriture de l'API : recopie le cookie XSRF-TOKEN dans l'en-tête attendu par le serveur. */
function ecrireApi(chemin, corps) {
  return fetch(chemin, {
    method: 'POST',
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': lireCookie('XSRF-TOKEN') },
    body: corps === undefined ? '' : JSON.stringify(corps),
  });
}

/** Envoi multipart : le navigateur pose lui-même le type de contenu et sa limite. */
function envoyerFormulaire(chemin, formulaire) {
  return fetch(chemin, {
    method: 'POST',
    credentials: 'same-origin',
    headers: { 'X-XSRF-TOKEN': lireCookie('XSRF-TOKEN') },
    body: formulaire,
  });
}
