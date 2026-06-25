package town.lampas.overrides.chat;

/**
 * A single queued line bound for the Discord webhook.
 *
 * @param username  webhook display name for this line (the player's display name, or the server name)
 * @param avatarUrl webhook avatar URL, or {@code null} to use the webhook's default avatar
 * @param content   the message text
 */
public record RelayMessage(String username, String avatarUrl, String content) {}
