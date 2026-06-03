package town.lampas.overrides.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "io.github.mortuusars.scholar.client.gui.widget.textbox.text.FormattedStringEditor", remap = false)
public abstract class FormattedStringEditorMixin {

    @Shadow public abstract int getCursorPos();
    @Shadow public abstract boolean isSelecting();
    @Shadow public abstract int length();

    @Inject(method = "removeCharsFromCursor", at = @At("HEAD"), cancellable = true)
    private void onRemoveCharsFromCursor(int direction, CallbackInfo ci) {
        if (!this.isSelecting()) {
            int cursor = this.getCursorPos();
            int removePos = cursor + direction;

            if (removePos < 0 || removePos > this.length()) {
                ci.cancel();
            }
        }
    }
}
