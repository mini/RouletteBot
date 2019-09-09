package mini.bot.roulettebot;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

import javax.security.auth.login.LoginException;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.PrivateChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.restaction.InviteAction;

public class RouletteBot extends ListenerAdapter {

	private volatile static boolean kickEnabled = false;
	private volatile static boolean masterEnable = true;

	private static final Map<Guild, Invite> invitesForGuilds = new ConcurrentHashMap<>();
	private static final Map<Guild, Gun> gunsForGuilds = new ConcurrentHashMap<>();
	private static final Map<Long, List<Role>> rolesForDead = new ConcurrentHashMap<>();

	public static void main(String[] args) {
		if (args.length != 1) {
			System.out.println("No API token provided (as argument)");
			System.exit(1);
		}

		buildJDA();
		consoleLoop();
	}

	private static void consoleLoop() {
		long start = System.currentTimeMillis();
		try (Scanner input = new Scanner(System.in)) {
			System.out.print("RouletteBot❯ ");
			while (input.hasNextLine()) {
				String cmd = input.nextLine();
				switch (cmd.toLowerCase()) {
					case "exit":
					case "quit":
					case "q":
						System.out.println("Quitting");
						System.exit(0);
						break;
					case "force":
					case "f":
						Gun.FORCE = !Gun.FORCE;
						System.out.println("Force death: " + Gun.FORCE);
						break;
					case "list":
					case "ls":
					case "l":
						System.out.println(invitesForGuilds.keySet());
						break;
					case "kick":
					case "k":
						kickEnabled = !kickEnabled;
						System.out.println("Kicking " + (kickEnabled ? "enabled" : "disabled"));
						break;
					case "master":
					case "m":
					case "e":
						masterEnable = !masterEnable;
						System.out.println("Master " + (masterEnable ? "enabled" : "disabled"));
						break;
					case "status":
					case "s":
						System.out.println("Uptime: " + (System.currentTimeMillis() - start) / 1000);
						System.out.println("Pulls: " + Gun.getPulls() + " Fired: " + Gun.getFired());
						System.out.println("Force death: " + Gun.FORCE);
						System.out.println(invitesForGuilds.keySet());
						System.out.println("Kicking " + (kickEnabled ? "enabled" : "disabled"));
						System.out.println("Master " + (masterEnable ? "enabled" : "disabled"));
						break;
					default:
						System.out.println("Unknown command");
						break;
				}
				System.out.print("RouletteBot❯ ");
			}
		}
	}

	@Override
	public void onGuildJoin(GuildJoinEvent event) {
		Guild guild = event.getGuild();
		System.out.println("Invited to " + guild.getName());
		generateInvite(guild);
		gunsForGuilds.put(guild, new Gun(guild.getName()));
	}

	@Override
	public void onGuildMemberJoin(GuildMemberJoinEvent event) {
		Member member = event.getMember();
		List<Role> roles = rolesForDead.get(member.getIdLong());
		if (roles != null) {
			Guild guild = event.getGuild();
			rolesForDead.remove(member.getIdLong(), roles);
			for (Role role : roles) {
				guild.addRoleToMember(member, role).queue();
			}
		}
	}

	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
		Message msg = event.getMessage();
		if (!msg.getAuthor().isBot() && msg.getContentRaw().toLowerCase().contains("roulette")) {
			process(event);
		}
	}

	private void process(MessageReceivedEvent event) {
		MessageChannel channel = event.getChannel();
		Guild guild = event.getGuild();

		channel.sendMessage("Click").queue();

		Gun gun = getGun(guild);
		if (!gun.fire()) {
			return;
		}

		channel.sendMessage("Bang!").complete();

		if (kickEnabled) {
			kickUser(event.getMember(), guild);
		}
	}

	private void kickUser(Member victim, Guild guild) {
		if (victim.isOwner()) {
			System.out.println(victim.getEffectiveName() + " was owner");
			return;
		}

		rolesForDead.put(victim.getUser().getIdLong(), victim.getRoles());
		PrivateChannel pmChannel = victim.getUser().openPrivateChannel().complete();
		String inviteLink = getInviteUrl(guild);
		System.out.println(
		        "Sending invite " + inviteLink + " to " + victim.getEffectiveName() + " Roles: " + victim.getRoles());
		pmChannel.sendMessage(inviteLink).queue();

		victim.kick("shot themselves").complete();
	}

	private String getInviteUrl(Guild guild) {
		Invite invite = invitesForGuilds.get(guild);
		if (invite == null) {
			invite = generateInvite(guild);
		}
		return invite.getUrl();
	}

	private Gun getGun(Guild guild) {
		if (!gunsForGuilds.containsKey(guild)) {
			gunsForGuilds.put(guild, new Gun(guild.getName()));
		}
		return gunsForGuilds.get(guild);
	}

	private Invite generateInvite(Guild guild) {
		List<Invite> existing = guild.retrieveInvites().complete();
		for (Invite i : existing) {
			if (i.getInviter().getName().toLowerCase().contains("roulettebot")) {
				i.delete().queue();
			}
		}

		System.out.println("Making invite");
		InviteAction action = guild.getDefaultChannel().createInvite();
		Invite invite = action.setMaxAge(0).setMaxUses(0).setTemporary(false).complete();
		invitesForGuilds.put(guild, invite);
		return invite;
	}

	private static JDA buildJDA() {
		JDABuilder builder = null;
		try {
			builder = new JDABuilder(readToken());
			RouletteBot bot = new RouletteBot();
			builder.addEventListeners(bot);
			JDA jda = builder.build();
			jda.awaitReady();
			return jda;
		} catch (LoginException | InterruptedException e) {
			e.printStackTrace();
			System.exit(1);
		}
		return null;
	}

	private static String readToken() {
		try {
			return Files.readAllLines(Paths.get(RouletteBot.class.getResource("/token.txt").toURI())).get(1);
		} catch (IOException | URISyntaxException e) {
			System.err.println("Error reading token file");
			e.printStackTrace();
		}
		return "Token not found";
	}

}
