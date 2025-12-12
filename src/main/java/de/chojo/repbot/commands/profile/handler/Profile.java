/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.repbot.commands.profile.handler;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.localization.util.LocalizedEmbedBuilder;
import de.chojo.jdautil.menus.MenuAction;
import de.chojo.jdautil.menus.entries.MenuEntry;
import de.chojo.jdautil.util.Premium;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.repbot.config.Configuration;
import de.chojo.repbot.dao.provider.GuildRepository;
import de.chojo.repbot.service.RoleAssigner;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;

public class Profile implements SlashHandler {
    private final GuildRepository guildRepository;
    private final Configuration configuration;
    private final RoleAssigner roleAssigner;

    public Profile(GuildRepository guildRepository, Configuration configuration, RoleAssigner roleAssigner) {
        this.guildRepository = guildRepository;
        this.configuration = configuration;
        this.roleAssigner = roleAssigner;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        var userOption = event.getOption("user");
        var detailed = Optional.ofNullable(event.getOption("detailed")).map(OptionMapping::getAsBoolean).orElse(false);
        var member = userOption != null ? userOption.getAsMember() : event.getMember();
        if (member == null) {
            event.reply(context.localize("error.userNotFound")).setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        var reputation = guildRepository.guild(event.getGuild())
                                        .reputation()
                                        .user(member)
                                        .profile()
                                        .publicProfile(configuration, context.guildLocalizer(), detailed);
        
        // Add button to toggle detailed view using Components V2
        var ranksButton = Button.primary("profile:ranks:" + member.getId(), context.localize("word.ranks"));
        var detailedButton = Button.secondary("profile:detailed:" + member.getId(), context.localize("word.detailed"));
        
        var menuAction = MenuAction.forCallback(reputation, event)
                .addComponent(MenuEntry.of(ranksButton, ctx -> {
                    // Send ranks list as separate embed message
                    var ranksEmbed = getRoleList(context, ctx.event().getGuild());
                    ctx.event().replyEmbeds(ranksEmbed).setEphemeral(true).queue();
                }))
                .addComponent(MenuEntry.of(detailedButton, ctx -> {
                    // Fetch the member from the button ID
                    var targetMember = ctx.event().getGuild().getMemberById(member.getId());
                    if (targetMember == null) {
                        ctx.event().reply(context.localize("error.userNotFound")).setEphemeral(true).queue();
                        return;
                    }
                    
                    // Generate detailed profile
                    var detailedProfile = guildRepository.guild(ctx.event().getGuild())
                                                         .reputation()
                                                         .user(targetMember)
                                                         .profile()
                                                         .publicProfile(configuration, context.guildLocalizer(), true);
                    
                    // Update the message with detailed profile and disable button
                    var disabledButton = detailedButton.asDisabled();
                    ctx.entry().component(disabledButton);
                    ctx.refresh(detailedProfile);
                }))
                .build();
        
        context.registerMenu(menuAction);
        roleAssigner.updateReporting(member, event.getGuildChannel());
    }

    private MessageEmbed getRoleList(EventContext context, Guild guild) {
        var settings = guildRepository.guild(guild).settings();
        var ranks = settings.ranks();

        var reputationRoles = ranks.ranks()
                                   .stream()
                                   .sorted(Comparator.reverseOrder())
                                   .filter(role -> role.getRole(guild).isPresent())
                                   .map(role -> role.reputation() + " ➜ " + role.getRole(guild).get().getAsMention())
                                   .collect(Collectors.joining("\n"));

        var builder = new LocalizedEmbedBuilder(context.guildLocalizer())
                .setTitle("command.roles.info.message.roleinfo");

        builder.addField("command.roles.info.message.reputationrole", reputationRoles, true);

        return builder.build();
    }
}
