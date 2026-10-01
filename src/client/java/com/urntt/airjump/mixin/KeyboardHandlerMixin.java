package com.urntt.airjump.mixin;

import com.urntt.airjump.AirJumpClient;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	/**
	 * Passes every keyboard event to the air jump handler before vanilla processes it. Vanilla's handling of the event
	 * is unchanged.
	 */
	@Inject(method = "keyPress", at = @At("HEAD"))
	private void airjump$onKeyPress(final long handle, final int action, final KeyEvent event, final CallbackInfo ci) {
		AirJumpClient.handleKeyPress(this.minecraft, action, event);
	}
}
