package town.lampas.overrides.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import net.neoforged.fml.loading.LoadingModList;
import java.util.List;
import java.util.Set;

public class LampasMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains("FormattedStringEditorMixin")) {
            return LoadingModList.get().getModFileById("scholar") != null;
        }
        if (mixinClassName.contains("NeonBlockColorProcedureMixin")) {
            return LoadingModList.get().getModFileById("neoncraft") != null;
        }
        if (mixinClassName.contains("Bounty") || mixinClassName.contains("ContractItem")) {
            return LoadingModList.get().getModFileById("wildernature") != null;
        }
        if (mixinClassName.contains("MerchantOfferMixin")) {
            return LoadingModList.get().getModFileById("lightmanscurrency") != null;
        }
        if (mixinClassName.contains("JadeObjectNameProviderMixin")) {
            return LoadingModList.get().getModFileById("jade") != null;
        }
        if (mixinClassName.contains("CookingPotBottleDupeMixin")) {
            return LoadingModList.get().getModFileById("farmersdelight") != null;
        }
        if (mixinClassName.contains("SellingBinCurrencyMixin")) {
            return LoadingModList.get().getModFileById("selling_bin") != null
                    && LoadingModList.get().getModFileById("lightmanscurrency") != null;
        }
        if (mixinClassName.contains("StarcatcherFishingBobMixin")) {
            return LoadingModList.get().getModFileById("starcatcher") != null;
        }
        if (mixinClassName.contains("TomsConnectorCacheGuardMixin")) {
            return LoadingModList.get().getModFileById("toms_storage") != null;
        }
        if (mixinClassName.contains("ToolboxTickPlayersGuardMixin")) {
            return LoadingModList.get().getModFileById("create") != null;
        }
        if (mixinClassName.contains("ItemElokosaPawMixin")) {
            return LoadingModList.get().getModFileById("mowziesmobs") != null;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() { return null; }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
