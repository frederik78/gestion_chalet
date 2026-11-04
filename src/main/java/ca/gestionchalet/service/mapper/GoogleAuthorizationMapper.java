package ca.gestionchalet.service.mapper;

import ca.gestionchalet.domain.EtatAutorisationGoogle;
import ca.gestionchalet.service.dto.GoogleAuthorizationStateDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface GoogleAuthorizationMapper {

    @Mapping(target = "etat", source = "etat")
    GoogleAuthorizationStateDTO toDto(EtatAutorisationGoogle etat);
}
