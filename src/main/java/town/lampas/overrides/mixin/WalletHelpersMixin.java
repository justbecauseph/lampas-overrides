package town.lampas.overrides.mixin;

import io.github.lightman314.lightmanscurrency.common.attachments.wallet.WalletHelpers;
import io.github.lightman314.lightmanscurrency.common.attachments.WalletHandler;
import io.github.lightman314.lightmanscurrency.common.core.ModAttachmentTypes;
import io.github.lightman314.lightmanscurrency.common.items.WalletItem;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyView;
import io.github.lightman314.lightmanscurrency.api.capability.money.IMoneyHandler;
import io.github.lightman314.lightmanscurrency.api.capability.money.CapabilityMoneyHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import javax.annotation.Nonnull;

@Mixin(value = WalletHelpers.class, remap = false)
public class WalletHelpersMixin {

    /**
     * @author markj
     * @reason Support ATM cards in player inventory for wallet money queries (e.g. trading).
     */
    @Overwrite
    @Nonnull
    public static MoneyView getWalletMoney(@Nonnull final LivingEntity entity) {
        WalletHandler walletHandler = entity.getData(ModAttachmentTypes.WALLET_HANDLER);
        ItemStack wallet = walletHandler.getWallet();
        MoneyView.Builder builder = MoneyView.builder();
        builder.merge(WalletItem.getDataWrapper(wallet).getStoredMoney());
        if (entity instanceof Player player) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty()) {
                    IMoneyHandler itemHandler = stack.getCapability(CapabilityMoneyHandler.MONEY_HANDLER_ITEM);
                    if (itemHandler != null) {
                        builder.merge(itemHandler.getStoredMoney());
                    }
                }
            }
        }
        return builder.build();
    }
}
