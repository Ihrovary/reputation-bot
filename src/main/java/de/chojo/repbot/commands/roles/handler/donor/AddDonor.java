/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.repbot.commands.roles.handler.donor;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.localization.util.Replacement;
import de.chojo.jdautil.util.Completion;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.repbot.dao.provider.GuildRepository;
import de.chojo.repbot.service.reputation.VoteAccessType;
import de.chojo.repbot.util.Parser;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.util.Collections;

public class AddDonor implements SlashHandler {
    private final GuildRepository guildRepository;

    public AddDonor(GuildRepository guildRepository) {
        this.guildRepository = guildRepository;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        var role = event.getOption("role").getAsRole();

        var accessOption = event.getOption("access");
        if (accessOption == null) {
            event.reply(context.localize("error.invalidOption")).setEphemeral(true).queue();
            return;
        }
        var access = Parser.parseEnum(accessOption.getAsString(), VoteAccessType.class);

        guildRepository.guild(event.getGuild()).settings().thanking().donorRoles().add(role, access);

        event.reply(context.localize("command.roles.donor.add.message.add", Replacement.createMention(role)))
            .setAllowedMentions(Collections.emptyList()).complete();
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event, EventContext context) {
        event.replyChoices(Completion.complete(event.getFocusedOption().getValue(), VoteAccessType.class)).queue();

        switch (event.getFocusedOption().getName()) {
            case "access" -> {
                event.replyChoices(Completion.complete(event.getFocusedOption().getValue(), VoteAccessType.class)).queue();
            }
            default -> event.replyChoices().complete();
        }
    }
}
