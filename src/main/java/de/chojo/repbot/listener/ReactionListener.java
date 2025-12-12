/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.repbot.listener;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import de.chojo.jdautil.localization.ILocalizer;
import de.chojo.jdautil.localization.util.LocaleProvider;
import de.chojo.jdautil.localization.util.Replacement;
import de.chojo.repbot.analyzer.results.match.ThankType;
import de.chojo.repbot.config.Configuration;
import de.chojo.repbot.dao.access.guild.settings.sub.thanking.ReactionCheckResult;
import de.chojo.repbot.dao.provider.GuildRepository;
import de.chojo.repbot.dao.snapshots.ReputationLogEntry;
import de.chojo.repbot.service.reputation.VoteType;
import de.chojo.repbot.service.reputation.ReputationService;
import de.chojo.repbot.service.reputation.SubmitResultContext;
import de.chojo.repbot.service.reputation.SubmitResultMessage;
import de.chojo.repbot.service.reputation.SubmitResultType;
import de.chojo.repbot.util.PermissionErrorHandler;
import de.chojo.repbot.util.SubmitResultNotifier;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import java.awt.Color;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.react.GenericMessageReactionEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveAllEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEmojiEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.slf4j.LoggerFactory.getLogger;

public class ReactionListener extends ListenerAdapter {
    private static final int REACTION_COOLDOWN = 30;
    private static final Logger log = getLogger(ReactionListener.class);
    private final GuildRepository guildRepository;
    private final ILocalizer localizer;
    private final ReputationService reputationService;
    private final Configuration configuration;
    private final Cache<Long, Instant> lastReaction = CacheBuilder.newBuilder().expireAfterAccess(60, TimeUnit.SECONDS)
                                                                  .build();

    public ReactionListener(GuildRepository guildRepository, ILocalizer localizer, ReputationService reputationService, Configuration configuration) {
        this.guildRepository = guildRepository;
        this.localizer = localizer;
        this.reputationService = reputationService;
        this.configuration = configuration;
    }

    @Override
    public void onMessageReactionAdd(@NotNull MessageReactionAddEvent event) {
        if (event.getUser().isBot() || !event.isFromGuild()) return;
        var guild = event.getGuild();
        var repGuild = guildRepository.guild(guild);
        var guildSettings = repGuild.settings();

        if (!guildSettings.thanking().channels().isEnabled(event.getGuildChannel())) return;
        if (!guildSettings.reputation().isReactionActive()) return;

        var reactionCheck = guildSettings.thanking().reactions().checkReaction(event.getReaction());
        if (reactionCheck == ReactionCheckResult.NOT_RELEVANT) return;

        Message message;
        try {
            message = event.getChannel()
                           .retrieveMessageById(event.getMessageId())
                           .timeout(10, TimeUnit.SECONDS)
                           .onErrorMap(err -> null)
                           .complete();
        } catch (InsufficientPermissionException e) {
            PermissionErrorHandler.handle(e, guild, localizer.context(LocaleProvider.guild(guild)), configuration);
            return;
        }

        if (message == null) return;

        var voteType = switch (reactionCheck) {
            case UPVOTE -> VoteType.UPVOTE;
            case DOWNVOTE -> VoteType.DOWNVOTE;
            default -> throw new IllegalArgumentException("Unexpected value: " + reactionCheck);
        };

        var receiver = event.getGuild().retrieveMember(message.getAuthor()).complete();
        SubmitResultContext context = new SubmitResultContext(event.getGuild(), event.getMember(), receiver, message, ThankType.REACTION, voteType);

        if (isCooldown(event.getMember())) {
            var resultMessage = localizer.localize(SubmitResultType.COOLDOWN_ABUSE.localeKey(), guild);
            var result = SubmitResultMessage.Fail(context, resultMessage);
            SubmitResultNotifier.notifyResult(guildSettings, localizer, result);
            removeReactionIfPossible(event);
            return;
        }

        var logEntry = repGuild.reputation().log().getLogEntries(message);
        if (!logEntry.isEmpty()) {
            // If an entry is already present, the target is actually not the message author but the one who received the reputation.
            // This is important if people react to the reputation emoji. In that case they agree with the reputation and second it.
            Member newReceiver;
            try {
                newReceiver = event.getGuild().retrieveMemberById(logEntry.get(0).receiverId()).complete();
            } catch (RuntimeException e) {
                return;
            }
            // If the correct receiver could not be determined, we stop, bcs why should we care.
            if (newReceiver == null) return;
            receiver = newReceiver;
        }

        if (PermissionErrorHandler.assertAndHandle(event.getGuildChannel(), localizer.context(LocaleProvider.guild(event.getGuild())), configuration, Permission.MESSAGE_SEND)) {
            return;
        }

        SubmitResultMessage result = reputationService.submitReputation(context, null);

        if (result.isSuccess()) {
            reacted(event.getMember());
        } else {
            removeReactionIfPossible(event);
        }

        if (guildSettings.messages().isReactionConfirmation()) {
            SubmitResultNotifier.notifyResult(guildSettings, localizer, result);
        }
    }

    @Override
    public void onMessageReactionRemoveEmoji(@NotNull MessageReactionRemoveEmojiEvent event) {
        if (!event.isFromGuild()) return;
        var guildSettings = guildRepository.guild(event.getGuild()).settings();

        var reactionCheck = guildSettings.thanking().reactions().checkReaction(event.getReaction());
        if (reactionCheck == ReactionCheckResult.NOT_RELEVANT) return;
        
        List<ReputationLogEntry> entries = guildRepository.guild(event.getGuild()).reputation().log().messageLog(event.getMessageIdLong(), 50).stream()
                                                          .filter(entry -> entry.type() == ThankType.REACTION)
                                                          .toList();
        reputationService.delete(entries, event.getGuildChannel(), event.getGuild());
    }

    @Override
    public void onMessageReactionRemove(@NotNull MessageReactionRemoveEvent event) {
        if (!event.isFromGuild()) return;

        var guild = event.getGuild();
        var guildSettings = guildRepository.guild(guild).settings();

        var reactionCheck = guildSettings.thanking().reactions().checkReaction(event.getReaction());
        if (reactionCheck == ReactionCheckResult.NOT_RELEVANT) return;
        
        var entries = guildRepository.guild(guild).reputation().log().messageLog(event.getMessageIdLong(), 50)
                                     .stream()
                                     .filter(entry -> entry.type() == ThankType.REACTION && entry.donorId() == event.getUserIdLong())
                                     .toList();
                                     
        if (!entries.isEmpty() && guildSettings.messages().isReactionConfirmation()) {
            reputationService.delete(entries, event.getGuildChannel(), guild);

            var resultMessage = localizer.localize("listener.reaction.removal", guild,
                         Replacement.create("DONOR", User.fromId(event.getUserId()).getAsMention()));

            SubmitResultNotifier.sendSubmitRemovalMessageToChannel(event, resultMessage);
        }
    }

    @Override
    public void onMessageReactionRemoveAll(@NotNull MessageReactionRemoveAllEvent event) {
        List<ReputationLogEntry> entries = guildRepository.guild(event.getGuild()).reputation().log().messageLog(event.getMessageIdLong(), 50).stream()
                                                          .filter(entry -> entry.type() == ThankType.REACTION)
                                                          .toList();
        reputationService.delete(entries, event.getGuildChannel(), event.getGuild());
    }

    public boolean isCooldown(Member member) {
        try {
            return lastReaction.get(member.getIdLong(), () -> Instant.MIN)
                               .isAfter(Instant.now().minus(REACTION_COOLDOWN, ChronoUnit.SECONDS));
        } catch (ExecutionException e) {
            log.error("Could not compute instant", e);
        }
        return true;
    }

    public void reacted(Member member) {
        lastReaction.put(member.getIdLong(), Instant.now());
    }

    private void removeReactionIfPossible(MessageReactionAddEvent event) {
        try {
            // Ensure we have permission to manage messages in the channel
            if (!event.getGuild().getSelfMember().hasPermission(event.getGuildChannel(), Permission.MESSAGE_MANAGE)) return;
            var user = event.getUser();
            if (user == null) return;
            event.getReaction().removeReaction(user).queue(RestAction.getDefaultSuccess(), ErrorResponseException.ignore(ErrorResponse.UNKNOWN_MESSAGE));
        } catch (InsufficientPermissionException ignored) {
            // Can't remove reaction due to missing permissions - nothing we can do
        }
    }
}
