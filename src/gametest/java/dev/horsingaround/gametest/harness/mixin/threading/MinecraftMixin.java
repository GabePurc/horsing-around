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

package dev.horsingaround.gametest.harness.mixin.threading;

import com.google.common.base.Preconditions;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
//? if >=26.1
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelStorageSource;

import dev.horsingaround.gametest.harness.impl.threading.ThreadingImpl;

@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Unique
	private Runnable deferredTask = null;

	@WrapMethod(method = "run")
	private void onRun(Operation<Void> original) throws Throwable {
		if (ThreadingImpl.isClientRunning) {
			throw new IllegalStateException("Client is already running");
		}

		ThreadingImpl.isClientRunning = true;
		ThreadingImpl.PHASER.register();

		try {
			original.call();
		} finally {
			deregisterClient();

			if (ThreadingImpl.testFailureException != null) {
				throw ThreadingImpl.testFailureException;
			}
		}
	}

	@Inject(method = "emergencySave", at = @At("HEAD"))
	private void deregisterAfterCrash(CallbackInfo ci) {
		// Deregister a bit earlier than normal to allow for the integrated server to stop without waiting for the client
		ThreadingImpl.setGameCrashed();
		deregisterClient();
	}

	//? if >=26.1 {
	@ModifyExpressionValue(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/DeltaTracker$Timer;advanceGameTime(J)I"))
	//?} elif >=1.21 {
	/*@ModifyExpressionValue(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/DeltaTracker$Timer;advanceTime(JZ)I"))
	*///?} else
	/*@ModifyExpressionValue(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Timer;advanceTime(J)I"))*/
	private int captureTicksPerFrame(int capturedTicksPerFrame, @Share("ticksPerFrame") LocalIntRef ticksPerFrame) {
		// limit the number of ticks in a single frame to 1 (disable the "catch-up" mechanism)
		if (capturedTicksPerFrame > 1) {
			capturedTicksPerFrame = 1;
		}

		ticksPerFrame.set(capturedTicksPerFrame);
		return capturedTicksPerFrame;
	}

	@Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runAllTasks()V", shift = At.Shift.AFTER))
	private void postRunTasksHook(CallbackInfo ci, @Share("ticksPerFrame") LocalIntRef ticksPerFrame) {
		// allow the test code to run if there is going to be a tick this frame
		if (ticksPerFrame.get() > 0) {
			postRunTasks();
		}
	}

	@Inject(method = "doWorldLoad", at = @At("HEAD"), cancellable = true)
	//? if >=26.1 {
	private void deferStartIntegratedServer(LevelStorageSource.LevelStorageAccess storageAccess, PackRepository dataPackManager, WorldStem worldStem, java.util.Optional<GameRules> gameRules, boolean newWorld, CallbackInfo ci) {
	//?} elif >=1.20.3 {
	/*private void deferStartIntegratedServer(LevelStorageSource.LevelStorageAccess storageAccess, PackRepository dataPackManager, WorldStem worldStem, boolean newWorld, CallbackInfo ci) {
	*///?} else
	/*private void deferStartIntegratedServer(String levelId, LevelStorageSource.LevelStorageAccess storageAccess, PackRepository dataPackManager, WorldStem worldStem, boolean newWorld, CallbackInfo ci) {*/
		if (ThreadingImpl.taskToRun != null) {
			// don't start the integrated server (which busywaits) inside a task
			//? if >=26.1 {
			deferredTask = () -> Minecraft.getInstance().doWorldLoad(storageAccess, dataPackManager, worldStem, gameRules, newWorld);
			//?} elif >=1.20.3 {
			/*deferredTask = () -> Minecraft.getInstance().doWorldLoad(storageAccess, dataPackManager, worldStem, newWorld);
			*///?} else
			/*deferredTask = () -> Minecraft.getInstance().doWorldLoad(levelId, storageAccess, dataPackManager, worldStem, newWorld);*/
			ci.cancel();
		}
	}

	//? if >=1.21.9 {
	@Inject(method = "doWorldLoad", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;managedBlock(Ljava/util/function/BooleanSupplier;)V"))
	//?} else
	/*@Inject(method = "doWorldLoad", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;sleep(J)V", remap = false))*/
	private void onStartIntegratedServerBusyWait(CallbackInfo ci) {
		// give the server a chance to tick too
		postRunTasks();
	}

	//? if >=1.20.5 {
	@Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At("HEAD"), cancellable = true)
	private void deferDisconnect(Screen disconnectionScreen, boolean transferring, CallbackInfo ci) {
	//?} elif >=1.20.2 {
	/*@Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("HEAD"), cancellable = true)
	private void deferDisconnect(Screen disconnectionScreen, CallbackInfo ci) {
	*///?} else {
	/*@Inject(method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("HEAD"), cancellable = true)
	private void deferDisconnect(Screen disconnectionScreen, CallbackInfo ci) {
	*///?}
		if (Minecraft.getInstance().getSingleplayerServer() != null && ThreadingImpl.taskToRun != null) {
			// don't disconnect (which busywaits) inside a task
			//? if >=1.20.5 {
			deferredTask = () -> Minecraft.getInstance().disconnect(disconnectionScreen, transferring);
			//?} elif >=1.20.2 {
			/*deferredTask = () -> Minecraft.getInstance().disconnect(disconnectionScreen);
			*///?} else
			/*deferredTask = () -> Minecraft.getInstance().clearLevel(disconnectionScreen);*/
			ci.cancel();
		}
	}

	//? if >=26.1 {
	@Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;renderFrame(Z)V", shift = At.Shift.AFTER))
	//?} elif >=1.21.11 {
	/*@Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runTick(Z)V", shift = At.Shift.AFTER))
	*///?} elif >=1.20.5 {
	/*@Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runTick(Z)V", shift = At.Shift.AFTER))
	*///?} elif >=1.20.2 {
	/*@Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runTick(Z)V", shift = At.Shift.AFTER))
	*///?} else
	/*@Inject(method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runTick(Z)V", shift = At.Shift.AFTER))*/
	private void onDisconnectBusyWait(CallbackInfo ci) {
		// give the server a chance to tick too
		postRunTasks();
	}

	@Unique
	private void postRunTasks() {
		ThreadingImpl.clientCanAcceptTasks = true;
		ThreadingImpl.enterPhase(ThreadingImpl.PHASE_TEST);

		if (ThreadingImpl.testThread != null) {
			while (true) {
				try {
					ThreadingImpl.CLIENT_SEMAPHORE.acquire();
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}

				if (ThreadingImpl.taskToRun != null) {
					ThreadingImpl.taskToRun.run();
				} else {
					break;
				}
			}
		}

		ThreadingImpl.enterPhase(ThreadingImpl.PHASE_TICK);

		Runnable deferredTask = this.deferredTask;
		this.deferredTask = null;

		if (deferredTask != null) {
			deferredTask.run();
		}
	}

	@Inject(method = "getInstance", at = @At("HEAD"))
	private static void checkThreadOnGetInstance(CallbackInfoReturnable<Minecraft> cir) {
		Preconditions.checkState(
				Thread.currentThread() != ThreadingImpl.testThread,
				"Minecraft.getInstance() cannot be called from the gametest thread. Try using ClientGameTestContext.runOnClient or ClientGameTestContext.computeOnClient"
		);
	}

	@Unique
	private static void deregisterClient() {
		if (ThreadingImpl.isClientRunning) {
			ThreadingImpl.clientCanAcceptTasks = false;
			ThreadingImpl.PHASER.arriveAndDeregister();
			ThreadingImpl.isClientRunning = false;
		}
	}
}
