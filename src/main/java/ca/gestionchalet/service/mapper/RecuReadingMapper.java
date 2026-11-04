package ca.gestionchalet.service.mapper;

import ca.gestionchalet.domain.RecuReading;
import ca.gestionchalet.service.dto.RecuReadingDTO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RecuReadingMapper {

    RecuReadingDTO toDto(RecuReading reading);
}
