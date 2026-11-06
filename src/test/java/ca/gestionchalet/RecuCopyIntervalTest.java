package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.RealServerTest;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.test.context.TestPropertySource;

/** D-60 : le passage périodique de la copie respecte l'intervalle configuré, lu tel que l'application le livre (valeur de l'application.yml, ici seulement la planification activée). */
@RealServerTest
@TestPropertySource(properties = {"recus.copie.automatique=true"})
class RecuCopyIntervalTest {

    @Autowired
    ScheduledAnnotationBeanPostProcessor planificateur;

    @Test
    void ca45_le_passage_periodique_attend_l_intervalle_configure() {
        List<FixedDelayTask> taches = planificateur.getScheduledTasks().stream().map(ScheduledTask::getTask)
                .filter(t -> t.getRunnable().toString().contains("processPending")).map(t -> (FixedDelayTask) t).toList();
        assertThat(taches).hasSize(1);
        assertThat(taches.get(0).getIntervalDuration()).isEqualTo(Duration.ofMinutes(1));
    }
}
