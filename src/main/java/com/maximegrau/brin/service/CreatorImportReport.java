package com.maximegrau.brin.service;

import java.util.Map;

/**
 * Bilan de l'import d'un créateur.
 *
 * @param scanned      vidéos lues dans la playlist (nouvelles depuis le dernier import)
 * @param alreadyKnown déjà en base, ignorées
 * @param rejected     écartées par {@link VideoFilter}, par raison
 * @param inserted     insérées en PENDING (à classer par l'IA)
 * @param quotaUnits   unités de quota YouTube consommées
 */
public record CreatorImportReport(String handle, int scanned, int alreadyKnown,
                                  Map<RejectionReason, Long> rejected, int inserted, int quotaUnits) {
}
