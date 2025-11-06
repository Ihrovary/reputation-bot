/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.repbot.service.reputation;

public enum KarmaType {
    POSITIVE,
    NEGATIVE;

    public String localCode() {
        return "$%s$".formatted(getClass().getSimpleName().toLowerCase() + "." + this.name().toLowerCase());
    }
}
