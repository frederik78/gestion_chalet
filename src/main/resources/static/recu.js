'use strict';

// Prise de vue, puis validation. Les photos et la saisie ne vivent que dans cette page : rien n'existe sur le serveur avant la
// validation, l'abandon n'a donc rien à nettoyer (PRD, section 4.1). Tout texte est affiché par textContent, jamais comme du HTML.

const APERCU_COTE_MAX = 320;
const MONTANT = /^(\d{1,7})(?:[.,](\d{1,2}))?$/;

const CHAMPS = {
  date: 'la date',
  fournisseur: 'le fournisseur',
  objet: "l'objet",
  typeDepense: 'le type de dépense',
  montantAvantTaxesCents: 'le montant avant taxes',
  tpsCents: 'la TPS',
  tvqCents: 'la TVQ',
  montantTotalCents: 'le montant total',
  numeroInscriptionTps: "le numéro d'inscription TPS",
  numeroInscriptionTvq: "le numéro d'inscription TVQ",
};

const elements = (() => {
  const parId = {};
  for (const id of ['prise-de-vue', 'validation', 'photographier', 'importer', 'photos-choisies', 'photos-a-valider', 'continuer',
    'message-depart', 'message-succes', 'message-validation', 'formulaire-recu', 'valider', 'abandonner', 'typeDepense']) {
    parId[id] = document.getElementById(id);
  }
  return parId;
})();

let limites = { photosParRecuMax: 1, tailleParPhotoMaxOctets: 0, formats: [] };
let photos = [];
// La même clé pour tous les envois d'un même reçu : un renvoi après une panne ne crée pas de doublon (R-11).
let cleIdempotence = null;

async function chargerConfiguration() {
  const reponse = await fetch('/api/configuration', { credentials: 'same-origin' });
  if (!reponse.ok) {
    elements['message-depart'].textContent = "La configuration n'a pas pu être chargée. Rechargez la page.";
    return;
  }
  limites = await reponse.json();
  for (const type of limites.typesDepense) {
    const option = document.createElement('option');
    option.value = type;
    option.textContent = type;
    elements.typeDepense.append(option);
  }
}

/** Une toile et non une image : la politique de sécurité du contenu n'autorise pas d'image blob:. */
async function creerApercu(fichier, libelle) {
  const figure = document.createElement('figure');
  const legende = document.createElement('figcaption');
  legende.textContent = libelle;
  try {
    const image = await createImageBitmap(fichier);
    const echelle = Math.min(1, APERCU_COTE_MAX / Math.max(image.width, image.height));
    const toile = document.createElement('canvas');
    toile.width = Math.round(image.width * echelle);
    toile.height = Math.round(image.height * echelle);
    toile.getContext('2d').drawImage(image, 0, 0, toile.width, toile.height);
    image.close();
    figure.append(toile);
  } catch {
    legende.textContent = libelle + ' (aperçu indisponible)';
  }
  figure.append(legende);
  return figure;
}

async function afficherPhotosChoisies() {
  const conteneur = elements['photos-choisies'];
  const apercus = await Promise.all(photos.map(async (photo, indice) => {
    const figure = await creerApercu(photo, 'Photo ' + (indice + 1));
    const retirer = document.createElement('button');
    retirer.type = 'button';
    retirer.className = 'secondaire';
    retirer.textContent = 'Retirer';
    retirer.addEventListener('click', () => {
      photos = photos.filter((autre) => autre !== photo);
      afficherPhotosChoisies();
    });
    figure.append(retirer);
    return figure;
  }));
  conteneur.replaceChildren(...apercus);
  elements.continuer.disabled = photos.length === 0;
}

function ajouterPhotos(fichiers) {
  elements['message-depart'].textContent = '';
  elements['message-succes'].textContent = '';
  for (const fichier of fichiers) {
    if (!limites.formats.includes(fichier.type)) {
      elements['message-depart'].textContent = 'Seules les photos JPEG et PNG sont acceptées.';
    } else if (fichier.size > limites.tailleParPhotoMaxOctets) {
      elements['message-depart'].textContent = 'Cette photo est trop lourde.';
    } else if (photos.length >= limites.photosParRecuMax) {
      elements['message-depart'].textContent = 'Un reçu compte au plus ' + limites.photosParRecuMax + ' photos.';
    } else {
      photos = [...photos, fichier];
    }
  }
  afficherPhotosChoisies();
}

for (const champ of [elements.photographier, elements.importer]) {
  champ.addEventListener('change', () => {
    ajouterPhotos(Array.from(champ.files));
    champ.value = '';
  });
}

async function montrerValidation() {
  cleIdempotence = crypto.randomUUID();
  elements['message-validation'].textContent = '';
  const apercus = await Promise.all(photos.map((photo, indice) => creerApercu(photo, 'Photo ' + (indice + 1))));
  elements['photos-a-valider'].replaceChildren(...apercus);
  elements['prise-de-vue'].hidden = true;
  elements.validation.hidden = false;
}

function revenirAuDepart() {
  photos = [];
  cleIdempotence = null;
  elements['formulaire-recu'].reset();
  elements['photos-choisies'].replaceChildren();
  elements['photos-a-valider'].replaceChildren();
  elements['message-depart'].textContent = '';
  elements['message-validation'].textContent = '';
  elements.continuer.disabled = true;
  elements.validation.hidden = true;
  elements['prise-de-vue'].hidden = false;
}

/** Un montant saisi en dollars, en cents entiers, sans passer par un flottant ; vide donne null, jamais 0 (R-08). */
function versCents(texte) {
  const nettoye = texte.replace(/\s/g, '');
  if (nettoye === '') {
    return null;
  }
  const morceaux = MONTANT.exec(nettoye);
  if (!morceaux) {
    return undefined;
  }
  return Number(morceaux[1]) * 100 + Number((morceaux[2] || '').padEnd(2, '0'));
}

function texteOuNull(id) {
  const texte = document.getElementById(id).value.trim();
  return texte === '' ? null : texte;
}

function lireValeurs() {
  const valeurs = {
    date: document.getElementById('date').value || null,
    fournisseur: texteOuNull('fournisseur'),
    objet: texteOuNull('objet'),
    typeDepense: elements.typeDepense.value || null,
    montantAvantTaxesCents: versCents(document.getElementById('montantAvantTaxes').value),
    tpsCents: versCents(document.getElementById('tps').value),
    tvqCents: versCents(document.getElementById('tvq').value),
    montantTotalCents: versCents(document.getElementById('montantTotal').value),
    numeroInscriptionTps: texteOuNull('numeroTps'),
    numeroInscriptionTvq: texteOuNull('numeroTvq'),
  };
  const invalide = Object.keys(valeurs).find((champ) => valeurs[champ] === undefined);
  return { valeurs, invalide };
}

function messageDeLErreur(statut, corps) {
  if (statut === 401) {
    return 'Votre session a expiré. Reconnectez-vous dans un autre onglet, puis validez à nouveau : la saisie et les photos sont conservées.';
  }
  if (corps && corps.code === 'CLE_DEJA_UTILISEE') {
    return "Ce reçu était déjà enregistré : votre modification n'a pas été appliquée. Abandonnez pour revenir au départ.";
  }
  if (corps && corps.code === 'VALIDATION' && corps.champs) {
    return 'À corriger : ' + corps.champs.map((erreur) => CHAMPS[erreur.champ] || erreur.champ).join(', ') + '.';
  }
  if (statut >= 500) {
    return "L'enregistrement a échoué. Rien n'est perdu ici : validez à nouveau.";
  }
  return (corps && corps.erreur) || "L'enregistrement a été refusé.";
}

async function valider(evenement) {
  evenement.preventDefault();
  const message = elements['message-validation'];
  const { valeurs, invalide } = lireValeurs();
  if (invalide) {
    message.textContent = 'À corriger : ' + CHAMPS[invalide] + '.';
    return;
  }
  const formulaire = new FormData();
  formulaire.append('recu', new Blob([JSON.stringify({ cleIdempotence, valeurs })], { type: 'application/json' }));
  photos.forEach((photo) => formulaire.append('photos', photo));

  message.textContent = '';
  elements.valider.disabled = true;
  // L'envoi ne s'annule pas : le serveur créerait un reçu indélébile (R-35) après un abandon accepté par l'écran.
  elements.abandonner.disabled = true;
  try {
    const reponse = await envoyerFormulaire('/api/recus', formulaire);
    if (reponse.ok) {
      revenirAuDepart();
      elements['message-succes'].textContent = 'Reçu enregistré.';
      return;
    }
    const corps = await reponse.json().catch(() => null);
    message.textContent = messageDeLErreur(reponse.status, corps);
  } catch {
    message.textContent = 'Le réseau est indisponible. Vos valeurs et vos photos sont conservées : validez à nouveau.';
  } finally {
    elements.valider.disabled = false;
    elements.abandonner.disabled = false;
  }
}

elements.continuer.addEventListener('click', montrerValidation);
elements.abandonner.addEventListener('click', revenirAuDepart);
elements['formulaire-recu'].addEventListener('submit', valider);
chargerConfiguration();
