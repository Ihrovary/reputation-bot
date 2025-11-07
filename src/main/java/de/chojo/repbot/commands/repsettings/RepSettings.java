/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.repbot.commands.repsettings;

import de.chojo.jdautil.interactions.slash.Slash;
import de.chojo.jdautil.interactions.slash.SubCommand;
import de.chojo.jdautil.interactions.slash.provider.SlashCommand;
import de.chojo.repbot.commands.repsettings.handler.Info;
import de.chojo.repbot.commands.repsettings.handler.name.Reset;
import de.chojo.repbot.commands.repsettings.handler.name.Set;
import de.chojo.repbot.commands.repsettings.handler.amounts.SetRepAmounts;
import de.chojo.repbot.config.Configuration;
import de.chojo.repbot.dao.provider.GuildRepository;

import static de.chojo.jdautil.interactions.slash.Argument.integer;
import static de.chojo.jdautil.interactions.slash.Argument.text;
import static de.chojo.jdautil.interactions.slash.Group.group;
import static de.chojo.jdautil.interactions.slash.SubCommand.sub;

public class RepSettings extends SlashCommand {

    public RepSettings(GuildRepository guildRepository, Configuration configuration) {
        super(Slash.of("repsettings", "command.repsettings.description")
                .guildOnly()
                .adminCommand()
                .subCommand(SubCommand.of("info", "command.repsettings.info.description")
                        .handler(new Info(guildRepository)))
                .group(group("name", "command.repsettings.name.description")
                        .subCommand(sub("set", "command.repsettings.name.set.description")
                                .handler(new Set(guildRepository, configuration))
                                .argument(text("name", "command.repsettings.name.set.options.name.description").minLength(1).maxLength(16).asRequired()))
                        .subCommand(sub("reset", "command.repsettings.name.reset.description")
                                .handler(new Reset(guildRepository))))
                .subCommand(sub("amounts", "command.repsettings.amounts.set.description")
                        .handler(new SetRepAmounts(guildRepository))
                        .argument(integer("positive", "command.repsettings.amounts.set.positive.description").min(1).max(100).asRequired())
                        .argument(integer("negative", "command.repsettings.amounts.set.negative.description").min(1).max(100).asRequired()))
        );
    }
}
