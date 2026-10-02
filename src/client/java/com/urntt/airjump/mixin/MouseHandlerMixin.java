package com.urntt.airjump.mixin;

import com.urntt.airjump.AirJumpClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	/**
	 * Passes every mouse button event to the air jump handler before vanilla processes it. Vanilla's handling of the
	 * event is unchanged.
	 */
	@Inject(method = "onButton", at = @At("HEAD"))
	private void airjump$onButton(final long handle, final MouseButtonInfo rawButtonInfo, final int action, final CallbackInfo ci) {
		AirJumpClient.handleMouseButton(this.minecraft, action, rawButtonInfo);
	}
}
