package ca.gestionchalet.service;

import ca.gestionchalet.domain.RecuRules;
import ca.gestionchalet.service.dto.ConfigurationDTO;
import ca.gestionchalet.service.mapper.ConfigurationMapper;
import org.springframework.stereotype.Service;

/** Expose aux écrans la part de la configuration qui les concerne. */
@Service
public class ConfigurationService {

    private final RecuRules rules;
    private final ConfigurationMapper configurationMapper;

    public ConfigurationService(RecuRules rules, ConfigurationMapper configurationMapper) {
        this.rules = rules;
        this.configurationMapper = configurationMapper;
    }

    public ConfigurationDTO current() {
        return configurationMapper.toDto(rules);
    }
}
