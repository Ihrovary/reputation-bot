/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.repbot.dao.access.guild.settings.sub.thanking;

import de.chojo.repbot.dao.access.guild.settings.sub.Thanking;
import de.chojo.repbot.dao.components.GuildHolder;
import de.chojo.repbot.service.reputation.VoteAccessType;
import de.chojo.repbot.service.reputation.VoteType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.slf4j.LoggerFactory.getLogger;

public abstract class RolesHolder implements GuildHolder {
    private static final Logger log = getLogger(RolesHolder.class);
    protected final Map<Long, VoteAccessType> roleAccess;
    protected final Thanking thanking;

    public RolesHolder(Thanking thanking, Map<Long, VoteAccessType> roleAccess) {
        this.thanking = thanking;
        this.roleAccess = roleAccess == null ? new HashMap<>() : roleAccess;
    }

    public boolean hasRole(@Nullable Member member) {
        var hasRole = hasRole(member, null);

        return hasRole == VoteAccessType.BOTH;
    }

    public VoteAccessType hasRole(@Nullable Member member, @Nullable VoteType voteType) {
        if (member == null) {
            log.trace("Member is null. Could not determine group.");
            return VoteAccessType.NONE;
        }

        if (roleAccess.isEmpty()) return VoteAccessType.BOTH;

        for (var role : member.getRoles()) {
            var id = role.getIdLong();
            if (roleAccess.containsKey(id)) {
                if (voteType == null) return VoteAccessType.BOTH; // membership check only
                var access = roleAccess.get(id);
                if (access == null) return VoteAccessType.BOTH; // default to allow (DB default BOTH)

                if (access.allows(voteType)) {
                    return VoteAccessType.BOTH;
                }
                else {
                    return access;
                }
            }
        }
        return VoteAccessType.NONE;
    }

    public Set<Role> roles() {
        return roleAccess.keySet().stream().map(id -> guild().getRoleById(id)).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    @Override
    public Guild guild() {
        return thanking.guild();
    }

    protected abstract String targetTable();

    public boolean add(Role role, VoteAccessType accessType) {
        var result = query("INSERT INTO %s(guild_id, role_id, vote_access_type) VALUES (?,?,?) " +
                        "ON CONFLICT(guild_id, role_id) DO UPDATE SET vote_access_type = EXCLUDED.vote_access_type", targetTable())
                .single(call().bind(guildId()).bind(role.getIdLong()).bind(accessType.name()))
                .update()
                .changed();
        if (result) {
            roleAccess.put(role.getIdLong(), accessType);
        }
        return result;
    }

    public boolean add(Role role) {
        return add(role, VoteAccessType.BOTH);
    }

    public boolean remove(Role role) {
        var result = query("DELETE FROM %s WHERE guild_id = ? AND role_id = ?", targetTable())
                .single(call().bind(guildId()).bind(role.getIdLong()))
                .update()
                .changed();
        if (result) {
            roleAccess.remove(role.getIdLong());
        }
        return result;
    }

    public String prettyString() {
        var effective = roleAccess.keySet();
        return effective.stream()
                      .map(id -> Optional.ofNullable(guild().getRoleById(id))
                                         .map(r -> "%s | %d".formatted(r.getName(), r.getPosition()))
                                         .orElse("Unkown (%d)".formatted(id)))
                      .collect(Collectors.joining(", "));
    }
}
