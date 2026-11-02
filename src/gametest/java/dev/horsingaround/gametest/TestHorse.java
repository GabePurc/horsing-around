package dev.horsingaround.gametest;

/**
 * The horse the ride tests ride: a vanilla horse, or with -Dhorsingaround.horse=<entity id> another mod's (the icys
 * compat pack rides one of Icy's Better Horses' breeds through the same tests).
 */
final class TestHorse {
	static final String TYPE = System.getProperty("horsingaround.horse", "minecraft:horse");

	private TestHorse() {
	}
}
