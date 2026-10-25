package com.maximegrau.brin.model.video;

/** Catégorie culturelle attribuée par l'IA. */
public enum Category {
    SCIENCES("Sciences"),
    HISTOIRE("Histoire"),
    ECONOMIE("Économie"),
    LANGUES("Langues"),
    GEOGRAPHIE("Géographie"),
    ARTS("Arts"),
    TECHNOLOGIE("Technologie"),
    SOCIETE("Société");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
