/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.repbot.util;

import de.chojo.jdautil.localization.ILocalizer;
import de.chojo.jdautil.localization.util.Replacement;
import de.chojo.repbot.dao.access.guild.settings.Settings;
import de.chojo.repbot.service.reputation.SubmitResultMessage;
import de.chojo.repbot.service.reputation.VoteType;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.internal.utils.PermissionUtil;

import org.slf4j.Logger;

import java.awt.Color;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.slf4j.LoggerFactory.getLogger;

public final class SubmitResultNotifier {
    private static final int MESSAGE_VIEW_SECONDS = 180;
    private static final int REACTION_VIEW_SECONDS = 10;
    private static final Logger log = getLogger(SubmitResultNotifier.class);
    private static final String SUBMIT_SUCCESS_REACTION = "🏅";
    private static final String SUBMIT_FAIL_REACTION = "❌";

    private SubmitResultNotifier() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    public static void notifyResult(Settings settings, ILocalizer localizer, SubmitResultMessage result) {
        addSubmitResultReaction(result.context().eventMessage(), result.isSuccess());
        sendSubmitResultToChannel(settings, localizer, result);
    }

    public static void addSubmitResultReaction(Message message, boolean success) {
        if (noAddReactionPermission(message)) {
            log.debug("Missing permission to add reaction on message {}", message.getIdLong());
            return;
        }

        if (success) {
            Emoji emoji = Emoji.fromUnicode(SUBMIT_SUCCESS_REACTION);
            message.addReaction(emoji).queue(RestAction.getDefaultSuccess(), err -> {
                log.debug("Failed to set submit result reaction: {}", err.getMessage());
            });
        }
        else {
            Emoji emoji = Emoji.fromUnicode(SUBMIT_FAIL_REACTION);
            message.addReaction(emoji).queue(RestAction.getDefaultSuccess(), err -> {
                log.debug("Failed to set submit result reaction: {}", err.getMessage());
            });
            message.removeReaction(emoji).queueAfter(REACTION_VIEW_SECONDS, TimeUnit.SECONDS, RestAction.getDefaultSuccess(), err -> {
                log.debug("Failed to remove submit result reaction: {}", err.getMessage());
            });
        }
    }

    public static void sendSubmitResultToChannel(Settings settings, ILocalizer localizer, SubmitResultMessage result) {
        long systemChannelId = settings.general().systemChannel();
        var guild = settings.guild();
        var guildId = settings.guild().getIdLong();

        if (systemChannelId == 0) {
            log.trace("No system channel configured for guild {}", guildId);
            return;
        }

        TextChannel botChannel = settings.guild().getTextChannelById(systemChannelId);
        if (botChannel == null) {
            log.debug("System channel {} not found in guild {}", systemChannelId, guildId);
            return;
        }

        if (noSendEmbendPermission(settings, botChannel)) {
            log.debug("Missing permissions to send message in system channel {} of guild {}", systemChannelId, guildId);
            return;
        }

        var context = result.context();
        boolean success = result.isSuccess();
        Message eventMessage = context.eventMessage();
        Member donor = context.donor();
        Member receiver = context.receiver();
        var messageFieldName = localizer.localize("words.message", guild);

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(success ? Color.GREEN : Color.RED)
                .setTimestamp(Instant.now());

        if (success) {
            var title = localizer.localize("reputation.confirmation.title.success", guild);
            var description = localizer.localize("listener.reaction.confirmation", guild,
                Replacement.createMention("DONOR", donor),
                Replacement.createMention("RECEIVER", receiver),
                Replacement.create("VOTETYPE", context.voteType().localeKey()));

            embed.setTitle(String.format("%s %s", SUBMIT_SUCCESS_REACTION, title));
            embed.setDescription(description);

            if (eventMessage != null) {
                embed.addField(messageFieldName, eventMessage.getJumpUrl(), false);
            }

        } else {
            
            var title = localizer.localize("reputation.confirmation.title.fail", guild);
            embed.setTitle(String.format("%s %s", SUBMIT_FAIL_REACTION, title));

            String errorMessage = result.resultMessage();
            if (errorMessage != null && !errorMessage.isEmpty()) {
                embed.setDescription(String.format("%s. %s", donor.getAsMention(), errorMessage));
            }

            if (eventMessage != null) {
                embed.addField(messageFieldName, eventMessage.getJumpUrl(), false);
            }
        }

        botChannel.sendMessageEmbeds(embed.build())
            .delay(MESSAGE_VIEW_SECONDS, TimeUnit.SECONDS)
            .flatMap(Message::delete)
            .queue(RestAction.getDefaultSuccess(), ErrorResponseException.ignore(ErrorResponse.UNKNOWN_MESSAGE));
    }

    public static void sendSubmitRemovalMessageToChannel(MessageReactionRemoveEvent event, String resultMessage) {

    }

    private static boolean noSendEmbendPermission(Settings settings, TextChannel botChannel) {
        return !PermissionUtil.checkPermission(
                botChannel.getPermissionContainer(),
                settings.guild().getSelfMember(),
                Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS);
    }

    private static boolean noAddReactionPermission(Message message) {
        return !PermissionUtil.checkPermission(
                message.getGuildChannel().getPermissionContainer(),
                message.getGuild().getSelfMember(),
                Permission.MESSAGE_ADD_REACTION);
    }
}
