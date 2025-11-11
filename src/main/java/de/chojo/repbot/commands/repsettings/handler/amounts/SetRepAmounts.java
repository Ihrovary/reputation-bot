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
        var upvoteOption = event.getOption("upvote");
        var downvoteOption = event.getOption("downvote");
        
        if (upvoteOption == null || downvoteOption == null) {
            event.reply("No amounts specified").setEphemeral(true).queue();
            return;
        }

        int upvoteAmount = 1;
        int downvoteAmount = -1;

        if (upvoteOption != null) {
            upvoteAmount = upvoteOption.getAsInt();
            settings.reputation().upvoteAmount(upvoteAmount);
        }

        if (downvoteOption != null) {
            downvoteAmount = -downvoteOption.getAsInt();
            settings.reputation().downvoteAmount(downvoteAmount);
        }

        var message = context.localize("command.repsettings.amounts.set.message",
                Replacement.create("UPVOTE", upvoteAmount),
                Replacement.create("DOWNVOTE", downvoteAmount));
        
        event.reply(message != null ? message : String.format("Amounts set to: Upvote=%d, Downvote=%d", upvoteAmount, downvoteAmount))
                .setEphemeral(true)
                .queue();
    }
}