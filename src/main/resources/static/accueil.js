'use strict';

document.getElementById('deconnexion').addEventListener('click', async () => {
  await ecrireApi('/api/deconnexion');
  window.location.assign('/');
});
