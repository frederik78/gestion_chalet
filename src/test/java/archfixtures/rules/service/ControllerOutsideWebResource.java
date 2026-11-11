package archfixtures.rules.service;

import org.springframework.web.bind.annotation.RestController;

/** Violation : une ressource REST réside dans web/rest. */
@RestController
public class ControllerOutsideWebResource {
}
