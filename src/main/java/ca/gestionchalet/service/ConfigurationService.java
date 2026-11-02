package ca.gestionchalet.service;

import ca.gestionchalet.domain.RecuRules;
import ca.gestionchalet.service.dto.ConfigurationDTO;
import org.springframework.stereotype.Service;

/** Expose aux écrans la part de la configuration qui les concerne. */
@Service
public class ConfigurationService {

    private final RecuRules rules;

    public ConfigurationService(RecuRules rules) {
        this.rules = rules;
    }

    public ConfigurationDTO current() {
        return new ConfigurationDTO(rules.typesDepense(), rules.photosParRecuMax(), rules.tailleParPhotoMaxOctets(), rules.formats());
    }
}
