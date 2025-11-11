/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.repbot.service.reputation;

public enum VoteType {
    UPVOTE("vote.upvote"),
    DOWNVOTE("vote.downvote");

    private final String localeKey;

    VoteType(String localeKey) {
        this.localeKey = localeKey;
    }

    public String localeKey() {
        return localeKey;
    }
}
