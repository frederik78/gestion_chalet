package ca.gestionchalet.service.mapper;

import ca.gestionchalet.domain.RecuRules;
import ca.gestionchalet.service.dto.ConfigurationDTO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/** Deux listes de textes voisines : la projection par nom évite de les inverser. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ConfigurationMapper {

    ConfigurationDTO toDto(RecuRules rules);
}
