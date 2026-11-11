package ca.gestionchalet.web.rest;

import ca.gestionchalet.service.ConfigurationService;
import ca.gestionchalet.service.dto.ConfigurationDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/configuration")
public class ConfigurationResource {

    private final ConfigurationService configurationService;

    public ConfigurationResource(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @GetMapping
    public ConfigurationDTO configuration() {
        return configurationService.current();
    }
}
