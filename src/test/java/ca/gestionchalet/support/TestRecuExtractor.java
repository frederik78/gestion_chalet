package ca.gestionchalet.support;

import ca.gestionchalet.client.RecuExtractionException;
import ca.gestionchalet.client.RecuExtractor;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Double du modèle : renvoie les champs indiqués par le test et retient chaque appel. Par défaut, aucune valeur. */
public class TestRecuExtractor implements RecuExtractor {

    public record Call(List<byte[]> images, List<String> expenseTypes) {
    }

    public final List<Call> calls = new CopyOnWriteArrayList<>();
    private volatile Map<String, Object> fields = Map.of();
    private volatile boolean failing;

    public void answer(Map<String, Object> newFields) {
        fields = newFields;
        failing = false;
    }

    public void fail() {
        failing = true;
    }

    public void reset() {
        calls.clear();
        answer(Map.of());
    }

    @Override
    public Map<String, Object> extract(List<byte[]> images, List<String> expenseTypes) {
        calls.add(new Call(images, expenseTypes));
        if (failing) {
            throw new RecuExtractionException("panne d'essai");
        }
        return fields;
    }
}
