package town.lampas.overrides.mixin;

import com.wdiscute.sellingbin.bin.Currency;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reworks wd's Selling Bin economy for Starcatcher fish to match the Lampas server:
 *
 * <ol>
 *   <li><b>Currency swap.</b> Starcatcher's built-in "Emeralds" pack registers vanilla
 *       {@code minecraft:emerald}/{@code emerald_block} as the selling-bin currency (and it is
 *       auto-enabled at {@code Pack.Position.BOTTOM}, so a datapack override would lose the
 *       priority fight). We instead rewrite {@link Currency#getCurrencies()} so the Elandian Aur
 *       coin ({@code lightmanscurrency:coin_emerald}) is the sole currency, mirroring how
 *       {@code MerchantOfferMixin} swaps emeralds for the same coin in villager trades.</li>
 *   <li><b>Ceiling the fish value.</b> Selling-bin values are integers and the coin price shown/paid
 *       is {@code value / coinValue}, so a fish worth 65 reads as 0.65 coins. We round each item's
 *       value <em>up</em> to a whole coin (e.g. 0.65&nbsp;&rarr;&nbsp;1, 1.45&nbsp;&rarr;&nbsp;2) so
 *       no catch ever pays a fractional Aur coin.</li>
 * </ol>
 *
 * <p>Targeted with {@code remap = false} (non-Minecraft class) and gated on both {@code selling_bin}
 * and {@code lightmanscurrency} being present in {@link LampasMixinPlugin}.
 */
@Mixin(value = Currency.class, remap = false)
public class SellingBinCurrencyMixin {

    @Unique
    private static final ResourceLocation lampasOverrides$COIN_EMERALD_ID =
            ResourceLocation.fromNamespaceAndPath("lightmanscurrency", "coin_emerald");

    @Unique
    private static Item lampasOverrides$coinEmerald = null;

    // Currency.getCurrencies() reads a NeoForge registry data map, which is data-driven and can
    // change on /reload. A short TTL keeps the hot-path win (repeated sales within the same window
    // reuse the cached list/unit) without going stale forever after a datapack reload.
    @Unique
    private static final long lampasOverrides$CACHE_TTL_MS = 5000L;

    @Unique
    private static volatile List<Currency> lampasOverrides$cachedCurrencies = null;

    @Unique
    private static volatile long lampasOverrides$cacheTimestamp = 0L;

    @Unique
    private static volatile int lampasOverrides$minUnit = -1;

    @Unique
    private static Item lampasOverrides$resolveCoin() {
        if (lampasOverrides$coinEmerald == null) {
            Item coin = BuiltInRegistries.ITEM.get(lampasOverrides$COIN_EMERALD_ID);
            if (coin != null && coin != Items.AIR) {
                lampasOverrides$coinEmerald = coin;
            }
        }
        return lampasOverrides$coinEmerald;
    }

    /**
     * Replace the emerald-based selling-bin currencies with the Elandian Aur coin (keeping any
     * non-emerald currencies other mods may contribute), so the bin pays out in Aur coins.
     */
    @Inject(method = "getCurrencies", at = @At("RETURN"), cancellable = true)
    private static void lampasOverrides$useAurCoin(CallbackInfoReturnable<List<Currency>> cir) {
        long now = System.currentTimeMillis();
        if (lampasOverrides$cachedCurrencies != null && now - lampasOverrides$cacheTimestamp < lampasOverrides$CACHE_TTL_MS) {
            cir.setReturnValue(lampasOverrides$cachedCurrencies);
            return;
        }

        Item coin = lampasOverrides$resolveCoin();
        if (coin == null) {
            return;
        }

        List<Currency> out = new ArrayList<>();
        boolean hasCoin = false;
        for (Currency c : cir.getReturnValue()) {
            Item item = c.item();
            if (item == Items.EMERALD || item == Items.EMERALD_BLOCK || c.value() <= 0) {
                continue;
            }
            if (item == coin) {
                hasCoin = true;
            }
            out.add(c);
        }
        if (!hasCoin) {
            // 100 selling-bin value == 1 Aur coin, matching the emerald value it replaces.
            out.add(new Currency(coin, 100));
        }
        out.sort(Comparator.comparingInt(Currency::value));
        
        List<Currency> immutable = java.util.Collections.unmodifiableList(out);
        lampasOverrides$cachedCurrencies = immutable;
        lampasOverrides$cacheTimestamp = now;
        lampasOverrides$minUnit = -1; // recompute alongside the refreshed currency list
        cir.setReturnValue(immutable);
    }

    /**
     * Round a single stack's computed value up to a whole number of the smallest currency unit, so
     * fish never sell for a fractional Aur coin.
     */
    @Inject(
            method = "calculateValueFromSingleStack(Lnet/minecraft/world/item/ItemStack;"
                    + "Lnet/minecraft/world/level/block/entity/BlockEntity;"
                    + "Lnet/minecraft/world/entity/player/Player;)I",
            at = @At("RETURN"),
            cancellable = true)
    private static void lampasOverrides$ceilToWholeCoin(CallbackInfoReturnable<Integer> cir) {
        int value = cir.getReturnValueI();
        if (value <= 0) {
            return;
        }

        int unit = lampasOverrides$minUnit;
        if (unit == -1) {
            unit = Integer.MAX_VALUE;
            for (Currency c : Currency.getCurrencies()) {
                int v = c.value();
                if (v > 0 && v < unit) {
                    unit = v;
                }
            }
            if (unit != Integer.MAX_VALUE) {
                lampasOverrides$minUnit = unit;
            }
        }

        if (unit == Integer.MAX_VALUE || unit <= 1) {
            return;
        }

        int rounded = ((value + unit - 1) / unit) * unit;
        if (rounded != value) {
            cir.setReturnValue(rounded);
        }
    }
}
