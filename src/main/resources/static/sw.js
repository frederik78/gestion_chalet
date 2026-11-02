'use strict';

// Pas de mode hors ligne : ce service worker n'intercepte aucune requête et ne met rien en cache.
// Il retire tout cache laissé par une version antérieure, sans jamais en créer.
self.addEventListener('activate', (evenement) => {
  evenement.waitUntil(caches.keys().then((noms) => Promise.all(noms.map((nom) => caches.delete(nom)))));
});
