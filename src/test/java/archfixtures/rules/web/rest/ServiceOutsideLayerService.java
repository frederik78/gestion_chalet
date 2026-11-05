package archfixtures.rules.web.rest;

import org.springframework.stereotype.Service;

/** Violation : un @Service réside dans service/. */
@Service
public class ServiceOutsideLayerService {
}
