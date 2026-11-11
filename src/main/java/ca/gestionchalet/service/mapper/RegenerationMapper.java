package ca.gestionchalet.service.mapper;

import ca.gestionchalet.domain.Regeneration;
import ca.gestionchalet.service.dto.RegenerationStateDTO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RegenerationMapper {

    RegenerationStateDTO toDto(Regeneration regeneration);
}
