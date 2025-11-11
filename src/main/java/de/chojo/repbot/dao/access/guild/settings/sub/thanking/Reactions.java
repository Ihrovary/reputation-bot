/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.repbot.dao.access.guild.settings.sub.thanking;

import de.chojo.jdautil.parsing.Verifier;
import de.chojo.repbot.dao.access.guild.settings.sub.Thanking;
import de.chojo.repbot.dao.components.GuildHolder;
import de.chojo.repbot.service.reputation.VoteType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji;

import java.util.List;
import java.util.Objects;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

public class Reactions implements GuildHolder {
    private final Thanking thanking;
    private final Set<String> reactions;
    private final Set<String> negativeReactions;
    private String mainReaction;

    public Reactions(Thanking thanking, String mainReaction, Set<String> positiveReactions, Set<String> negativeReactions) {
        this.thanking = thanking;
        this.mainReaction = mainReaction;
        this.reactions = positiveReactions;
        this.negativeReactions = negativeReactions;
    }

    @Override
    public Guild guild() {
        return thanking.guild();
    }

    @Override
    public long guildId() {
        return thanking.guildId();
    }

    public ReactionCheckResult checkReaction(MessageReaction reaction) {
        if (reaction.getEmoji() instanceof UnicodeEmoji emoji) {
            return checkReaction(emoji.getAsReactionCode());
        }
        if (reaction.getEmoji() instanceof CustomEmoji emoji) {
            return checkReaction(emoji.getId());
        }
        return ReactionCheckResult.NOT_RELEVANT;
    }

    private ReactionCheckResult checkReaction(String reaction) {
        if (reactions.contains(reaction)) {
            return ReactionCheckResult.POSITIVE;    
        } else if (negativeReactions.contains(reaction)) {
            return ReactionCheckResult.NEGATIVE;
        } else {
            return ReactionCheckResult.NOT_RELEVANT;
        }
    }

    public boolean reactionIsEmote() {
        return Verifier.isValidId(mainReaction());
    }

    public Optional<String> reactionMention() {
        if (!reactionIsEmote()) {
            return Optional.ofNullable(mainReaction());
        }
        return Optional.of(guild().retrieveEmojiById(mainReaction()).onErrorMap(err -> null).complete())
                       .map(CustomEmoji::getAsMention);
    }

    public String mainReaction() {
        return mainReaction;
    }

    public List<String> getAdditionalReactionMentions() {
        return reactions.stream()
                        .map(reaction -> {
                            if (Verifier.isValidId(reaction)) {
                                var asMention = guild().retrieveEmojiById(reaction).onErrorMap(err -> null)
                                                       .complete();
                                return asMention == null ? null : asMention.getAsMention();
                            }
                            return reaction;
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
    }

    public boolean add(String reaction, VoteType type) {
        var result = query("""
                INSERT INTO guild_reactions(guild_id, reaction, reaction_type) VALUES (?,?,?)
                    ON CONFLICT(guild_id, reaction)
                        DO NOTHING;
                """)
                .single(call().bind(guildId()).bind(reaction).bind(type.name()))
                .update()
                .changed();
        if (result) {
            if (type == VoteType.UPVOTE) {
                reactions.add(reaction);
            } else {
                negativeReactions.add(reaction);
            }
        }
        return result;
    }

    public boolean remove(String reaction) {
        var result = query("""
                DELETE FROM guild_reactions WHERE guild_id = ? AND reaction = ?;
                """)
                .single(call().bind(guildId()).bind(reaction))
                .update()
                .changed();
        if (result) {
            if (negativeReactions.contains(reaction)) {
                negativeReactions.remove(reaction);
            }
            else{
                reactions.remove(reaction);
            }
        }

        return result;
    }

    public boolean mainReaction(String reaction) {
        reaction = Objects.requireNonNullElse(reaction, Thanking.DEFAULT_REACTION);
        var result = query("""
                INSERT INTO thank_settings(guild_id, reaction) VALUES (?,?)
                    ON CONFLICT(guild_id)
                        DO UPDATE
                            SET reaction = excluded.reaction
                """)
                .single(call().bind(guildId()).bind(reaction))
                .update()
                .changed();
        if (result) {
            mainReaction = reaction;
        }
        return result;
    }

    public Set<String> allReactions() {
        var combined = new HashSet<>(reactions);
        combined.addAll(negativeReactions);
        return combined;
    }
}
