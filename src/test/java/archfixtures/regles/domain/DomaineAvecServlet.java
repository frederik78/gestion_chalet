package archfixtures.regles.domain;

import jakarta.servlet.http.HttpServletRequest;

/** Violation : le domaine ne dépend pas de l'API Servlet. */
public class DomaineAvecServlet {
    HttpServletRequest requete;
}
