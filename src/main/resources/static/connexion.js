'use strict';

const MESSAGES = {
  401: 'Mot de passe incorrect.',
  403: 'La page a expiré. Rechargez-la, puis réessayez.',
  429: 'Trop de tentatives. Réessayez plus tard.',
};

if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('/sw.js');
}

// Une session encore valide mène directement à l'accueil.
fetch('/accueil.html', { method: 'HEAD', credentials: 'same-origin' }).then((reponse) => {
  if (reponse.ok) {
    window.location.replace('/accueil.html');
  }
});

document.getElementById('connexion').addEventListener('submit', async (evenement) => {
  evenement.preventDefault();
  const message = document.getElementById('message');
  const champ = document.getElementById('mot-de-passe');
  message.textContent = '';
  try {
    const reponse = await fetch('/api/connexion', {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'X-XSRF-TOKEN': lireCookie('XSRF-TOKEN') },
      body: new URLSearchParams({ motDePasse: champ.value }),
    });
    if (reponse.ok) {
      window.location.assign('/accueil.html');
      return;
    }
    message.textContent = MESSAGES[reponse.status] || 'La connexion a échoué.';
  } catch {
    message.textContent = 'Le serveur est injoignable.';
  }
  champ.value = '';
});
