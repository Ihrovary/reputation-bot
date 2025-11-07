/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.repbot.commands.repsettings.handler.amounts;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.localization.util.Replacement;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.repbot.dao.provider.GuildRepository;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public class SetRepAmounts implements SlashHandler {
    private final GuildRepository guildRepository;

    public SetRepAmounts(GuildRepository guildRepository) {
        this.guildRepository = guildRepository;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        var settings = guildRepository.guild(event.getGuild()).settings();
        var positiveOption = event.getOption("positive");
        var negativeOption = event.getOption("negative");
        
        if (positiveOption == null || negativeOption == null) {
            event.reply("No amounts specified").setEphemeral(true).queue();
            return;
        }

        int positiveAmount = 1;
        int negativeAmount = -1;

        if (positiveOption != null) {
            positiveAmount = positiveOption.getAsInt();
            settings.reputation().positiveAmount(positiveAmount);
        }

        if (negativeOption != null) {
            negativeAmount = -negativeOption.getAsInt();
            settings.reputation().negativeAmount(negativeAmount);
        }

        var message = context.localize("command.repsettings.amounts.set.message",
                Replacement.create("POSITIVE", positiveAmount),
                Replacement.create("NEGATIVE", negativeAmount));
        
        event.reply(message != null ? message : String.format("Amounts set to: positive=%d, negative=%d", positiveAmount, negativeAmount))
                .setEphemeral(true)
                .queue();
    }
}