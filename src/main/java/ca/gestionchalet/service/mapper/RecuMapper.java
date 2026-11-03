package ca.gestionchalet.service.mapper;

import ca.gestionchalet.domain.Photo;
import ca.gestionchalet.domain.Recu;
import ca.gestionchalet.domain.Valeurs;
import ca.gestionchalet.service.dto.RecuDTO;
import ca.gestionchalet.service.dto.ValeursDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/** Les champs ignorés ne seront renseignés qu'avec les issues qui les calculent : alertes, copie et correction. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RecuMapper {

    @Mapping(target = "alertes", expression = "java(java.util.List.of())")
    @Mapping(target = "motifBlocage", ignore = true)
    @Mapping(target = "prochaineTentative", ignore = true)
    @Mapping(target = "corrigeLe", ignore = true)
    RecuDTO toDto(Recu recu);

    @Mapping(target = "lienDrive", ignore = true)
    RecuDTO.PhotoDTO toDto(Photo photo);

    Valeurs toValeurs(ValeursDTO valeurs);
}
