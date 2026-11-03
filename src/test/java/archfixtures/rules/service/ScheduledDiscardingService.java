package archfixtures.rules.service;

import ca.gestionchalet.domain.Photo;
import ca.gestionchalet.repository.PhotoStorage;
import org.springframework.scheduling.annotation.Scheduled;

/** Violation volontaire : une tâche planifiée retire une photo. */
public class ScheduledDiscardingService {

    private PhotoStorage storage;
    private Photo photo;

    @Scheduled(fixedDelay = 1000)
    public void purge() {
        storage.discard(photo);
    }
}
