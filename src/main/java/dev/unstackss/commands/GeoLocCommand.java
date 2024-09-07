package dev.unstackss.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.level.ServerPlayer;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public class GeoLocCommand {

    private static final String IPINFO_API_KEY = "19218d34ec708d";
    private static final OkHttpClient client = new OkHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("geoloc")
                .requires(source -> source.hasPermission(3))
                .then(
                    Commands.argument("targets", GameProfileArgument.gameProfile())
                        .suggests(
                            (context, builder) -> {
                                PlayerList playerList = context.getSource().getServer().getPlayerList();
                                return SharedSuggestionProvider.suggest(
                                    playerList.getPlayers()
                                        .stream()
                                        .map(player -> player.getGameProfile().getName()),
                                    builder
                                );
                            }
                        )
                        .executes(context -> geoLocPlayers(context.getSource(), GameProfileArgument.getGameProfiles(context, "targets")))
                )
        );
    }

    private static int geoLocPlayers(CommandSourceStack source, Collection<GameProfile> targets) {
        PlayerList playerList = source.getServer().getPlayerList();
        for (GameProfile profile : targets) {
            ServerPlayer serverPlayer = playerList.getPlayer(profile.getId());
            if (serverPlayer == null) {
                source.sendFailure(Component.literal("Giocatore non trovato: " + profile.getName()));
                continue;
            }

            Player player = Bukkit.getPlayer(profile.getId());
            assert player != null;
            String playerIp = getPlayerIpAddress(player);

            if (playerIp != null) {
                String geoLocInfo = getGeoLocInfo(playerIp);
                source.sendSuccess(() -> Component.literal("Giocatore: " + profile.getName() + "\n" + geoLocInfo).withColor(Color.AQUA.asARGB()), false);
            } else {
                source.sendFailure(Component.literal("Nessun indirizzo IP trovato per il giocatore: " + profile.getName()));
            }
        }
        return 1;
    }

    private static String getPlayerIpAddress(Player player) {
        return player.getAddress() != null ? player.getAddress().getHostString() : "Indirizzo IP non disponibile";
    }

    private static String getGeoLocInfo(String ipAddress) {
        String url = "https://ipinfo.io/" + ipAddress + "?token=" + IPINFO_API_KEY;
        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Codice inaspettato " + response);
            }

            assert response.body() != null;
            String responseBody = response.body().string();
            Map<String, Object> info = objectMapper.readValue(responseBody, Map.class);

            String city = (String) info.get("city");
            boolean isProxy = (Boolean) info.getOrDefault("proxy", false);

            return String.format("Indirizzo IP: %s\nCittà: %s\nProxy/VPN: %s",
                ipAddress, city, isProxy ? "Sì" : "No");
        } catch (IOException e) {
            e.printStackTrace();
            return "Impossibile recuperare le informazioni di geolocalizzazione.";
        }
    }
}
