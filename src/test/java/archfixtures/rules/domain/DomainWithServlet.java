package archfixtures.rules.domain;

import jakarta.servlet.http.HttpServletRequest;

/** Violation : le domaine ne dépend pas de l'API Servlet. */
public class DomainWithServlet {
    HttpServletRequest requete;
}
