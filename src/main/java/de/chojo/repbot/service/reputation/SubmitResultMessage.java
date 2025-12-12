/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.repbot.service.reputation;

public record SubmitResultMessage(SubmitResultContext context, boolean isSuccess, VoteType voteType, String resultMessage) {

    public static SubmitResultMessage Fail(SubmitResultContext context) {
        return new SubmitResultMessage(context, false, context.voteType(), "");
    }

    public static SubmitResultMessage Success(SubmitResultContext context) {
        return new SubmitResultMessage(context, true, context.voteType(), "");
    }

    public static SubmitResultMessage Fail(SubmitResultContext context, String message) {
        return new SubmitResultMessage(context, false, context.voteType(), message);
    }

    public static SubmitResultMessage Success(SubmitResultContext context, String message) {
        return new SubmitResultMessage(context, true, context.voteType(), message);
    }
}