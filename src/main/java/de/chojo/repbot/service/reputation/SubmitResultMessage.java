/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.repbot.service.reputation;

public record SubmitResultMessage(boolean isSuccess, String message) {

    public static SubmitResultMessage Fail() {
        return new SubmitResultMessage(false, "");
    }

    public static SubmitResultMessage Success() {
        return new SubmitResultMessage(true, "");
    }

    public static SubmitResultMessage Fail(String message) {
        return new SubmitResultMessage(false, message);
    }

    public static SubmitResultMessage Success(String message) {
        return new SubmitResultMessage(true, message);
    }
}