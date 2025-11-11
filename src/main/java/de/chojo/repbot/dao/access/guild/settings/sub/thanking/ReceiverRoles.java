/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.repbot.dao.access.guild.settings.sub.thanking;

import de.chojo.repbot.dao.access.guild.settings.sub.Thanking;
import de.chojo.repbot.service.reputation.VoteAccessType;
import net.dv8tion.jda.api.entities.Guild;

import java.util.Map;

public class ReceiverRoles extends RolesHolder {
    private final Thanking thanking;

    public ReceiverRoles(Thanking thanking, Map<Long, VoteAccessType> roleAccess) {
        super(thanking, roleAccess);
        this.thanking = thanking;
    }

    @Override
    public Guild guild() {
        return thanking.guild();
    }

    @Override
    public long guildId() {
        return thanking.guildId();
    }

    @Override
    protected String targetTable() {
        return "receiver_roles";
    }
}
