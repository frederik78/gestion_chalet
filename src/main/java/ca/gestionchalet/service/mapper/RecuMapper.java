package ca.gestionchalet.service.mapper;

import ca.gestionchalet.domain.Alerte;
import ca.gestionchalet.domain.Photo;
import ca.gestionchalet.domain.Recu;
import ca.gestionchalet.domain.Valeurs;
import ca.gestionchalet.service.dto.RecuDTO;
import ca.gestionchalet.service.dto.ValeursDTO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/** Les champs ignorés ne seront renseignés qu'avec les issues qui les calculent : le lien Drive. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RecuMapper {

    RecuDTO toDto(Recu recu);

    @Mapping(target = "lienDrive", ignore = true)
    RecuDTO.PhotoDTO toDto(Photo photo);

    List<RecuDTO.AlerteDTO> toAlerteDtos(List<Alerte> alertes);

    Valeurs toValeurs(ValeursDTO valeurs);
}
