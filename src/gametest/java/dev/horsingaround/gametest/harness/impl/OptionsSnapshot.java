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

package dev.horsingaround.gametest.harness.impl;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;

/**
 * The game's options as the tests start them, and back to that before each test. Fabric's harness walks them with
 * {@code Options.processOptions}, through an interface only Fabric's access widener opens; this reads the same values
 * from the fields: each option's value, and the plain settings (numbers, flags, text, choices) kept in fields of their own.
 */
public final class OptionsSnapshot {
	private OptionsSnapshot() {
	}

	public static Map<Field, Object> take(Options options) {
		Map<Field, Object> values = new HashMap<>();

		for (Field field : Options.class.getDeclaredFields()) {
			int modifiers = field.getModifiers();

			if (Modifier.isStatic(modifiers)) {
				continue;
			}

			Class<?> type = field.getType();
			boolean option = OptionInstance.class.isAssignableFrom(type);

			if (!option && (Modifier.isFinal(modifiers) || !(type.isPrimitive() || type == String.class || type.isEnum()))) {
				continue;
			}

			try {
				field.setAccessible(true);
				Object value = field.get(options);
				values.put(field, option ? ((OptionInstance<?>) value).get() : value);
			} catch (ReflectiveOperationException | RuntimeException e) {
				throw new IllegalStateException("Can't read the option " + field.getName(), e);
			}
		}

		return values;
	}

	/**
	 * 26.x's graphics preset sets the graphics options when it changes, and any of them changing sets the preset to custom.
	 * It's set first (as Options.processOptions does), then every other option over it.
	 */
	@SuppressWarnings("unchecked")
	public static void restore(Options options, Map<Field, Object> values) {
		values.entrySet().stream().filter(entry -> entry.getKey().getName().equals("graphicsPreset")).forEach(entry -> set(options, entry.getKey(), entry.getValue()));
		values.forEach((field, value) -> {
			if (!field.getName().equals("graphicsPreset")) {
				set(options, field, value);
			}
		});
	}

	@SuppressWarnings("unchecked")
	private static void set(Options options, Field field, Object value) {
		try {
			if (OptionInstance.class.isAssignableFrom(field.getType())) {
				((OptionInstance<Object>) field.get(options)).set(value);
			} else {
				field.set(options, value);
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Can't restore the option " + field.getName(), e);
		}
	}
}
