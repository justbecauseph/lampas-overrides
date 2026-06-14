package town.lampas.overrides;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client-only listener that prepends a clickable YouTube/Twitch icon to chat messages from
 * players who have self-linked a channel. Uses NeoForge's {@link ClientChatReceivedEvent.Player}
 * (a layer below PronounMC's {@code ChatListener} mixin) so the two decorations coexist with
 * no mixin conflict — the icon sits just before the message text.
 */
public class ChatSocialDecorator {
    private static final ResourceLocation ICON_FONT =
            ResourceLocation.fromNamespaceAndPath(LampasOverridesMod.MODID, "social_icons");

    // Private-use codepoints mapped to the bitmap glyphs in assets/.../font/social_icons.json
    private static final String YOUTUBE_GLYPH = "";
    private static final String TWITCH_GLYPH = "";

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent.Player event) {
        if (!ModConfig.SHOW_SOCIAL_ICONS.get()) return;

        UUID sender = event.getSender();
        if (sender == null || event.isSystem()) return;

        // Warm the cache for everyone currently online so a player's first message decorates too.
        prefetchOnlinePlayers();

        SocialCache.Socials socials = SocialCache.get(sender);
        if (socials == null) return;

        MutableComponent prefix = Component.empty();
        boolean any = false;
        if (socials.hasYoutube()) {
            prefix.append(buildIcon(YOUTUBE_GLYPH, "YouTube", socials.youtubeHandle(), socials.youtubeUrl()));
            any = true;
        }
        if (socials.hasTwitch()) {
            if (any) prefix.append(Component.literal(" "));
            prefix.append(buildIcon(TWITCH_GLYPH, "Twitch", socials.twitchHandle(), socials.twitchUrl()));
            any = true;
        }
        if (!any) return;

        prefix.append(Component.literal(" "));
        event.setMessage(Component.empty().append(prefix).append(event.getMessage()));
    }

    private static MutableComponent buildIcon(String glyph, String platform, String handle, String url) {
        String hoverText = (handle != null && !handle.isEmpty()) ? platform + ": " + handle : platform;
        Style style = Style.EMPTY
                .withFont(ICON_FONT)
                // White so the brand colours baked into the PNG glyphs render undistorted.
                .withColor(TextColor.fromRgb(0xFFFFFF))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hoverText)))
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        return Component.literal(glyph).setStyle(style);
    }

    private static void prefetchOnlinePlayers() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        List<UUID> ids = new ArrayList<>();
        mc.getConnection().getOnlinePlayers().forEach(info -> ids.add(info.getProfile().getId()));
        if (!ids.isEmpty()) {
            SocialCache.prefetch(ids);
        }
    }
}
