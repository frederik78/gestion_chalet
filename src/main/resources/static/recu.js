'use strict';

// Prise de vue, puis validation. Les photos et la saisie ne vivent que dans cette page : rien n'existe sur le serveur avant la
// validation, l'abandon n'a donc rien à nettoyer (PRD, section 4.1). Tout texte est affiché par textContent, jamais comme du HTML.

const APERCU_COTE_MAX = 320;
const DELAI_CONTROLE_MS = 300;
const CONTROLE_INDISPONIBLE = "Les alertes n'ont pas pu être vérifiées. Vous pouvez tout de même valider.";
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

// Le serveur fait foi sur les photos : il lit leur contenu, que le contrôle de cet écran ne voit pas.
const REFUS_DE_PHOTO = {
  FORMAT_NON_ACCEPTE: "Une photo n'est pas une image JPEG ou PNG.",
  TROP_DE_PHOTOS: 'Ce reçu compte trop de photos.',
  TAILLE_EXCESSIVE: 'Les photos sont trop lourdes.',
  IMAGE_ILLISIBLE: 'Une photo est illisible.',
  IMAGE_TROP_GRANDE: "Les dimensions d'une photo sont trop grandes.",
  PHOTO_MANQUANTE: 'Au moins une photo est requise.',
};

const elements = (() => {
  const parId = {};
  for (const id of ['prise-de-vue', 'validation', 'photographier', 'importer', 'photos-choisies', 'photos-a-valider', 'continuer',
    'message-depart', 'message-succes', 'message-validation', 'message-lecture', 'relancer-lecture', 'retour-photos', 'formulaire-recu',
    'valider', 'abandonner', 'typeDepense', 'alertes']) {
    parId[id] = document.getElementById(id);
  }
  return parId;
})();

let limites = { photosParRecuMax: 1, tailleParPhotoMaxOctets: 0, formats: [] };
let photos = [];
// La même clé pour tous les envois d'un même reçu : un renvoi après une panne ne crée pas de doublon (R-11).
let cleIdempotence = null;
// Seule la réponse au dernier contrôle demandé s'affiche : une réponse en retard décrirait des valeurs déjà changées.
let dernierControle = 0;
let minuterieControle = null;
// Une lecture dont la réponse arrive après un abandon ou un retour aux photos est ignorée.
let generationLecture = 0;
let lectureFaite = false;
let montantTotalLuCents = null;
// Les photos sur lesquelles porte la lecture ; null dès qu'elles ont changé, jusqu'à une relance.
let photosLues = null;

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
  elements['message-validation'].textContent = '';
  const apercus = await Promise.all(photos.map((photo, indice) => creerApercu(photo, 'Photo ' + (indice + 1))));
  elements['photos-a-valider'].replaceChildren(...apercus);
  elements['prise-de-vue'].hidden = true;
  elements.validation.hidden = false;
}

const MESSAGE_ECHEC_LECTURE = 'La lecture a échoué. Saisissez les valeurs ou relancez la lecture.';
const MESSAGE_ILLISIBLE = "Ce reçu n'a pas pu être lu. Saisissez les valeurs ou relancez la lecture.";
const MESSAGE_LIMITE_LECTURES = 'La limite de lectures est atteinte. Saisissez les valeurs ou relancez plus tard.';
const MESSAGE_PHOTOS_CHANGEES = 'Les photos ont changé : les valeurs affichées viennent des précédentes. Vérifiez-les ou relancez la lecture.';
const MESSAGE_SESSION_EXPIREE = 'Votre session a expiré. Reconnectez-vous dans un autre onglet, puis relancez : vos photos sont conservées.';

/** Rend le corps d'une lecture, ou le refus à afficher : une photo refusée se retire, une session expirée se rouvre, le reste mène à la saisie manuelle. */
async function demanderLecture() {
  const formulaire = new FormData();
  photos.forEach((photo) => formulaire.append('photos', photo));
  try {
    const reponse = await envoyerFormulaire('/api/lectures', formulaire);
    const corps = await reponse.json().catch(() => null);
    if (reponse.ok && corps) {
      return { lecture: corps };
    }
    if (reponse.status === 401) {
      return { refus: MESSAGE_SESSION_EXPIREE };
    }
    if (corps && REFUS_DE_PHOTO[corps.code]) {
      return { refus: REFUS_DE_PHOTO[corps.code] };
    }
    return { empechee: reponse.status === 429 ? MESSAGE_LIMITE_LECTURES : MESSAGE_ECHEC_LECTURE };
  } catch {
    return { empechee: MESSAGE_ECHEC_LECTURE };
  }
}

/** Un montant en cents entiers, sans flottant ; 0 reste 0,00 et null reste vide (R-08). */
function centsVersTexte(cents) {
  return Math.trunc(cents / 100) + ',' + String(cents % 100).padStart(2, '0');
}

/** Seul un champ encore vide est rempli : une relance n'efface jamais ce que l'utilisateur a saisi. */
function preremplir(valeurs) {
  const remplir = (id, valeur) => {
    const champ = document.getElementById(id);
    if (valeur !== null && valeur !== undefined && champ.value === '') {
      champ.value = valeur;
    }
  };
  const montant = (cents) => (cents === null || cents === undefined ? null : centsVersTexte(cents));
  remplir('date', valeurs.date);
  remplir('fournisseur', valeurs.fournisseur);
  remplir('objet', valeurs.objet);
  remplir('typeDepense', valeurs.typeDepense);
  remplir('montantAvantTaxes', montant(valeurs.montantAvantTaxesCents));
  remplir('tps', montant(valeurs.tpsCents));
  remplir('tvq', montant(valeurs.tvqCents));
  remplir('montantTotal', montant(valeurs.montantTotalCents));
  remplir('numeroTps', valeurs.numeroInscriptionTps);
  remplir('numeroTvq', valeurs.numeroInscriptionTvq);
}

function afficherLecture({ lecture, empechee }) {
  const lue = lecture && lecture.resultat === 'LU';
  if (lue) {
    preremplir(lecture.valeurs);
    montantTotalLuCents = lecture.valeurs.montantTotalCents;
    elements['message-lecture'].textContent = lecture.champsRejetes.length === 0 ? ''
      : 'À vérifier, valeur non retenue : ' + lecture.champsRejetes.map((champ) => CHAMPS[champ] || champ).join(', ') + '.';
  } else if (lecture) {
    elements['message-lecture'].textContent = lecture.resultat === 'ILLISIBLE' ? MESSAGE_ILLISIBLE : MESSAGE_ECHEC_LECTURE;
  } else {
    elements['message-lecture'].textContent = empechee;
  }
  elements['relancer-lecture'].hidden = lue;
  lectureFaite = true;
  photosLues = [...photos];
}

/** Le montant lu ne vaut que pour les photos lues (R-13) ; la saisie reste, et rien n'est relu sans le demander. */
function invaliderLectureSiPhotosChangees() {
  const inchangees = photosLues && photosLues.length === photos.length && photos.every((photo, indice) => photo === photosLues[indice]);
  if (!inchangees) {
    photosLues = null;
    montantTotalLuCents = null;
    elements['message-lecture'].textContent = MESSAGE_PHOTOS_CHANGEES;
    elements['relancer-lecture'].hidden = false;
  }
}

/** Après un retour aux photos, la saisie reste telle quelle : pas de nouvelle lecture sans le demander. */
async function continuer() {
  cleIdempotence = cleIdempotence || crypto.randomUUID();
  if (lectureFaite) {
    invaliderLectureSiPhotosChangees();
    await montrerValidation();
    return;
  }
  const generation = ++generationLecture;
  const depart = elements['prise-de-vue'];
  elements['message-depart'].textContent = '';
  elements['message-succes'].textContent = 'Lecture en cours…';
  elements.continuer.disabled = true;
  depart.inert = true;
  const resultat = await demanderLecture();
  depart.inert = false;
  elements.continuer.disabled = photos.length === 0;
  elements['message-succes'].textContent = '';
  if (generation !== generationLecture) {
    return;
  }
  if (resultat.refus) {
    elements['message-depart'].textContent = resultat.refus;
    return;
  }
  afficherLecture(resultat);
  await montrerValidation();
}

async function relancerLecture() {
  const generation = ++generationLecture;
  elements['relancer-lecture'].disabled = true;
  elements.valider.disabled = true;
  elements['message-lecture'].textContent = 'Lecture en cours…';
  const resultat = await demanderLecture();
  elements['relancer-lecture'].disabled = false;
  elements.valider.disabled = false;
  if (generation !== generationLecture) {
    return;
  }
  if (resultat.refus) {
    elements['message-lecture'].textContent = resultat.refus;
    return;
  }
  afficherLecture(resultat);
}

/** D-41 : retirer ou ajouter une photo sans perdre la saisie ni la clé d'idempotence. */
function revenirAuxPhotos() {
  generationLecture += 1;
  elements['message-validation'].textContent = '';
  elements.validation.hidden = true;
  elements['prise-de-vue'].hidden = false;
  afficherPhotosChoisies();
}

function revenirAuDepart() {
  generationLecture += 1;
  lectureFaite = false;
  montantTotalLuCents = null;
  photosLues = null;
  elements['message-lecture'].textContent = '';
  elements['relancer-lecture'].hidden = true;
  photos = [];
  cleIdempotence = null;
  oublierLesControles();
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
  if (corps && REFUS_DE_PHOTO[corps.code]) {
    return REFUS_DE_PHOTO[corps.code] + " Rien n'a été enregistré.";
  }
  if (corps && corps.code === 'VALIDATION' && corps.champs) {
    return 'À corriger : ' + corps.champs.map((erreur) => CHAMPS[erreur.champ] || erreur.champ).join(', ') + '.';
  }
  if (statut >= 500) {
    return "L'enregistrement a échoué. Rien n'est perdu ici : validez à nouveau.";
  }
  return (corps && corps.erreur) || "L'enregistrement a été refusé.";
}

function afficherAlertes(textes) {
  elements.alertes.replaceChildren(...textes.map((texte) => {
    const alerte = document.createElement('li');
    alerte.textContent = texte;
    return alerte;
  }));
}

function oublierLesControles() {
  clearTimeout(minuterieControle);
  dernierControle += 1;
  afficherAlertes([]);
}

/** Les alertes viennent du serveur et n'empêchent jamais de valider. Tant que la saisie est incomplète ou invalide, il n'y a rien à contrôler. */
async function controler(numero) {
  const { valeurs, invalide } = lireValeurs();
  if (invalide || !valeurs.date || !valeurs.fournisseur || !valeurs.typeDepense || !valeurs.montantTotalCents) {
    afficherAlertes([]);
    return;
  }
  let textes;
  try {
    const reponse = await ecrireApi('/api/recus/controles', { valeurs });
    if (reponse.ok) {
      textes = (await reponse.json()).alertes.map((alerte) => alerte.message);
    } else {
      textes = reponse.status === 400 ? [] : [CONTROLE_INDISPONIBLE];
    }
  } catch {
    textes = [CONTROLE_INDISPONIBLE];
  }
  if (numero === dernierControle) {
    afficherAlertes(textes);
  }
}

function planifierControle() {
  clearTimeout(minuterieControle);
  dernierControle += 1;
  const numero = dernierControle;
  minuterieControle = setTimeout(() => controler(numero), DELAI_CONTROLE_MS);
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
  formulaire.append('recu', new Blob([JSON.stringify({ cleIdempotence, valeurs, montantTotalLuCents })], { type: 'application/json' }));
  photos.forEach((photo) => formulaire.append('photos', photo));

  message.textContent = '';
  elements.valider.disabled = true;
  // L'envoi ne s'annule pas : le serveur créerait un reçu indélébile (R-35) après un abandon accepté par l'écran.
  elements.abandonner.disabled = true;
  elements['retour-photos'].disabled = true;
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
    elements['retour-photos'].disabled = false;
  }
}

elements.continuer.addEventListener('click', continuer);
elements['relancer-lecture'].addEventListener('click', relancerLecture);
elements['retour-photos'].addEventListener('click', revenirAuxPhotos);
elements.abandonner.addEventListener('click', revenirAuDepart);
elements['formulaire-recu'].addEventListener('submit', valider);
elements['formulaire-recu'].addEventListener('input', planifierControle);
chargerConfiguration();
