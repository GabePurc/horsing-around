/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Changed for Horsing Around (2026): moved into this project's test source set and made loader-neutral, so every
 * loader and Minecraft version runs the same client tests. Originally from Fabric API's fabric-client-gametest-api-v1.
 */

package dev.horsingaround.gametest.harness.mixin.input;

import java.util.Optional;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.platform.DisplayData;
import com.mojang.blaze3d.platform.Monitor;
//? if >=26.2 {
import com.mojang.blaze3d.platform.MonitorManager;
//?} else
/*import com.mojang.blaze3d.platform.ScreenManager;*/
import com.mojang.blaze3d.platform.VideoMode;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.WindowEventHandler;
import com.llamalad7.mixinextras.sugar.Local;
//? if >=26.3 {
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDL_Event;
//?}
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.horsingaround.gametest.harness.impl.util.WindowHooks;

@Mixin(Window.class)
public abstract class WindowMixin implements WindowHooks {
	@Shadow
	private int x;
	@Shadow
	private int y;
	@Shadow
	private int windowedX;
	@Shadow
	private int windowedY;
	@Shadow
	private int width;
	@Shadow
	private int height;
	@Shadow
	private int windowedWidth;
	@Shadow
	private int windowedHeight;
	@Shadow
	private int framebufferWidth;
	@Shadow
	private int framebufferHeight;
	@Shadow
	private boolean fullscreen;
	@Shadow
	@Final
	private WindowEventHandler eventHandler;
	@Shadow
	@Final
	//? if >=26.2 {
	private MonitorManager monitorManager;
	//?} else
	/*private ScreenManager screenManager;*/
	@Shadow
	private Optional<VideoMode> preferredFullscreenVideoMode;

	@Shadow
	protected abstract void setMode();

	//? if >=26.1 {
	@Shadow
	private boolean focused;
	//?}
	@Unique
	private int defaultWidth;
	@Unique
	private int defaultHeight;
	@Unique
	private int realWidth;
	@Unique
	private int realHeight;
	@Unique
	private int realFramebufferWidth;
	@Unique
	private int realFramebufferHeight;

	//? if >=26.3 {
	@Inject(method = "<init>(Lcom/mojang/blaze3d/platform/WindowEventHandler;Lcom/mojang/blaze3d/platform/DisplayData;Ljava/lang/String;ZLjava/lang/String;Lcom/mojang/blaze3d/platform/MonitorManager;Lcom/mojang/renderpearl/api/device/GpuBackend;I)V", at = @At("RETURN"))
	//?} else
	/*@Inject(method = "<init>", at = @At("RETURN"))*/
	private void onInit(CallbackInfo ci, @Local(argsOnly = true) DisplayData displayData) {
		//? if >=1.21.5 {
		this.defaultWidth = displayData.width();
		this.defaultHeight = displayData.height();
		//?} else {
		/*this.defaultWidth = displayData.width;
		this.defaultHeight = displayData.height;
		*///?}
		this.realWidth = this.width;
		this.realHeight = this.height;
		this.realFramebufferWidth = this.framebufferWidth;
		this.realFramebufferHeight = this.framebufferHeight;

		this.width = this.windowedWidth = this.framebufferWidth = defaultWidth;
		this.height = this.windowedHeight = this.framebufferHeight = defaultHeight;
	}

	//? if >=26.3 {
	@Inject(method = {"onFocus", "onIconified"}, at = @At("HEAD"), cancellable = true)
	private void cancelEvents(CallbackInfo ci) {
		ci.cancel();
	}

	@Inject(method = "handleEvent", at = @At("HEAD"), cancellable = true)
	private void cancelDirectEvents(SDL_Event event, CallbackInfo ci) {
		if (event.type() == SDLEvents.SDL_EVENT_WINDOW_MOUSE_ENTER
				|| event.type() == SDLEvents.SDL_EVENT_WINDOW_MOUSE_LEAVE) {
			ci.cancel();
		}
	}
	//?} elif >=1.21.2 {
	/*@Inject(method = {"onFocus", "onEnter", "onIconify"}, at = @At("HEAD"), cancellable = true)
	private void cancelEvents(CallbackInfo ci) {
		ci.cancel();
	}
	*///?} else {
	/*@Inject(method = {"onFocus", "onEnter"}, at = @At("HEAD"), cancellable = true)
	private void cancelEvents(CallbackInfo ci) {
		ci.cancel();
	}
	*///?}

	@Inject(method = "onResize", at = @At("HEAD"), cancellable = true)
	//? if >=26.3 {
	private void cancelResize(int width, int height, CallbackInfo ci) {
	//?} else
	/*private void cancelResize(long window, int width, int height, CallbackInfo ci) {*/
		realWidth = width;
		realHeight = height;
		ci.cancel();
	}

	@Inject(method = "onFramebufferResize", at = @At("HEAD"), cancellable = true)
	//? if >=26.3 {
	private void cancelFramebufferResize(int width, int height, CallbackInfo ci) {
	//?} else
	/*private void cancelFramebufferResize(long window, int width, int height, CallbackInfo ci) {*/
		realFramebufferWidth = width;
		realFramebufferHeight = height;
		ci.cancel();
	}

	@WrapMethod(method = "setMode")
	private void wrapSetMode(Operation<Void> original) {
		int prevWidth = this.width;
		int prevHeight = this.height;
		int prevWindowedWidth = this.windowedWidth;
		int prevWindowedHeight = this.windowedHeight;
		int prevFramebufferWidth = this.framebufferWidth;
		int prevFramebufferHeight = this.framebufferHeight;

		original.call();

		this.realWidth = this.width;
		this.realHeight = this.height;
		this.realFramebufferWidth = this.framebufferWidth;
		this.realFramebufferHeight = this.framebufferHeight;

		this.width = prevWidth;
		this.height = prevHeight;
		this.windowedWidth = prevWindowedWidth;
		this.windowedHeight = prevWindowedHeight;
		this.framebufferWidth = prevFramebufferWidth;
		this.framebufferHeight = prevFramebufferHeight;
	}

	@Inject(method = "setWindowed", at = @At("HEAD"), cancellable = true)
	private void setWindowedSize(int width, int height, CallbackInfo ci) {
		this.fullscreen = false;
		fabric_resize(width, height);
		ci.cancel();
	}

	@Override
	public int fabric_getRealWidth() {
		return realWidth;
	}

	@Override
	public int fabric_getRealHeight() {
		return realHeight;
	}

	@Override
	public int fabric_getRealFramebufferWidth() {
		return realFramebufferWidth;
	}

	@Override
	public int fabric_getRealFramebufferHeight() {
		return realFramebufferHeight;
	}

	@Override
	public void fabric_resetSize() {
		fabric_resize(defaultWidth, defaultHeight);
	}

	@Override
	public void fabric_resize(int width, int height) {
		if (width == this.width && width == this.windowedWidth && width == this.framebufferWidth && height == this.height && height == this.windowedHeight && height == this.framebufferHeight) {
			return;
		}

		// Move the top left corner of the window so that the window expands/contracts from its center, while also
		// trying to keep the window within the monitor's bounds
		//? if >=26.2 {
		Monitor monitor = this.monitorManager.findBestMonitor((Window) (Object) this);
		//?} else
		/*Monitor monitor = this.screenManager.findBestMonitor((Window) (Object) this);*/

		if (monitor != null) {
			//? if >=26.3 {
			VideoMode videoMode = monitor.getPreferredVideoMode(this.preferredFullscreenVideoMode);
			//?} else
			/*VideoMode videoMode = monitor.getPreferredVidMode(this.preferredFullscreenVideoMode);*/
			//? if >=26.2 {
			int monitorX = monitor.x();
			int monitorY = monitor.y();
			//?} else {
			/*int monitorX = monitor.getX();
			int monitorY = monitor.getY();
			*///?}

			this.x += (this.windowedWidth - width) / 2;
			this.y += (this.windowedHeight - height) / 2;

			if (this.x + width > monitorX + videoMode.getWidth()) {
				this.x = monitorX + videoMode.getWidth() - width;
			}

			if (this.x < monitorX) {
				this.x = monitorX;
			}

			if (this.y + height > monitorY + videoMode.getHeight()) {
				this.y = monitorY + videoMode.getHeight() - height;
			}

			if (this.y < monitorY) {
				this.y = monitorY;
			}

			this.windowedX = this.x;
			this.windowedY = this.y;
		}

		this.width = this.windowedWidth = this.framebufferWidth = width;
		this.height = this.windowedHeight = this.framebufferHeight = height;

		setMode();
		this.eventHandler.resizeGui();
	}

	@Override
	public void fabric_focus() {
		//? if >=26.1 {
		this.focused = true;
		//?} else
		/*net.minecraft.client.Minecraft.getInstance().setWindowActive(true);*/
	}
}
