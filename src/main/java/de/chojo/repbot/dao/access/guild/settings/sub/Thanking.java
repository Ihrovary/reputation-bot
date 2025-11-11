/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.repbot.dao.access.guild.settings.sub;

import de.chojo.repbot.dao.access.guild.settings.Settings;
import de.chojo.repbot.dao.access.guild.settings.sub.thanking.Channels;
import de.chojo.repbot.dao.access.guild.settings.sub.thanking.DonorRoles;
import de.chojo.repbot.dao.access.guild.settings.sub.thanking.Reactions;
import de.chojo.repbot.dao.access.guild.settings.sub.thanking.ReceiverRoles;
import de.chojo.repbot.dao.access.guild.settings.sub.thanking.Thankwords;
import de.chojo.repbot.service.reputation.VoteAccessType;
import de.chojo.repbot.service.reputation.VoteType;
import de.chojo.repbot.dao.components.GuildHolder;
import de.chojo.sadu.mapper.wrapper.Row;
import net.dv8tion.jda.api.entities.Guild;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

public class Thanking  implements GuildHolder {
    public static final String DEFAULT_REACTION = "🏅";
    private final String mainReaction;
    private final Settings settings;
    private final boolean channelWhitelist;

    private Channels channels;
    private DonorRoles donorRoles;
    private ReceiverRoles receiverRoles;
    private Reactions reactions;
    private Thankwords thankwords;

    public Thanking(Settings settings) {
        this(settings, DEFAULT_REACTION, true);
    }

    public Thanking(Settings settings, String mainReaction, boolean channelWhitelist) {
        this.settings = settings;
        this.mainReaction = mainReaction;
        this.channelWhitelist = channelWhitelist;
    }

    public static Thanking build(Settings settings, Row row) throws SQLException {
        return new Thanking(settings,
                row.getString("reaction"),
                row.getBoolean("channel_whitelist")
        );
    }

    public Channels channels() {
        if (channels != null) {
            return channels;
        }
        var channels = query("""
                       SELECT channel_id
                       FROM active_channel
                       WHERE guild_id = ?
                       """)
                .single(call().bind(guildId()))
                .map(r -> r.getLong("channel_id"))
                .all();
        var categories = query("""
                       SELECT category_id
                       FROM active_categories
                       WHERE guild_id = ?
                       """)
                .single(call().bind(guildId()))
                .mapAs(Long.class)
                .all();
        this.channels = new Channels(this, channelWhitelist, new HashSet<>(channels), new HashSet<>(categories));
        return this.channels;
    }

    public DonorRoles donorRoles() {
        if (donorRoles != null) {
            return donorRoles;
        }
        var rows = query("""
                       SELECT role_id, vote_access_type
                       FROM donor_roles
                       WHERE guild_id = ?
                       """)
                .single(call().bind(guildId()))
                .map(r -> java.util.Map.entry(r.getLong("role_id"), r.getString("vote_access_type")))
                .all();

        var accessRoles = new HashMap<Long, de.chojo.repbot.service.reputation.VoteAccessType>();
        for (var e : rows) {
            if (e.getValue() != null) {
                try {
                    accessRoles.put(e.getKey(), de.chojo.repbot.service.reputation.VoteAccessType.valueOf(e.getValue()));
                } catch (IllegalArgumentException ex) {
                    // ignore unknown values and fall back to default (BOTH)
                }
            } else {
                // default to BOTH if DB value missing
                accessRoles.put(e.getKey(), de.chojo.repbot.service.reputation.VoteAccessType.BOTH);
            }
        }

        donorRoles = new DonorRoles(this, accessRoles);
        return donorRoles;
    }

    public ReceiverRoles receiverRoles() {
        if (receiverRoles != null) {
            return receiverRoles;
        }
        var rows = query("""
                       SELECT role_id, vote_access_type
                       FROM receiver_roles
                       WHERE guild_id = ?
                       """)
                .single(call().bind(guildId()))
                .map(r -> java.util.Map.entry(r.getLong("role_id"), r.getString("vote_access_type")))
                .all();

        var accessRoles = new HashMap<Long, VoteAccessType>();
        for (var e : rows) {
            if (e.getValue() != null) {
                try {
                    accessRoles.put(e.getKey(), VoteAccessType.valueOf(e.getValue()));
                } catch (IllegalArgumentException ex) {
                    // ignore unknown values and fall back to default (BOTH)
                }
            } else {
                accessRoles.put(e.getKey(), VoteAccessType.BOTH); // default to BOTH if DB value missing
            }
        }
        
        receiverRoles = new ReceiverRoles(this, accessRoles);
        return receiverRoles;
    }

    public Reactions reactions() {
        if (reactions != null) {
            return reactions;
        }
        
        var upvoteReactions = query("""
                       SELECT reaction
                       FROM guild_reactions
                       WHERE guild_id = ? AND reaction_type = ?
                       """)
                .single(call().bind(guildId()).bind(VoteType.UPVOTE.name()))
                .mapAs(String.class)
                .all();

        var downvoteReactions = query("""
                       SELECT reaction
                       FROM guild_reactions
                       WHERE guild_id = ? AND reaction_type = ?
                       """)
                .single(call().bind(guildId()).bind(VoteType.DOWNVOTE.name()))
                .mapAs(String.class)
                .all();

        this.reactions = new Reactions(this, mainReaction, new HashSet<>(upvoteReactions), new HashSet<>(downvoteReactions));
        return this.reactions;
    }

    public Thankwords thankwords() {
        if (thankwords != null) {
            return thankwords;
        }
        var thankwords = query("""
                       SELECT thankword
                       FROM thankwords
                       WHERE guild_id = ?
                       """)
                .single(call().bind(guildId()))
                .mapAs(String.class)
                .all();

        this.thankwords = new Thankwords(this, new HashSet<>(thankwords));
        return this.thankwords;
    }

    public Settings settings() {
        return settings;
    }

    @Override
    public Guild guild() {
        return settings.guild();
    }

    @Override
    public long guildId() {
        return settings.guildId();
    }
}
