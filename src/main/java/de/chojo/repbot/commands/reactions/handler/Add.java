/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.repbot.commands.reactions.handler;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.localization.util.Replacement;
import de.chojo.jdautil.util.Completion;
import de.chojo.jdautil.util.Premium;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.repbot.config.Configuration;
import de.chojo.repbot.dao.provider.GuildRepository;
import de.chojo.repbot.service.reputation.KarmaType;
import de.chojo.repbot.util.Parser;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;

import java.util.Objects;

import static de.chojo.repbot.commands.reactions.util.EmojiCheck.checkEmoji;

public class Add implements SlashHandler {
    private final GuildRepository guildRepository;
    private final Configuration configuration;

    public Add(GuildRepository guildRepository, Configuration configuration) {
        this.guildRepository = guildRepository;
        this.configuration = configuration;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        if (Premium.checkAndReplyPremium(context, configuration.skus().features().additionalEmojis().additionalEmojis())) {
            return;
        }

        var emoteOption = event.getOption("emote");
        var typeOption = event.getOption("type");
        if (emoteOption == null || typeOption == null) {
            event.reply(context.localize("error.invalidOption")).setEphemeral(true).queue();
            return;
        }
        
        var emote = emoteOption.getAsString();
        var type = Parser.parseEnum(typeOption.getAsString(), KarmaType.class);
        var checkingMessage = Objects.requireNonNull(context.localize("command.reactions.message.checking"));
        var message = event.reply(checkingMessage)
                         .flatMap(InteractionHook::retrieveOriginal).complete();
        handleAddCheckResult(event.getGuild(), context, message, emote, type);
    }

    private void handleAddCheckResult(Guild guild, EventContext context, Message message, String emote, KarmaType type) {
        var reactions = guildRepository.guild(guild).settings().thanking().reactions();
        var result = checkEmoji(message, emote);
        switch (result.result()) {
            case EMOJI_FOUND -> {
                reactions.add(emote, type);
                message.editMessage(context.localize("command.reactions.add.message.add",
                        Replacement.create("EMOTE", result.mention()))).complete();
            }
            case EMOTE_FOUND -> {
                reactions.add(result.id(), type);
                message.editMessage(context.localize("command.reactions.add.message.add",
                        Replacement.create("EMOTE", result.mention()))).queue();
            }
            case NOT_FOUND -> message.editMessage(context.localize("command.reactions.message.notfound")).queue();
            case UNKNOWN_EMOJI ->
                    message.editMessage(context.localize("command.reactions.message.emojinotfound")).queue();
        }
    }
    
    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event, EventContext context) {
        event.replyChoices(Completion.complete(event.getFocusedOption().getValue(), KarmaType.class)).queue();

        switch (event.getFocusedOption().getName()) {
            case "type" -> {
                event.replyChoices(Completion.complete(event.getFocusedOption().getValue(), KarmaType.class)).queue();
            }
            default -> event.replyChoices().complete();
        }
    }
}
