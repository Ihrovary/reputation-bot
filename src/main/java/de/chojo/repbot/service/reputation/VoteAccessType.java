/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.repbot.service.reputation;

public enum VoteAccessType {
    NONE,
    BOTH,
    UPVOTE_ONLY,
    DOWNVOTE_ONLY;

    public boolean allows(VoteType voteType) {
        if (this == BOTH) return true;
        if (this == NONE) return false;
        if (this == UPVOTE_ONLY) return voteType == VoteType.UPVOTE;
        if (this == DOWNVOTE_ONLY) return voteType == VoteType.DOWNVOTE;
        return false;
    }
}
