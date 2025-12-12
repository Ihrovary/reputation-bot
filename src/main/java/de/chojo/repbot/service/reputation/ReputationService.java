/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.repbot.service.reputation;

import de.chojo.jdautil.localization.ILocalizer;
import de.chojo.jdautil.localization.LocalizationContext;
import de.chojo.jdautil.localization.util.LocaleProvider;
import de.chojo.jdautil.localization.util.Replacement;
import de.chojo.jdautil.parsing.Verifier;
import de.chojo.jdautil.util.Premium;
import de.chojo.repbot.analyzer.ContextResolver;
import de.chojo.repbot.analyzer.MessageContext;
import de.chojo.repbot.analyzer.results.match.ThankType;
import de.chojo.repbot.commands.log.handler.LogFormatter;
import de.chojo.repbot.config.Configuration;
import de.chojo.repbot.config.elements.MagicImage;
import de.chojo.repbot.dao.access.guild.settings.Settings;
import de.chojo.repbot.dao.access.guild.settings.sub.Reputation;
import de.chojo.repbot.dao.provider.GuildRepository;
import de.chojo.repbot.dao.snapshots.ReputationLogEntry;
import de.chojo.repbot.service.RoleAssigner;
import de.chojo.repbot.util.Messages;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.awt.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;

public class ReputationService {
    private static final Logger log = getLogger(ReputationService.class);
    private final GuildRepository guildRepository;
    private final RoleAssigner assigner;
    private final Configuration configuration;
    private final ContextResolver contextResolver;
    private final ILocalizer localizer;
    private Instant lastEasterEggSent = Instant.EPOCH;

    public ReputationService(GuildRepository guildRepository, ContextResolver contextResolver, RoleAssigner assigner, Configuration configuration, ILocalizer localizer) {
        this.guildRepository = guildRepository;
        this.assigner = assigner;
        this.configuration = configuration;
        this.contextResolver = contextResolver;
        this.localizer = localizer;
    }

    /**
     * Submit a reputation.
     * <p>
     * This reputation will be checked by several factors based on the {@link de.chojo.repbot.dao.access.guild.settings.Settings}.
     *
     * @param guild      guild where the vote was given
     * @param donor      donor of the reputation
     * @param receiver   receiver of the reputation
     * @param message    triggered message
     * @param refMessage reference message if present
     * @param type       type of reputation source
     * @return true if the reputation was counted and is valid
     */
    public SubmitResultMessage submitReputation(SubmitResultContext context, @Nullable Message refMessage) {
        log.trace("Submitting reputation for message {} of type {}", context.eventMessage().getIdLong(), context.type());

        var repGuild = guildRepository.guild(context.guild());
        SubmitResultMessage failResult = SubmitResultMessage.Fail(context);

        if (context.receiver() == null) {
            return failResult;
        }
        // block bots
        if (context.receiver().getUser().isBot()) {
            log.trace("Author of {} is bot.", context.eventMessage().getIdLong());
            return failResult;
        }

        var settings = repGuild.settings();
        var messageSettings = settings.reputation();
        var thankSettings = settings.thanking();
        var analyzer = repGuild.reputation().analyzer();

        analyzer.log(context.eventMessage(), SubmitResult.of(SubmitResultType.SUBMITTING,
                Replacement.create("type", "$%s$".formatted(context.type().nameLocaleKey())),
                Replacement.createMention(context.donor())));

        // block non reputation channel
        if (!thankSettings.channels().isEnabled(context.eventMessage().getGuildChannel())) {
            analyzer.log(context.eventMessage(), SubmitResult.of(SubmitResultType.CHANNEL_INACTIVE));
            log.trace("Channel of message {} is not enabled", context.eventMessage().getIdLong());
            return failResult;
        }

        if (isTypeDisabled(context.type(), messageSettings)) {
            analyzer.log(context.eventMessage(), SubmitResult.of(SubmitResultType.THANK_TYPE_DISABLED, Replacement.create("thanktype", "$%s$".formatted(context.type().nameLocaleKey()))));
            log.trace("Thank type {} for message {} is disabled", context.type(), context.eventMessage().getIdLong());
            return failResult;
        }

        var messageContext = getContext(context.donor(), context.eventMessage(), context.type(), settings);

        if (isSelfVote(context.donor(), context.receiver(), context.eventMessage())) {
            analyzer.log(context.eventMessage(), SubmitResult.of(SubmitResultType.SELF_VOTE));
            log.trace("Detected self vote on {}", context.eventMessage().getIdLong());
            var failMessage = localizer.localize(SubmitResultType.SELF_VOTE.localeKey(), context.guild());
            return SubmitResultMessage.Fail(context, failMessage);
        }

        var abuseCheck = assertAbuseProtection(context, refMessage, messageContext);
        if (!abuseCheck.isSuccess()) {
            return abuseCheck;
        }

        var amount = settings.reputation().getAmount(context.voteType());
        boolean success = log(context.guild(), context.donor(), context.receiver(), context.eventMessage(), refMessage, context.type(), amount, settings);

        if (success) {
            var successMessage = localizer.localize("listener.reaction.confirmation", context.guild(),
                Replacement.createMention("DONOR", context.donor()),
                Replacement.createMention("RECEIVER", context.receiver()),
                Replacement.create("VOTETYPE", context.voteType().localeKey()));
            return SubmitResultMessage.Success(context, successMessage);
        }

        return failResult;
    }

    public void deleteBulk(List<Long> messages, GuildMessageChannelUnion channel, Guild guild) {
        var reputationLog = guildRepository.guild(guild).reputation().log();
        List<ReputationLogEntry> entries = messages.stream()
                                                   .map(reputationLog::getLogEntries)
                                                   .flatMap(Collection::stream)
                                                   .toList();
        delete(entries, channel, guild);
    }

    public void delete(long messageIdLong, GuildMessageChannelUnion channel, Guild guild) {
        List<ReputationLogEntry> logEntries = guildRepository.guild(guild).reputation().log().getLogEntries(messageIdLong);
        delete(logEntries, channel, guild);
    }

    public void delete(List<ReputationLogEntry> entries, GuildMessageChannelUnion channel, Guild guild) {
        if (entries.isEmpty()) return;
        entries.forEach(ReputationLogEntry::deleteAll);
        LocalizationContext context = localizer.context(LocaleProvider.guild(guild));
        String deleted = entries.stream().map(e -> LogFormatter.formatMessageLogEntrySimple(context, e)).collect(Collectors.joining("\n"));
        if (entries.size() > 1) {
            String title = localizer.localize("listener.reputation.log.bulkdelete", guild, Replacement.create("CHANNEL", channel.getAsMention()));
            deleted = title + "\n" + deleted;
        } else {
            deleted = LogFormatter.formatMessageLogEntrySimple(context, entries.get(0)) + " **|** " + channel.getAsMention();
        }

        logToChannel(guildRepository.guild(guild).settings(), ":red_circle: " + deleted);

    }

    private MessageContext getContext(Member donor, Message message, ThankType type, Settings settings) {
        MessageContext context;
        if (type == ThankType.REACTION) {
            // Check if user was recently seen in this channel.
            context = contextResolver.getCombinedContext(donor, message, settings);
        } else {
            context = contextResolver.getCombinedContext(message, settings);
        }
        return context;
    }

    private SubmitResultMessage assertAbuseProtection(SubmitResultContext context, @Nullable Message refMessage, MessageContext messageContext) {
        var repGuild = guildRepository.guild(context.guild());
        var analyzer = repGuild.reputation().analyzer();
        var settings = repGuild.settings();
        var abuseSettings = settings.abuseProtection();

        // Abuse Protection: target context
        if (!messageContext.members().contains(context.receiver()) && abuseSettings.isReceiverContext()) {
            log.trace("Receiver is not in context of {}", messageContext.message().getIdLong());
            analyzer.log(messageContext.message(), SubmitResult.of(SubmitResultType.TARGET_NOT_IN_CONTEXT, Replacement.createMention(context.receiver())));
            var failMessage = localizer.localize(SubmitResultType.TARGET_NOT_IN_CONTEXT.localeKey(), context.guild(), Replacement.createMention(context.receiver()));
            return SubmitResultMessage.Fail(context, failMessage);
        }

        // Abuse Protection: donor context
        if (!messageContext.members().contains(context.donor()) && abuseSettings.isDonorContext()) {
            log.trace("Donor is not in context of {}", messageContext.message().getIdLong());
            analyzer.log(messageContext.message(), SubmitResult.of(SubmitResultType.DONOR_NOT_IN_CONTEXT, Replacement.createMention(context.donor())));
            var failMessage = localizer.localize(SubmitResultType.DONOR_NOT_IN_CONTEXT.localeKey(), context.guild(), Replacement.createMention(context.donor()));
            return SubmitResultMessage.Fail(context, failMessage);
        }

        // Abuse protection: Cooldown and Role Access
        var canGiveReputationResult = canGiveReputation(context, settings);
        if (!canGiveReputationResult.isSuccess()) {
            log.trace("Cooldown active on {}", context.eventMessage().getIdLong());
            return canGiveReputationResult;
        }

        // block outdated ref message
        // Abuse protection: Message age
        if (refMessage != null) {
            if (abuseSettings.isOldMessage(refMessage) && !messageContext.latestMessages(abuseSettings.minMessages()).contains(refMessage)) {
                log.trace("Reference message of {} is outdated", context.eventMessage().getIdLong());
                analyzer.log(context.eventMessage(), SubmitResult.of(SubmitResultType.OUTDATED_REFERENCE_MESSAGE));
                var failMessage = localizer.localize(SubmitResultType.OUTDATED_REFERENCE_MESSAGE.localeKey(), context.guild());
                return SubmitResultMessage.Fail(context, failMessage);
            }
        }

        // block outdated message
        // Abuse protection: Message age
        if (abuseSettings.isOldMessage(context.eventMessage())) {
            log.trace("Message of {} is outdated", context.eventMessage().getIdLong());
            analyzer.log(context.eventMessage(), SubmitResult.of(SubmitResultType.OUTDATED_MESSAGE));
            var failMessage = localizer.localize(SubmitResultType.OUTDATED_MESSAGE.localeKey(), context.guild());
            return SubmitResultMessage.Fail(context, failMessage);
        }

        if (abuseSettings.isReceiverLimit(context.receiver(), context.voteType())) {
            log.trace("Receiver limit is reached on {}", context.eventMessage().getIdLong());
            analyzer.log(context.eventMessage(), SubmitResult.of(SubmitResultType.RECEIVER_LIMIT));
            var failMessage = localizer.localize(SubmitResultType.RECEIVER_LIMIT.localeKey(), context.guild());
            return SubmitResultMessage.Fail(context, failMessage);
        }

        if (abuseSettings.isDonorLimit(context.donor())) {
            log.trace("Donor limit is reached on {}", context.eventMessage().getIdLong());
            analyzer.log(context.eventMessage(), SubmitResult.of(SubmitResultType.DONOR_LIMIT));
            var failMessage = localizer.localize(SubmitResultType.DONOR_LIMIT.localeKey(), context.guild());
            return SubmitResultMessage.Fail(context, failMessage);
        }

        return SubmitResultMessage.Success(context);
    }

    private boolean isSelfVote(Member donor, Member receiver, Message message) {
        // block self vote
        if (Verifier.equalSnowflake(receiver, donor)) {
            MagicImage magicImage = configuration.magicImage();
            if (lastEasterEggSent.until(Instant.now(), ChronoUnit.MINUTES) > magicImage.magicImageCooldown()
                    && ThreadLocalRandom.current().nextInt(magicImage.magicImagineChance()) == 0) {
                lastEasterEggSent = Instant.now();
                //TODO: Escape unknown channel 5
                message.replyEmbeds(new EmbedBuilder()
                               .setImage(magicImage.magicImageLink())
                               .setColor(Color.RED).build())
                       .queue(msg -> msg.delete().queueAfter(
                               magicImage.magicImageDeleteSchedule(), TimeUnit.SECONDS,
                               RestAction.getDefaultSuccess(),
                               ErrorResponseException.ignore(
                                       ErrorResponse.UNKNOWN_MESSAGE,
                                       ErrorResponse.UNKNOWN_CHANNEL,
                                       ErrorResponse.ILLEGAL_OPERATION_ARCHIVED_THREAD))
                       );
            }
            return true;
        }
        return false;
    }

    private boolean log(Guild guild, Member donor, Member receiver, Message message, @Nullable Message refMessage, ThankType type, int amount, Settings settings) {
        var repGuild = guildRepository.guild(guild);
        // try to log a reputation
        if (!repGuild.reputation().user(receiver)
                     .addReputation(donor, message, refMessage, type, amount)) {// submit to database failed. Maybe this message was already voted by the user.
            repGuild.reputation().analyzer().log(message, SubmitResult.of(SubmitResultType.ALREADY_PRESENT));
            log.trace("Could not log reputation for message {}. An equal entry was already present.", message.getIdLong());
            return false;
        }

        logReputationEntry(settings, guild, new ReputationLogEntry(
                guild.getIdLong(),
                message.getChannel().getIdLong(),
                donor.getIdLong(),
                receiver.getIdLong(),
                message.getIdLong(),
                refMessage == null ? 0 : refMessage.getIdLong(),
                type,
                Instant.now()));

        // update role
        var newRank = assigner.updateReporting(receiver, message.getGuildChannel());

        // Send a level-up message
        newRank.ifPresent(rank -> {
            var announcements = repGuild.settings().announcements();
            if (!announcements.isActive()) return;
            var channel = message.getChannel().asGuildMessageChannel();
            if (!announcements.isSameChannel()) {
                channel = guild.getTextChannelById(announcements.channelId());
            }
            if (channel == null || rank.getRole(guild).isEmpty()) return;
            channel.sendMessage(localizer.localize("message.levelAnnouncement", guild,
                           Replacement.createMention(receiver), Replacement.createMention(rank.role().get())))
                   .setAllowedMentions(Collections.emptyList())
                   .complete();
        });
        return true;
    }

    private void logReputationEntry(Settings settings, Guild guild, ReputationLogEntry reputationLogEntry) {
        String message = LogFormatter.formatMessageLogEntry(localizer.context(LocaleProvider.guild(guild)), reputationLogEntry);
        logToChannel(settings, ":green_circle: " + message);
    }

    private void logToChannel(Settings settings, String string) {
        if (!settings.repGuild().settings().logChannel().active()) return;

        TextChannel textChannelById = settings.guild().getTextChannelById(settings.repGuild().settings().logChannel().channelId());
        if (textChannelById == null) return;

        textChannelById.sendMessage(string).setAllowedMentions(Collections.emptyList()).complete();
    }

    private boolean isTypeDisabled(ThankType type, Reputation reputation) {
        // force settings
        switch (type) {
            case FUZZY -> {
                if (!reputation.isFuzzyActive()) return true;
            }
            case MENTION -> {
                if (!reputation.isMentionActive()) return true;
            }
            case ANSWER -> {
                if (!reputation.isAnswerActive()) return true;
            }
            case REACTION -> {
                if (!reputation.isReactionActive()) return true;
            }
            case EMBED -> {
                if (!reputation.isEmbedActive()) return true;
            }
            case DIRECT -> {
                if (!reputation.isDirectActive()) return true;
            }
            default -> throw new IllegalStateException("Unexpected value: " + type);
        }
        return false;
    }

    public SubmitResultMessage canGiveReputation(SubmitResultContext context, Settings settings) {
        var repGuild = settings.repGuild();
        var analyzer = repGuild.reputation().analyzer();
        // block cooldown
        var optRating = guildRepository.guild(context.guild()).reputation().user(context.donor()).getLastReputation(context.receiver());

        if (optRating.isPresent()) {
            var lastRating = optRating.get();

            if (settings.abuseProtection().cooldown() < 0) {
                analyzer.log(context.eventMessage(), SubmitResult.of(SubmitResultType.COOLDOWN_ONCE));
                var failMessage = localizer.localize(SubmitResultType.COOLDOWN_ONCE.localeKey(), context.guild());
                return SubmitResultMessage.Fail(context, failMessage);
            }

            if (lastRating.tillNow().toMinutes() < settings.abuseProtection().cooldown()) {
                analyzer.log(context.eventMessage(), SubmitResult.of(SubmitResultType.COOLDOWN_ACTIVE,
                        Replacement.create("TARGET", "$words.message$"),
                        Replacement.create("URL", lastRating.getMessageJumpLink()),
                        Replacement.create("ENTRY", lastRating.simpleString()),
                        Replacement.create("TIMESTAMP", lastRating.timestamp()),
                        Replacement.create("REMAINING", lastRating.tillNow().toMinutes()),
                        Replacement.create("TOTAL", settings.abuseProtection().cooldown())));
                log.trace("The last rating is too recent. {}/{}", lastRating.tillNow().toMinutes(),
                        settings.abuseProtection().cooldown());

                var resultMessage = localizer.localize(SubmitResultType.COOLDOWN_RECENT_ACTIVE.localeKey(), context.guild(), 
                    Replacement.create("REMAINING", settings.abuseProtection().cooldown() - lastRating.tillNow().toMinutes()));

                return SubmitResultMessage.Fail(context, resultMessage);
            }
        }

        var hasRoleAccessResult = hasRoleAccess(context, settings);
        if (!hasRoleAccessResult.isSuccess()) {
            return hasRoleAccessResult;
        }

        return SubmitResultMessage.Success(context);
    }

    
    private SubmitResultMessage hasRoleAccess(SubmitResultContext context, Settings settings) {
        var repGuild = settings.repGuild();
        var analyzer = repGuild.reputation().analyzer();

        if (!settings.thanking().receiverRoles().hasRole(context.receiver())) {
            analyzer.log(context.eventMessage(), SubmitResult.of(SubmitResultType.NO_RECEIVER_ROLE, Replacement.createMention(context.receiver())));
            log.trace("The receiver does not have a receiver role.");
            var failMessage = localizer.localize(SubmitResultType.NO_RECEIVER_ROLE.localeKey(), context.guild(), Replacement.createMention(context.receiver()));
            return SubmitResultMessage.Fail(context, failMessage);
        }

        var donorRoleAccess = settings.thanking().donorRoles().hasRole(context.donor(), context.voteType());
        
        switch (donorRoleAccess) {
            case VoteAccessType.BOTH: {
                return SubmitResultMessage.Success(context);
            }
            case VoteAccessType.NONE: {
                analyzer.log(context.eventMessage(), SubmitResult.of(SubmitResultType.NO_DONOR_VOTE_ACCESS, Replacement.createMention(context.donor())));
                log.trace("The donor does not have permission to upvote or downvote.");
                var failMessage = localizer.localize(SubmitResultType.NO_DONOR_VOTE_ACCESS.localeKey(), context.guild(), Replacement.createMention(context.donor()));
                return SubmitResultMessage.Fail(context, failMessage);
            }
            case VoteAccessType.UPVOTE_ONLY: {
                analyzer.log(context.eventMessage(), SubmitResult.of(SubmitResultType.NO_DONOR_DOWNVOTE_ACCESS, Replacement.createMention(context.donor())));
                log.trace("The donor does not have permission to upvote.");
                var failMessage = localizer.localize(SubmitResultType.NO_DONOR_DOWNVOTE_ACCESS.localeKey(), context.guild(), Replacement.createMention(context.donor()));
                return SubmitResultMessage.Fail(context, failMessage);
            }
            case VoteAccessType.DOWNVOTE_ONLY: {
                analyzer.log(context.eventMessage(), SubmitResult.of(SubmitResultType.NO_DONOR_UPVOTE_ACCESS, Replacement.createMention(context.donor())));
                log.trace("The donor does not have permission to downvote.");
                var failMessage = localizer.localize(SubmitResultType.NO_DONOR_UPVOTE_ACCESS.localeKey(), context.guild(), Replacement.createMention(context.donor()));
                return SubmitResultMessage.Fail(context, failMessage);
            }
        }

        return SubmitResultMessage.Success(context);
    }
}
