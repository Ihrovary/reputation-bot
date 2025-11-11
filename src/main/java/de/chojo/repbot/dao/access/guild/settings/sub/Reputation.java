/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.repbot.dao.access.guild.settings.sub;

import de.chojo.repbot.dao.access.guild.settings.Settings;
import de.chojo.repbot.dao.components.GuildHolder;
import de.chojo.repbot.service.reputation.VoteType;
import de.chojo.sadu.mapper.wrapper.Row;
import de.chojo.sadu.queries.api.call.Call;
import net.dv8tion.jda.api.entities.Guild;
import org.jetbrains.annotations.PropertyKey;

import java.sql.SQLException;
import java.util.List;
import java.util.function.Function;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

public class Reputation implements GuildHolder {
    private final Settings settings;
    private boolean reactionActive;
    private boolean answerActive;
    private boolean mentionActive;
    private boolean fuzzyActive;
    private boolean embedActive;
    private boolean directActive;
    private int upvoteAmount;
    private int downvoteAmount;

    public Reputation(Settings settings) {
        this(settings, true, true, true, true, true, false, 1, -1);
    }

    public Reputation(Settings settings, boolean reactionActive, boolean answerActive, boolean mentionActive, boolean fuzzyActive, boolean embedActive, boolean directActive, int upvoteAmount, int downvoteAmount) {
        this.settings = settings;
        this.reactionActive = reactionActive;
        this.answerActive = answerActive;
        this.mentionActive = mentionActive;
        this.fuzzyActive = fuzzyActive;
        this.embedActive = embedActive;
        this.directActive = directActive;
        this.upvoteAmount = upvoteAmount;
        this.downvoteAmount = downvoteAmount;
    }

    public static Reputation build(Settings settings, Row rs) throws SQLException {
        return new Reputation(settings,
                rs.getBoolean("reactions_active"),
                rs.getBoolean("answer_active"),
                rs.getBoolean("mention_active"),
                rs.getBoolean("fuzzy_active"),
                rs.getBoolean("embed_active"),
                rs.getBoolean("skip_single_embed"),
                rs.getInt("upvote_amount"),
                rs.getInt("downvote_amount"));
    }

    public boolean isReactionActive() {
        return reactionActive;
    }

    public boolean isAnswerActive() {
        return answerActive;
    }

    public boolean isMentionActive() {
        return mentionActive;
    }

    public boolean isFuzzyActive() {
        return fuzzyActive;
    }

    public boolean isEmbedActive() {
        return embedActive;
    }

    public boolean isDirectActive() {
        return directActive;
    }

    public boolean embedActive(boolean embedActive) {
        var result = set("embed_active", stmt -> stmt.bind(embedActive));
        if (result) {
            this.embedActive = embedActive;
        }
        return this.embedActive;
    }

    public boolean reactionActive(boolean reactionActive) {
        var result = set("reactions_active", stmt -> stmt.bind(reactionActive));
        if (result) {
            this.reactionActive = reactionActive;
        }
        return this.reactionActive;
    }

    public boolean answerActive(boolean answerActive) {
        var result = set("answer_active", stmt -> stmt.bind(answerActive));
        if (result) {
            this.answerActive = answerActive;
        }
        return this.answerActive;
    }

    public boolean mentionActive(boolean mentionActive) {
        var result = set("mention_active", stmt -> stmt.bind(mentionActive));
        if (result) {
            this.mentionActive = mentionActive;
        }
        return this.mentionActive;
    }

    public boolean fuzzyActive(boolean fuzzyActive) {
        var result = set("fuzzy_active", stmt -> stmt.bind(fuzzyActive));
        if (result) {
            this.fuzzyActive = fuzzyActive;
        }
        return this.fuzzyActive;
    }

    public boolean directActive(boolean directActive) {
        var result = set("skip_single_embed", stmt -> stmt.bind(directActive));
        if (result) {
            this.directActive = directActive;
        }
        return this.directActive;
    }

    public int upvoteAmount(int amount) {
        var result = set("upvote_amount", stmt -> stmt.bind(amount));
        if (result) {
            this.upvoteAmount = amount;
        }
        return this.upvoteAmount;
    }

    public int downvoteAmount(int amount) {
        var result = set("downvote_amount", stmt -> stmt.bind(amount));
        if (result) {
            this.downvoteAmount = amount;
        }
        return this.downvoteAmount;
    }

    public int getAmount(VoteType type) {
        return switch (type) {
            case UPVOTE -> upvoteAmount;
            case DOWNVOTE -> downvoteAmount;
        };
    }

    public String toLocalizedString() {
        var setting = List.of(
                getSetting("command.repsettings.info.message.option.byreaction.name", isReactionActive()),
                getSetting("command.repsettings.info.message.option.byanswer.name", isAnswerActive()),
                getSetting("command.repsettings.info.message.option.bymention.name", isMentionActive()),
                getSetting("command.repsettings.info.message.option.byfuzzy.name", isFuzzyActive()),
                getSetting("command.repsettings.info.message.option.byembed.name", isEmbedActive()),
                getSetting("command.repsettings.info.message.option.skipsingletarget.name", settings.reputation().isDirectActive()),
                getSetting("command.repsettings.info.message.option.reputationmode.name", settings.general().reputationMode().localeCode()),
                getSetting("command.repsettings.info.message.option.upvoteAmount.name", upvoteAmount),
                getSetting("command.repsettings.info.message.option.downvoteAmount.name", downvoteAmount)
        );

        return String.join("\n", setting);
    }

    private String getSetting(@PropertyKey(resourceBundle = "locale") String locale, boolean object) {
        return getSetting(locale, object ? "words.enabled" : "words.disabled");
    }

    private String getSetting(@PropertyKey(resourceBundle = "locale") String locale, String object) {
        return String.format("$%s$: $%s$", locale, object);
    }

    private String getSetting(@PropertyKey(resourceBundle = "locale") String locale, int object) {
        return String.format("$%s$: %d", locale, object);
    }

    @Override
    public Guild guild() {
        return settings.guild();
    }

    @Override
    public long guildId() {
        return settings.guildId();
    }

    private boolean set(String parameter, Function<Call, Call> builder) {
        return query("""
                INSERT INTO reputation_settings(guild_id, %s) VALUES (?, ?)
                ON CONFLICT(guild_id) DO UPDATE SET %s = excluded.%s;
                """, parameter, parameter, parameter)
                .single(builder.apply(call().bind(guildId())))
                .insert()
                .changed();
    }

    public String prettyString() {
        return """
                Reaction active: %s
                Answer active: %s
                Mention active: %s
                Fuzzy active: %s
                Embed active: %s
                Skip single embed: %s
                """.formatted(reactionActive, answerActive, mentionActive, fuzzyActive, embedActive, directActive)
                   .stripIndent();
    }
}
