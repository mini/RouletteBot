package mini.bot.roulettebot;

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
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.InviteAction;

public class RouletteBot extends ListenerAdapter {

	private volatile static boolean kickEnabled = true;
	private volatile static boolean masterEnable = true;

	private static final Map<Guild, Gun> gunsForGuilds = new ConcurrentHashMap<>();
	private static final Map<Long, List<Role>> rolesForDead = new ConcurrentHashMap<>();

	public static void main(String[] args) {
		if (args.length != 1) {
			System.out.println("No API token provided (as argument)");
			System.exit(1);
		}

		buildJDA(args[0]);
		consoleLoop();
	}

	private static void consoleLoop() {
		long start = System.currentTimeMillis();
		try (Scanner input = new Scanner(System.in)) {
			System.out.print("RouletteBot> ");
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
						System.out.println(gunsForGuilds.keySet());
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
						System.out.println("Active guilds: " + gunsForGuilds.keySet());
						System.out.println("Kicking " + (kickEnabled ? "enabled" : "disabled"));
						System.out.println("Master " + (masterEnable ? "enabled" : "disabled"));
						break;
					default:
						System.out.println("Unknown command");
						System.out.println("(q)uit (f)orceDeath (l)istServers (k)ickToggle (e)nableToggle (s)tatus");
						break;
				}
				System.out.print("RouletteBot> ");
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
			System.out.println("Added " + roles.size() + " roles");
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

		Member victim = event.getMember();
		if (victim.isOwner()) {
			System.out.println(victim.getEffectiveName() + " was server owner");
			return;
		}

		Gun gun = getGun(guild);
		if (!gun.fire()) {
			return;
		}

		if (kickEnabled) {
			channel.sendMessage("Bang!").complete();
			kickUser(victim, guild);
		}
	}

	private void kickUser(Member victim, Guild guild) {
		rolesForDead.put(victim.getUser().getIdLong(), victim.getRoles());
		PrivateChannel pmChannel = victim.getUser().openPrivateChannel().complete();
		String inviteLink = generateInvite(guild).getUrl();
		System.out.println("Sending invite " + inviteLink + " to " + victim.getEffectiveName() + " Roles: " + victim.getRoles());
		pmChannel.sendMessage(inviteLink).complete();
		victim.kick("shot themselves").complete();
	}


	private Gun getGun(Guild guild) {
		if (!gunsForGuilds.containsKey(guild)) {
			gunsForGuilds.put(guild, new Gun(guild.getName()));
		}
		return gunsForGuilds.get(guild);
	}

	private Invite generateInvite(Guild guild) {
		System.out.println("Making invite");
		InviteAction action = guild.getDefaultChannel().createInvite();
		Invite invite = action.setMaxAge(0).setMaxUses(1).setTemporary(false).complete();
		return invite;
	}

	private static JDA buildJDA(String token) {
		JDABuilder builder = null;
		try {
			builder = JDABuilder.createDefault(token);
			builder.enableIntents(List.of(GatewayIntent.GUILD_MEMBERS));
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
}
